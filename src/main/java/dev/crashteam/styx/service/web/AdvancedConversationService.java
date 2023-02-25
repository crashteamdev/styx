package dev.crashteam.styx.service.web;

import dev.crashteam.styx.exception.OriginalRequestException;
import dev.crashteam.styx.exception.ProxyForbiddenException;
import dev.crashteam.styx.exception.ProxyGlobalException;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.request.RetriesRequest;
import dev.crashteam.styx.model.web.ErrorResult;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import dev.crashteam.styx.model.web.Result;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import dev.crashteam.styx.util.AdvancedProxyUtils;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AdvancedConversationService {

    private final WebClientService webClientService;
    private final CachedProxyService proxyService;
    private final RetriesRequestService retriesRequestService;

    public Mono<Result> getProxiedResult(ProxyRequestParams params) {
        String requestId = UUID.randomUUID().toString();
        return proxyService.getRandomProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return proxyService.getRandomProxy(params.getTimeout())
                                .flatMap(proxy -> getProxiedResponse(params, proxy, requestId));
                    } else {
                        return getWebClientResponse(params)
                                .delaySubscription(Duration.ofMillis(params.getTimeout()));
                    }
                })
                .onErrorResume(Objects::nonNull, e -> {
                    log.error("Unknown error", e);
                    return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                }).doOnSuccess(result -> retriesRequestService.deleteIfExistByRequestId(requestId).subscribe());

    }

    private Mono<Result> getProxiedResponse(ProxyRequestParams params, ProxyInstance proxy, String requestId) {
        log.info("Sending request via proxy - [{}:{}]. URL - {}, HttpMethod - {}. Proxy source - {}, Bad proxy points - {}",
                proxy.getHost(), proxy.getPort(),
                params.getUrl(), params.getHttpMethod(), proxy.getProxySource().getValue(), proxy.getBadProxyPoint());
        return webClientService.getProxiedWebclientWithHttpMethod(params, proxy)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful() && !httpStatus.equals(HttpStatus.FORBIDDEN), this::getMonoError)
                .onStatus(httpStatus -> httpStatus.equals(HttpStatus.FORBIDDEN), this::getForbiddenError)
                .toEntity(Object.class)
                .timeout(Duration.ofMillis(4000L), Mono.error(new ReadTimeoutException("Timeout")))
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
                    return retriesRequestService.existsByRequestId(requestId)
                            .flatMap(exist -> {
                                if (exist) {
                                    return retriesRequestService.findByRequestId(requestId);
                                } else {
                                    RetriesRequest retriesRequest = new RetriesRequest();
                                    retriesRequest.setRequestId(requestId);
                                    retriesRequest.setRetries(3);
                                    return Mono.just(retriesRequest);
                                }
                            }).flatMap(retriesRequest -> {
                                retriesRequest.setRetries(retriesRequest.getRetries() - 1);
                                if (retriesRequest.getRetries() == 0) {
                                    retriesRequestService.deleteByRequestId(requestId).subscribe();
                                    proxyService.deleteByHashKey(proxy);
                                    log.error("Proxy - [{}:{}] request failed, URL - {}, HttpMethod - {}. Error - {}", proxy.getHost(),
                                            proxy.getPort(), params.getUrl(), params.getHttpMethod(),
                                            Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(e.getMessage()));
                                    return Mono.just(ErrorResult.proxyConnectionError(params.getUrl(), e));
                                }
                                retriesRequestService.save(retriesRequest).subscribe();
                                if (params.getTimeout() == 0L) {
                                    params.setTimeout(4000L);
                                }
                                return connectionErrorResult(e, params, requestId);
                            });
                })
                .onErrorResume(throwable -> throwable instanceof ProxyGlobalException,
                        e -> Mono.just(ErrorResult.unknownError(params.getUrl(), e)));

    }

    private Mono<Result> getWebClientResponse(ProxyRequestParams params) {
        log.warn("No active proxies available, sending request as is on url - [{}]", params.getUrl());
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

    private Mono<Result> connectionErrorResult(Throwable e, ProxyRequestParams params, String requestId) {
        log.warn("Trying to send request with another random proxy. Exception - " + e.getMessage());
        return proxyService.getRandomProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return proxyService.getRandomProxy(params.getTimeout()).flatMap(proxy ->
                                getProxiedResponse(params, proxy, requestId));
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

    private Mono<? extends Throwable> getForbiddenError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .onErrorResume(Objects::nonNull, e -> {
                    log.error("Unknown error", e);
                    return Mono.error(new ProxyGlobalException(e.getMessage(), e));
                })
                .flatMap(body -> Mono.error(new ProxyForbiddenException("Proxy request forbidden error", body,
                        response.rawStatusCode())));

    }
}
