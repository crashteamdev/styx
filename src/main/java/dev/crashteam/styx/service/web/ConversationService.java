package dev.crashteam.styx.service.web;

import dev.crashteam.styx.exception.OriginalRequestException;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import dev.crashteam.styx.model.web.Result;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import io.netty.handler.proxy.ProxyConnectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final CachedProxyService proxyService;
    private final RetriesRequestService retriesRequestService;
    private final WebClientService webClientService;

    public Mono<Result> getProxiedResultWithParams(ProxyRequestParams params, Map<String, String> headers) {
        return getRandomProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return getRandomProxy(params.getTimeout()).flatMap(proxy -> getProxiedResponseWithParams(params, headers, proxy));
                    } else {
                        return getWebClientResponseWithParams(params, headers).delaySubscription(Duration.ofMillis(params.getTimeout()));
                    }
                });
    }

    public Mono<Result> getProxiedResponse(String url, Map<String, String> headers, Long timeout) {
        return getRandomProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return getRandomProxy(timeout).flatMap(proxy -> getProxiedWebClientResponse(url, proxy, headers, timeout));
                    } else {
                        return getWebClientResponse(url, headers).delaySubscription(Duration.ofMillis(timeout));
                    }
                });
    }

    private Mono<Result> getProxiedWebClientResponse(String url, ProxyInstance proxy,
                                                     Map<String, String> headers, Long timeout) {
        log.info("Sending request via proxy - [{}:{}]. URL - {}. Proxy source - {}, Bad proxy points - {}",
                proxy.getHost(), proxy.getPort(),
                url, proxy.getProxySource().getValue(), proxy.getBadProxyPoint());
        return webClientService.getProxiedWebClient(url, proxy, headers)
                .get()
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), this::getMonoError)
                .toEntity(Object.class)
                .map(response -> Result.success(response.getStatusCodeValue(), url, response.getBody()))
                .onErrorResume(throwable -> throwable instanceof OriginalRequestException, e -> {
                    log.error("Request with proxy failed with an error: ", e);
                    final OriginalRequestException requestException = (OriginalRequestException) e;
                    return Mono.just(Result.proxyError(requestException.getStatusCode(), url, requestException.getBody()));
                })
                .onErrorResume(throwable -> throwable instanceof ConnectException
                        || throwable instanceof WebClientRequestException, e -> {
                    proxyService.setBadProxyOnError(proxy, e);
                    return connectionErrorResult(e, url, headers, timeout);
                })
                .onErrorResume(throwable -> throwable instanceof ProxyConnectException,
                        e -> Mono.just(Result.proxyConnectionError(url)));
    }

    private Mono<Result> getProxiedResponseWithParams(ProxyRequestParams params, Map<String, String> headers, ProxyInstance proxy) {
        log.info("Sending request via proxy - [{}:{}]. URL - {}, HttpMethod - {}. Proxy source - {}, Bad proxy points - {}",
                proxy.getHost(), proxy.getPort(),
                params.getUrl(), params.getHttpMethod(), proxy.getProxySource().getValue(), proxy.getBadProxyPoint());
        return webClientService.getProxiedWebclientWithHttpMethod(params, proxy, headers)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), this::getMonoError)
                .toEntity(Object.class)
                .map(response -> Result.success(response.getStatusCodeValue(), params.getUrl(), response.getBody()))
                .onErrorResume(throwable -> throwable instanceof OriginalRequestException, e -> {
                    log.error("Request with proxy failed with an error: ", e);
                    final OriginalRequestException requestException = (OriginalRequestException) e;
                    return Mono.just(Result.proxyError(requestException.getStatusCode(), params.getUrl(), requestException.getBody()));
                })
                .onErrorResume(throwable -> throwable instanceof ConnectException
                        || throwable instanceof WebClientRequestException, e -> {
                    proxyService.setBadProxyOnError(proxy, e);
                    return connectionErrorResultWithParams(e, params, headers);
                })
                .onErrorResume(throwable -> throwable instanceof ProxyConnectException,
                        e -> Mono.just(Result.proxyConnectionError(params.getUrl())));

    }

    private Mono<Result> connectionErrorResultWithParams(Throwable e, ProxyRequestParams params, Map<String, String> headers) {
        log.error("Trying to send request with another random proxy. ", e);
        return getRandomProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return getRandomProxy(params.getTimeout()).flatMap(proxy ->
                                getProxiedResponseWithParams(params, headers, proxy));
                    } else {
                        return getWebClientResponseWithParams(params, headers).delaySubscription(Duration.ofMillis(params.getTimeout()));
                    }
                });
    }

    private Mono<Result> connectionErrorResult(Throwable e, String url, Map<String, String> headers, Long timeout) {
        log.error("Trying to send request with another random proxy. ", e);
        return getRandomProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return getRandomProxy(timeout).flatMap(proxy ->
                                getProxiedWebClientResponse(url, proxy, headers, timeout));
                    } else {
                        return getWebClientResponse(url, headers).delaySubscription(Duration.ofMillis(timeout));
                    }
                });
    }

    private Mono<Result> connectionErrorResultWithLimitedRetries(Throwable e, String requestId, String url,
                                                                 Map<String, String> headers, Long timeout) {
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
                                        return getRandomProxy(timeout).flatMap(p -> getProxiedWebClientResponse(url, p, headers, timeout));
                                    } else {
                                        return Mono.just(Result.noActiveProxyError(url));
                                    }
                                });
                    } else {
                        return Mono.just(Result.exhaustedRetriesProxyError(url));
                    }
                });
    }

    private Mono<Result> getWebClientResponse(String url, Map<String, String> headers) {
        log.info("No active proxies available, sending request as is on url - [{}]", url);
        return webClientService.getWebClient(url, headers)
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

    private Mono<Result> getWebClientResponseWithParams(ProxyRequestParams params, Map<String, String> headers) {
        log.info("No active proxies available, sending request as is on url - [{}]", params.getUrl());
        return webClientService.getWebclientWithHttpMethod(params, headers)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), this::getMonoError)
                .toEntity(Object.class)
                .map(response -> Result.successNoProxy(response.getStatusCodeValue(), params.getUrl(), response.getBody()))
                .onErrorResume(Objects::nonNull,
                        e -> {
                            log.error("Request without proxy failed with an error: ", e);
                            if (e instanceof OriginalRequestException requestException) {
                                return Mono.just(Result.unknownError(requestException.getStatusCode(), params.getUrl(), requestException.getBody()));
                            } else {
                                return Mono.just(Result.unknownError(500, params.getUrl(), e.getMessage()));
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
}
