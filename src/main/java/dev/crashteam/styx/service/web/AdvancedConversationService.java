package dev.crashteam.styx.service.web;

import dev.crashteam.styx.exception.OriginalRequestException;
import dev.crashteam.styx.exception.ProxyGlobalException;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.web.ErrorResult;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import dev.crashteam.styx.model.web.Result;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import dev.crashteam.styx.util.AdvancedProxyUtils;
import io.netty.handler.proxy.ProxyConnectException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdvancedConversationService {

    private final WebClientService webClientService;
    private final CachedProxyService proxyService;

    public Mono<Result> getProxiedResult(ProxyRequestParams params) {
        return proxyService.getRandomProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return proxyService.getRandomProxy(params.getTimeout())
                                .flatMap(proxy -> getProxiedResponse(params, proxy));
                    } else {
                        return getWebClientResponse(params)
                                .delaySubscription(Duration.ofMillis(params.getTimeout()));
                    }
                })
                .onErrorResume(Objects::nonNull, e -> {
                    log.error("Unknown error", e);
                    return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                });

    }

    private Mono<Result> getProxiedResponse(ProxyRequestParams params, ProxyInstance proxy) {
        log.info("Sending request via proxy - [{}:{}]. URL - {}, HttpMethod - {}. Proxy source - {}, Bad proxy points - {}",
                proxy.getHost(), proxy.getPort(),
                params.getUrl(), params.getHttpMethod(), proxy.getProxySource().getValue(), proxy.getBadProxyPoint());
        return webClientService.getProxiedWebclientWithHttpMethod(params, proxy)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), this::getMonoError)
                .toEntity(Object.class)
                .map(response -> Result.success(response.getStatusCodeValue(), params.getUrl(), response.getBody(),
                        params.getHttpMethod()))
                .onErrorResume(throwable -> throwable instanceof OriginalRequestException, e -> {
                    log.error("Request with proxy failed with an error: ", e);
                    final OriginalRequestException requestException = (OriginalRequestException) e;
                    return Mono.just(ErrorResult.originalRequestError(requestException.getStatusCode(), params.getUrl(),
                            e, requestException.getBody()));
                })
                .onErrorResume(AdvancedProxyUtils::badProxyError, e -> {
                    if (e.getCause() instanceof UnsupportedMediaTypeException) {
                        return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                    }
                    proxyService.deleteByHashKey(proxy);
                    log.error("PROXY ERROR ", Optional.ofNullable(e.getCause()).orElse(e));
                    return connectionErrorResult(e, params);
                })
                .onErrorResume(throwable -> throwable instanceof ProxyGlobalException,
                        e -> Mono.just(ErrorResult.unknownError(params.getUrl(), e)))
                .onErrorResume(throwable -> throwable instanceof ProxyConnectException,
                        e -> Mono.just(ErrorResult.proxyConnectionError(params.getUrl(), e)));

    }

    private Mono<Result> getWebClientResponse(ProxyRequestParams params) {
        log.info("No active proxies available, sending request as is on url - [{}]", params.getUrl());
        return webClientService.getWebclientWithHttpMethod(params)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), this::getMonoError)
                .toEntity(Object.class)
                .map(response -> Result.successNoProxy(response.getStatusCodeValue(), params.getUrl(), response.getBody(),
                        params.getHttpMethod()))
                .onErrorResume(throwable -> throwable instanceof ProxyGlobalException,
                        e -> Mono.just(ErrorResult.unknownError(params.getUrl(), e)))
                .onErrorResume(Objects::nonNull,
                        e -> {
                            log.error("Request without proxy failed with an error: ", e);
                            if (e instanceof OriginalRequestException requestException) {
                                return Mono.just(ErrorResult.originalRequestError(requestException.getStatusCode(),
                                        params.getUrl(), requestException, requestException.getBody()));
                            } else {
                                return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                            }
                        });
    }

    private Mono<Result> connectionErrorResult(Throwable e, ProxyRequestParams params) {
        log.error("Trying to send request with another random proxy. Exception - " + e.getMessage());
        return proxyService.getRandomProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return proxyService.getRandomProxy(params.getTimeout()).flatMap(proxy ->
                                getProxiedResponse(params, proxy));
                    } else {
                        return getWebClientResponse(params)
                                .delaySubscription(Duration.ofMillis(params.getTimeout()));
                    }
                });
    }

    private Mono<? extends Throwable> getMonoError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .onErrorResume(Objects::nonNull, e -> {
                    log.error("Unknown error", e);
                    return Mono.error(new ProxyGlobalException(e.getMessage(), e));
                })
                .flatMap(body -> Mono.error(new OriginalRequestException("Proxy request error", body,
                        response.rawStatusCode())));

    }
}
