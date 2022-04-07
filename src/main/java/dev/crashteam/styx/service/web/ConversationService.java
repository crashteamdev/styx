package dev.crashteam.styx.service.web;

import dev.crashteam.styx.exception.OriginalRequestException;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.request.RetriesRequest;
import dev.crashteam.styx.model.web.Result;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import io.netty.channel.ChannelOption;
import io.netty.handler.proxy.ProxyConnectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final CachedProxyService proxyService;
    private final RetriesRequestService retriesRequestService;

    @Value("${app.proxy.timeout}")
    private Integer timeout;

    private final int BUFFER_SIZE = 2 * 1024 * 1024;

    public Mono<Result> getProxiedResponse(String url, Map<String, String> headers, Long timeout) {
        return getRandomProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        String requestId = UUID.randomUUID().toString();
                        retriesRequestService.save(new RetriesRequest(requestId, 3)).subscribe();
                        return getRandomProxy(timeout).flatMap(proxy -> getProxiedWebClientResponse(url, proxy, headers, requestId, timeout));
                    } else {
                        return getWebClientResponse(url, headers).delaySubscription(Duration.ofMillis(timeout));
                    }
                });
    }

    private Mono<Result> getProxiedWebClientResponse(String url, ProxyInstance proxy,
                                                     Map<String, String> headers, String requestId, Long timeout) {
        log.info("Sending request via proxy - [{}:{}]. URL - {}. Proxy source - {}, Bad proxy points - {}", proxy.getHost(), proxy.getPort(),
                url, proxy.getProxySource().getValue(), proxy.getBadProxyPoint());
        return getProxiedWebClient(url, proxy, headers)
                .get()
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), this::getMonoError)
                .toEntity(Object.class)
                .map(response -> Result.success(response.getStatusCodeValue(), url, response.getBody()))
                .doOnError(throwable -> throwable instanceof ConnectException, ex -> proxyService.setBadProxyOnError(proxy, ex))
                .doOnSuccess(result -> retriesRequestService.deleteByRequestId(requestId).subscribe())
                .onErrorResume(throwable -> throwable instanceof OriginalRequestException, e -> {
                    log.error("Request with proxy failed with an error: ", e);
                    final OriginalRequestException requestException = (OriginalRequestException) e;
                    return Mono.just(Result.proxyError(requestException.getStatusCode(), url, requestException.getBody()));
                })
                .onErrorResume(throwable -> throwable instanceof ConnectException, e -> {
                    log.error("Trying to send request with another random proxy. ", e);
                    return retriesRequestService.findByRequestId(requestId)
                            .flatMap(request -> {
                                request.setRetries(request.getRetries() - 1);
                                return retriesRequestService.save(request);
                            })
                            .flatMap(request -> {
                                if (request.getRetries() > 0) {
                                    return getRandomProxy(0L)
                                            .hasElement()
                                            .flatMap(hasElement -> {
                                                if (hasElement) {
                                                    return getRandomProxy(timeout).flatMap(p -> getProxiedWebClientResponse(url, p, headers, requestId, timeout));
                                                } else {
                                                    return Mono.just(Result.noActiveProxyError(url));
                                                }
                                            });
                                } else {
                                    return Mono.just(Result.exhaustedRetriesProxyError(url));
                                }
                            });
                })
                .onErrorResume(throwable -> throwable instanceof ProxyConnectException, e -> Mono.just(Result.proxyConnectionError(url)));
    }

    private Mono<Result> getWebClientResponse(String url, Map<String, String> headers) {
        log.info("No active proxies available, sending request as is on url - [{}]", url);
        return getWebClient(url, headers)
                .get()
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), this::getMonoError)
                .toEntity(Object.class)
                .map(response -> Result.successNoProxy(response.getStatusCodeValue(), url, response.getBody()))
                .onErrorResume(Objects::nonNull,
                        e -> {
                            log.error("Request without proxy failed with an error: ", e);
                            OriginalRequestException requestException = (OriginalRequestException) e;
                            return Mono.just(Result.unknownError(requestException.getStatusCode(), url, requestException.getBody()));
                        });
    }

    private WebClient getProxiedWebClient(String url, ProxyInstance proxy, Map<String, String> headers) {
        return WebClient.builder()
                .exchangeStrategies(getMaxBufferSize())
                .defaultHeaders(getHeadersConsumer(headers))
                .baseUrl(url)
                .clientConnector(getConnector(proxy))
                .build();
    }

    private WebClient getWebClient(String url, Map<String, String> headers) {
        return WebClient.builder()
                .exchangeStrategies(getMaxBufferSize())
                .defaultHeaders(getHeadersConsumer(headers))
                .baseUrl(url)
                .build();
    }

    private ReactorClientHttpConnector getConnector(ProxyInstance proxy) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, timeout)
                .proxy(p ->
                        p.type(ProxyProvider.Proxy.HTTP)
                                .host(proxy.getHost())
                                .port(Integer.parseInt(proxy.getPort()))
                                .connectTimeoutMillis(timeout)
                                .username(proxy.getUser())
                                .password(f -> proxy.getPassword()));
        return new ReactorClientHttpConnector(httpClient);

    }

    private Consumer<HttpHeaders> getHeadersConsumer(Map<String, String> headers) {
        return httpHeaders -> headers.forEach((k, v) -> {
            if (k.startsWith("X-")) {
                httpHeaders.add(k.substring(2), v);
            }
        });
    }

    private Mono<ProxyInstance> getRandomProxy(Long timeout) {
        Random random = new Random();
        final Flux<ProxyInstance> activeProxies = proxyService.getActive();
        return activeProxies
                .delaySubscription(Duration.ofMillis(timeout))
                .count()
                .map(s -> {
                    if (s != null && s > 1) {
                        return random.nextLong(s);
                    }
                    return 0L;
                })
                .flatMap(index -> activeProxies
                        .count()
                        .filter(size -> size > 0)
                        .flatMap(p -> activeProxies.elementAt(Math.toIntExact(index))))
                .switchIfEmpty(Mono.empty());
    }

    private Mono<? extends Throwable> getMonoError(ClientResponse response) {
        return response.bodyToMono(Object.class)
                .flatMap(body -> Mono.error(new OriginalRequestException("Proxy request error", body, response.rawStatusCode())));

    }

    private ExchangeStrategies getMaxBufferSize() {
         return ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(BUFFER_SIZE))
                .build();

    }
}
