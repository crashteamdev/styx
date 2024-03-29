package dev.crashteam.styx.service.web;

import dev.crashteam.styx.exception.OriginalRequestException;
import dev.crashteam.styx.exception.ProxyForbiddenException;
import dev.crashteam.styx.exception.ProxyGlobalException;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.proxy.ProxySource;
import dev.crashteam.styx.model.request.RetriesRequest;
import dev.crashteam.styx.model.web.ErrorResult;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import dev.crashteam.styx.model.web.Result;
import dev.crashteam.styx.service.forbidden.ForbiddenProxyService;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import dev.crashteam.styx.util.AdvancedProxyUtils;
import io.netty.handler.proxy.ProxyConnectException;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
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
    private final ForbiddenProxyService forbiddenProxyService;
    private final RetriesRequestService retriesRequestService;

    @Value("${app.proxy.retries.attempts}")
    private Integer retries;

    @Value("${app.proxy.retries.exponent}")
    private Double exponent;

    public Mono<Result> getProxiedResult(ProxyRequestParams params) {
        String requestId = UUID.randomUUID().toString();
        return proxyService.getRandomProxy(0L, params.getUrl())
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return proxyService.getRandomProxy(params.getTimeout(), params.getUrl())
                                .flatMap(proxy -> getProxiedResponse(params, proxy, requestId))
                                .doOnSuccess(result -> {
                                    log.debug("----RESPONSE----\n{}", result.getBody());
                                });
                    } else {
                        return getWebClientResponse(params, requestId)
                                .delaySubscription(Duration.ofMillis(params.getTimeout()))
                                .doOnSuccess(result -> {
                                    log.debug("----RESPONSE----\n{}", result.getBody());
                                });
                    }
                })
                .onErrorResume(Objects::nonNull, e -> {
                    String cause = Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(e.getMessage());
                    log.error("Unknown error for request id - {}. Cause - {}", requestId, cause, e);
                    return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                }).doFinally(result -> retriesRequestService.deleteIfExistByRequestId(requestId).subscribe());

    }

    private Mono<Result> getProxiedResponse(ProxyRequestParams params, ProxyInstance proxy, String requestId) {
        log.info("Sending request via proxy - [{}:{}]. URL - {}, HttpMethod - {}. Proxy source - {}",
                proxy.getHost(), proxy.getPort(),
                params.getUrl(), params.getHttpMethod(), Optional.ofNullable(proxy.getProxySource())
                        .map(ProxySource::getValue).orElse("Unknown"));
        String rootUrl = AdvancedProxyUtils.getRootUrl(params.getUrl());
        return webClientService.getProxiedWebclientWithHttpMethod(params, proxy)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful() && !httpStatus.equals(HttpStatus.FORBIDDEN), this::getMonoError)
                .onStatus(httpStatus -> httpStatus.equals(HttpStatus.FORBIDDEN), this::getForbiddenError)
                .toEntity(Object.class)
                .map(response -> Result.success(response.getStatusCodeValue(), params.getUrl(), response.getBody(),
                        params.getHttpMethod()))
                .onErrorResume(throwable -> throwable instanceof OriginalRequestException, e -> {
                    final OriginalRequestException requestException = (OriginalRequestException) e;
                    return Mono.just(ErrorResult.originalRequestError(requestException.getStatusCode(), params.getUrl(),
                            e, requestException.getBody()));
                })
                .onErrorResume(throwable -> (throwable.getCause() != null && throwable.getCause() instanceof ConnectException) ||
                        (throwable instanceof ProxyConnectException ||
                        (throwable.getCause() != null && throwable.getCause() instanceof ProxyConnectException)), e -> {
                    log.error("Proxy connection exception for url - {}, trying another proxy", rootUrl);
                    forbiddenProxyService.save(rootUrl, proxy);
                    return connectionErrorResult(e, params, requestId);
                })
                .onErrorResume(AdvancedProxyUtils::badProxyError, e -> {
                    if (e.getCause() instanceof UnsupportedMediaTypeException) {
                        return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                    }
                    log.warn("Retrying request because of exception - {}. Request id - {}", e.getMessage(), requestId);
                    return retriesRequestService.existsByRequestId(requestId)
                            .flatMap(exist -> getRetriesRequest(exist, requestId))
                            .flatMap(retriesRequest -> {
                                Optional<ProxyInstance.BadUrl> badUrlOptional = getOptionalBadUrl(proxy, rootUrl);
                                retriesRequest.setRetries(retriesRequest.getRetries() - 1);
                                if (retriesRequest.getRetries() == 0) {
                                    retriesRequestService.deleteByRequestId(requestId).subscribe();
                                    if (badUrlOptional.isPresent()) {
                                        ProxyInstance.BadUrl badUrl = badUrlOptional.get();
                                        if (badUrl.getPoint() >= 3) {
                                            forbiddenProxyService.save(rootUrl, proxy);
                                            badUrl.setPoint(0);
                                        } else {
                                            badUrl.setPoint(badUrl.getPoint() + 1);
                                            log.warn("Setting bad point for proxy - [{}:{}], badUrl - {}", proxy.getHost(),
                                                    proxy.getPort(), badUrl);
                                        }
                                    } else {
                                        ProxyInstance.BadUrl badUrl = new ProxyInstance.BadUrl();
                                        badUrl.setUrl(rootUrl);
                                        badUrl.setPoint(1);
                                        proxy.getBadUrls().add(badUrl);
                                    }
                                    proxyService.saveExisting(proxy);
                                    log.error("Proxy - [{}:{}] request failed, URL - {}, HttpMethod - {}. Error - {}", proxy.getHost(),
                                            proxy.getPort(), params.getUrl(), params.getHttpMethod(),
                                            Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(e.getMessage()));
                                    return getWebClientResponse(params, requestId);
                                }
                                long exponentialTimeout = (long) (retriesRequest.getTimeout() * exponent);
                                retriesRequest.setTimeout(exponentialTimeout);
                                retriesRequestService.save(retriesRequest).subscribe();
                                params.setTimeout(exponentialTimeout);
                                return connectionErrorResult(e, params, requestId);
                            });
                })
                .onErrorResume(throwable -> throwable instanceof ProxyGlobalException,
                        e -> Mono.just(ErrorResult.unknownError(params.getUrl(), e)))
                .doOnSuccess(result -> {
                    Optional<ProxyInstance.BadUrl> badUrl = getOptionalBadUrl(proxy, rootUrl);
                    if (!(result instanceof ErrorResult) && (badUrl.isPresent() && badUrl.get().getPoint() > 0)) {
                        log.warn("Reset to zero bad points for proxy - [{}:{}]", proxy.getHost(), proxy.getPort());
                        badUrl.get().setPoint(0);
                        proxyService.saveExisting(proxy);
                    }
                });

    }

    private Mono<Result> getWebClientResponse(ProxyRequestParams params, String requestId) {
        log.warn("No active proxies available or retries request with id [{}] exhausted, " +
                "sending request as is on url - [{}]", requestId, params.getUrl());
        return webClientService.getWebclientWithHttpMethod(params)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful(), this::getMonoError)
                .toEntity(Object.class)
                .timeout(Duration.ofMillis(4000L), Mono.error(new ReadTimeoutException("Timeout")))
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
        return proxyService.getRandomProxy(0L, params.getUrl())
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return proxyService.getRandomProxy(params.getTimeout(), params.getUrl()).flatMap(proxy ->
                                getProxiedResponse(params, proxy, requestId));
                    } else {
                        return getWebClientResponse(params, requestId)
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
                .flatMap(body -> Mono.error(new ProxyForbiddenException(("Proxy request forbidden error, " +
                        "response code from proxied client - %s").formatted(response.rawStatusCode()), body,
                        response.rawStatusCode())));

    }

    private Optional<ProxyInstance.BadUrl> getOptionalBadUrl(ProxyInstance proxy, String rootUrl) {
        return proxy.getBadUrls()
                .stream()
                .filter(it -> rootUrl.equals(it.getUrl()))
                .findFirst();
    }

    private Mono<RetriesRequest> getRetriesRequest(boolean exist, String requestId) {
        if (exist) {
            return retriesRequestService.findByRequestId(requestId);
        } else {
            RetriesRequest retriesRequest = new RetriesRequest();
            retriesRequest.setRequestId(requestId);
            retriesRequest.setRetries(retries);
            retriesRequest.setTimeout(1000L);
            return Mono.just(retriesRequest);
        }
    }
}
