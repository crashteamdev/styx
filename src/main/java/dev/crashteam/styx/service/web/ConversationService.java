package dev.crashteam.styx.service.web;

import dev.crashteam.styx.exception.FailedProxyRequestException;
import dev.crashteam.styx.exception.OriginalRequestException;
import dev.crashteam.styx.model.proxy.CachedProxy;
import dev.crashteam.styx.model.web.Response;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import dev.crashteam.styx.util.ErrorUtils;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import reactor.util.retry.Retry;

import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final CachedProxyService proxyService;

    public Mono<Response> getProxiedResponse(String url, Map<String, String> headers, WebSession webSession) {
        return getRandomProxy()
                .flatMap(proxy -> getProxiedWebClientResponse(url, proxy, headers, webSession)
                        .map(response -> buildResponse(response, url))
                        .switchIfEmpty(Mono.defer(() -> getWebClientResponse(url, headers)
                                .map(response -> buildResponse(response, url)))));
    }

    private Mono<ResponseEntity<Object>> getProxiedWebClientResponse(String url, CachedProxy proxy,
                                                                     Map<String, String> headers, WebSession webSession) {
        log.info("Sending request via proxy - [{}:{}]. URL - {}. Bad proxy points - {}", proxy.getHost(), proxy.getPort(),
                url, proxy.getBadProxyPoint());
        return getProxiedWebClient(url, proxy, headers)
                .get()
                .retrieve()
                .onStatus(HttpStatus::is5xxServerError,
                        response -> Mono.error(new FailedProxyRequestException("Proxy request error", response.rawStatusCode())))
                .toEntity(Object.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(throwable -> throwable instanceof FailedProxyRequestException))
                .doOnError(throwable -> throwable instanceof ConnectException, ex -> {
                    proxy.setBadProxyPoint(proxy.getBadProxyPoint() + 1);
                    if (proxy.getBadProxyPoint() == 3) {
                        proxy.setActive(false);
                    }
                    proxyService.save(proxy);
                    log.error("Proxy - [{}:{}] marked as unstable", proxy.getHost(), proxy.getPort(), ex);
                })
                .onErrorResume(throwable -> {
                    int retryCounter = (int) webSession.getAttributes().get(webSession.getId());
                    return throwable instanceof ConnectException && retryCounter > 0;
                }, e -> {
                    int retryCounter = (int) webSession.getAttributes().get(webSession.getId()) - 1;
                    log.error("Trying to send request with another random proxy. Retries left: {}", retryCounter, e);
                    webSession.getAttributes().put(webSession.getId(), retryCounter);
                    return getRandomProxy()
                            .flatMap(p -> getProxiedWebClientResponse(url, p, headers, webSession));
                }).onErrorResume(throwable -> (int) webSession.getAttributes().get(webSession.getId()) == 0, ErrorUtils::getOriginalErrorResponse);
    }

    private Mono<ResponseEntity<Object>> getWebClientResponse(String url, Map<String, String> headers) {
        log.info("No active proxies available, sending request as is on url - [{}]", url);
        return getWebClient(url, headers)
                .get()
                .retrieve()
                .onStatus(httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        response -> Mono.error(new OriginalRequestException("Proxy request error", response.rawStatusCode())))
                .toEntity(Object.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(throwable -> throwable instanceof OriginalRequestException))
                .onErrorResume(throwable -> throwable instanceof OriginalRequestException,
                        e -> {
                            log.error("Request without proxy failed with an error: ", e);
                            return ErrorUtils.getOriginalErrorResponse((OriginalRequestException) e);
                        });
    }

    private WebClient getProxiedWebClient(String url, CachedProxy proxy, Map<String, String> headers) {
        return WebClient.builder()
                .defaultHeaders(getHeadersConsumer(headers))
                .baseUrl(url)
                .clientConnector(getConnector(proxy))
                .build();
    }

    private WebClient getWebClient(String url, Map<String, String> headers) {
        return WebClient.builder()
                .defaultHeaders(getHeadersConsumer(headers))
                .baseUrl(url)
                .build();
    }

    private ReactorClientHttpConnector getConnector(CachedProxy proxy) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000)
                .proxy(p ->
                        p.type(ProxyProvider.Proxy.HTTP)
                                .host(proxy.getHost())
                                .port(Integer.parseInt(proxy.getPort()))
                                .connectTimeoutMillis(4000)
                                .username(proxy.getUser())
                                .password(f -> proxy.getPassword()));
        return new ReactorClientHttpConnector(httpClient);

    }

    private Consumer<HttpHeaders> getHeadersConsumer(Map<String, String> headers) {
        return httpHeaders -> {
            headers.forEach((k, v) -> {
                if (k.startsWith("X-")) {
                    httpHeaders.add(k.substring(2), v);
                }
            });
        };
    }

    private Mono<CachedProxy> getRandomProxy() {
        Random random = new Random();
        final Flux<CachedProxy> activeProxies = proxyService.getActive();
        return activeProxies
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

    private Response buildResponse(ResponseEntity<Object> response, String url) {
        Response proxiedResponse = new Response();
        proxiedResponse.setBody(response.getBody());
        proxiedResponse.setUrl(url);
        proxiedResponse.setOriginalStatus(response.getStatusCodeValue());
        return proxiedResponse;
    }
}
