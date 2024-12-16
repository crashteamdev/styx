package dev.crashteam.styx.service.web;

import dev.crashteam.styx.exception.*;
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
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.UnsupportedMediaTypeException;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;

import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
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

    @Value("${app.proxy.retries.timeout}")
    private Long retriesTimeout;

    public Mono<Result> getProxiedResult(ProxyRequestParams params, Map<String, String> headers) {
        String requestId = UUID.randomUUID().toString();
        long timeout = params.getTimeout() == null ? 0L : params.getTimeout();
        Map<String, String> headersByPattern = AdvancedProxyUtils.getHeadersByPattern(headers);
        Mono<ProxyInstance> proxyInstance = getProxy(params, headersByPattern, timeout);
        return proxyInstance
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return proxyInstance
                                .flatMap(proxy -> {
                                    if (ProxySource.MOBILE_PROXY.equals(proxy.getProxySource())) {
                                        return getMobileProxyProxiedResponse(params, proxy, requestId, headersByPattern);
                                    } else {
                                        return getProxiedResponse(params, proxy, requestId, headersByPattern);
                                    }
                                })
                                .doOnSuccess(result -> {
                                    if (result != null) {
                                        log.debug("----RESPONSE----\n{}", result.getBody());
                                    }
                                });
                    } else {
                        return getNonProxiedClientResponse(params, requestId)
                                .delaySubscription(Duration.ofMillis(params.getTimeout() == null ? 0L : params.getTimeout()))
                                .doOnSuccess(result -> {
                                    if (result != null) {
                                        log.debug("----RESPONSE----\n{}", result.getBody());
                                    }
                                });
                    }
                })
                .onErrorResume(Objects::nonNull, e -> {
                    String cause = Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(e.getMessage());
                    log.error("Unknown error for request id - {}. External headers - {}. Cause - {}", requestId, headersByPattern, cause, e);
                    return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                }).doFinally(result -> retriesRequestService.deleteIfExistByRequestId(requestId).subscribe());

    }

    private Mono<Result> getMobileProxyProxiedResponse(ProxyRequestParams params, ProxyInstance proxy, String requestId,
                                                       Map<String, String> headers) {

        log.info("Sending request via proxy - [{}:{}]. URL - {}, HttpMethod - {}. RequestId - {}, Proxy source - {}, External headers -{}",
                proxy.getHost(), proxy.getPort(),
                params.getUrl(), params.getHttpMethod(), requestId, Optional.ofNullable(proxy.getProxySource())
                        .map(ProxySource::getValue).orElse("Unknown"), headers);
        return webClientService.getProxiedWebclientWithHttpMethod(params, proxy)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful() && !httpStatus.equals(HttpStatus.FORBIDDEN)
                        && !httpStatus.equals(HttpStatus.TOO_MANY_REQUESTS), this::getMonoError)
                .onStatus(httpStatus -> httpStatus.equals(HttpStatus.FORBIDDEN), this::getForbiddenError)
                .onStatus(httpStatus -> httpStatus.equals(HttpStatus.TOO_MANY_REQUESTS), this::getTooManyRequestError)
                .toEntity(Object.class)
                .map(response -> Result.success(response.getStatusCodeValue(), params.getUrl(), response.getBody(),
                        params.getHttpMethod()))
                //.timeout(Duration.ofSeconds(20), Mono.error(new ReadTimeoutException("Mobile proxy timeout")))
                .onErrorResume(throwable -> throwable instanceof NonProxiedException,
                        e -> getNonProxiedClientResponse(params, requestId))
                .onErrorResume(throwable -> throwable instanceof OriginalRequestException, e -> {
                    final OriginalRequestException requestException = (OriginalRequestException) e;
                    return Mono.just(ErrorResult.originalRequestError(requestException.getStatusCode(), params.getUrl(),
                            e, requestException.getBody()));
                })
                .onErrorResume(AdvancedProxyUtils::badProxyError, e -> {
                    if (e.getCause() instanceof UnsupportedMediaTypeException) {
                        return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                    }
                    log.warn("Retrying request because of exception - {}. Request id - {}", e.getMessage(), requestId);
                    return retriesRequestService.existsByRequestId(requestId)
                            .flatMap(exist -> getRetriesRequest(exist, requestId))
                            .flatMap(retriesRequest -> {
                                if (needToChangeIp(e)) {
                                    proxy.setBadProxyPoint(proxy.getBadProxyPoint() + 1);
                                    proxyService.saveExisting(proxy);
                                }
                                retriesRequest.setRetries(retriesRequest.getRetries() - 1);
                                if (retriesRequest.getRetries() == 0) {
                                    retriesRequestService.deleteByRequestId(requestId).subscribe();
                                    log.error("Proxy - [{}:{}] request failed, URL - {}, HttpMethod - {}. Cause - {}", proxy.getHost(),
                                            proxy.getPort(), params.getUrl(), params.getHttpMethod(),
                                            Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(e.getMessage()));
                                    return getNonProxiedClientResponse(params, requestId);
                                }
                                long exponentialTimeout = (long) (retriesRequest.getTimeout() * exponent);
                                retriesRequest.setTimeout(exponentialTimeout);
                                retriesRequestService.save(retriesRequest).subscribe();
                                params.setTimeout(exponentialTimeout);
                                return connectionMobileProxyErrorResult(e, params, requestId, headers, proxy.getSystem());
                            });
                });
    }

    private Mono<Result> getProxiedResponse(ProxyRequestParams params, ProxyInstance proxy, String requestId, Map<String, String> headers) {
        log.info("Sending request via proxy - [{}:{}]. URL - {}, HttpMethod - {}. Proxy source - {}. External headers - {}",
                proxy.getHost(), proxy.getPort(),
                params.getUrl(), params.getHttpMethod(), Optional.ofNullable(proxy.getProxySource())
                        .map(ProxySource::getValue).orElse("Unknown"), headers);
        String rootUrl = AdvancedProxyUtils.getRootUrl(params.getUrl());
        return webClientService.getProxiedWebclientWithHttpMethod(params, proxy)
                .retrieve()
                .onStatus(httpStatus -> !httpStatus.is2xxSuccessful() && !httpStatus.equals(HttpStatus.FORBIDDEN)
                        && !httpStatus.equals(HttpStatus.TOO_MANY_REQUESTS), this::getMonoError)
                .onStatus(httpStatus -> httpStatus.equals(HttpStatus.FORBIDDEN) || httpStatus.equals(HttpStatus.TOO_MANY_REQUESTS), this::getForbiddenError)
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
                    return connectionErrorResult(e, params, requestId, headers);
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
                                String contextId = headers.get("X-Context-Id");
                                String appId = headers.get("X-App-Id");
                                if (retriesRequest.getRetries() == 0) {
                                    if (!(StringUtils.hasText(contextId) && StringUtils.hasText(appId))) {
                                        log.error("Proxy - [{}:{}] request failed, URL - {}, HttpMethod - {}. Cause - {}", proxy.getHost(),
                                                proxy.getPort(), params.getUrl(), params.getHttpMethod(),
                                                Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(e.getMessage()));
                                        if (e instanceof TooManyRequestException
                                                || (e.getCause() != null && e.getCause() instanceof TooManyRequestException)) {
                                            return getNonProxiedResponseOnRetryFailed(requestId, params);
                                        }
                                        return getNonProxiedResponseOnRetryFailed(requestId, badUrlOptional, rootUrl, proxy, params);
                                    } else {
                                        if (e instanceof ProxyForbiddenException forbiddenException) {
                                            return Mono.just(ErrorResult.originalRequestError(forbiddenException.getStatusCode(),
                                                    params.getUrl(), e, forbiddenException.getBody()));
                                        } else if (e instanceof TooManyRequestException manyRequestException) {
                                            return Mono.just(ErrorResult.originalRequestError(manyRequestException.getStatusCode(),
                                                    params.getUrl(), e, manyRequestException.getBody()));
                                        } else {
                                            return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                                        }
                                    }
                                }
                                long exponentialTimeout = (long) (retriesRequest.getTimeout() * exponent);
                                retriesRequest.setTimeout(exponentialTimeout);
                                retriesRequestService.save(retriesRequest).subscribe();
                                params.setTimeout(exponentialTimeout);
                                return connectionErrorResult(e, params, requestId, headers);
                            });
                })
                .onErrorResume(throwable -> throwable instanceof ProxyGlobalException,
                        e -> Mono.just(ErrorResult.unknownError(params.getUrl(), e)))
                .doOnSuccess(result -> {
                    if (result.getCode() == 0) { // check if request is proxied
                        Optional<ProxyInstance.BadUrl> badUrl = getOptionalBadUrl(proxy, rootUrl);
                        if (!(result instanceof ErrorResult) && (badUrl.isPresent() && badUrl.get().getPoint() > 0)) {
                            log.warn("Reset to zero bad points for proxy - [{}:{}]", proxy.getHost(), proxy.getPort());
                            badUrl.get().setPoint(0);
                            proxyService.saveExisting(proxy);
                        }
                    }
                });

    }

    private Mono<Result> getNonProxiedResponseOnRetryFailed(String requestId, Optional<ProxyInstance.BadUrl> badUrlOptional,
                                                            String rootUrl, ProxyInstance proxy, ProxyRequestParams params) {
        saveForbiddenProxy(requestId, badUrlOptional, rootUrl, proxy);
        return getNonProxiedClientResponse(params, requestId);
    }

    private Mono<Result> getNonProxiedResponseOnRetryFailed(String requestId, ProxyRequestParams params) {
        return getNonProxiedClientResponse(params, requestId);
    }

    private void saveForbiddenProxy(String requestId, Optional<ProxyInstance.BadUrl> badUrlOptional,
                                    String rootUrl, ProxyInstance proxy) {
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
    }

    private Mono<Result> getNonProxiedClientResponse(ProxyRequestParams params, String requestId) {
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
                            log.error("Request without proxy failed cause: ", e);
                            if (e instanceof OriginalRequestException requestException) {
                                return Mono.just(ErrorResult.originalRequestError(requestException.getStatusCode(),
                                        params.getUrl(), requestException, requestException.getBody()));
                            } else {
                                return Mono.just(ErrorResult.unknownError(params.getUrl(), e));
                            }
                        });
    }

    private Mono<Result> connectionErrorResult(Throwable e, ProxyRequestParams params, String requestId, Map<String, String> headers) {
        log.warn("Trying to send request with another random proxy. Exception - " + e.getMessage());
        String contextId = headers.get("X-Context-Id");
        String appId = headers.get("X-App-Id");
        Mono<ProxyInstance> proxyInstance;
        boolean contextUserRequest = StringUtils.hasText(contextId) && StringUtils.hasText(appId);
        if (contextUserRequest) {
            log.info("Get proxy for app {}", appId);
            proxyInstance = proxyService.getByContextId(contextId, appId);
        } else {
            proxyInstance = proxyService.getRandomProxyWithoutMobile(params.getUrl());
        }
        return proxyInstance
                .delaySubscription(Duration.ofMillis(params.getTimeout() == null ? 0L : params.getTimeout()))
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return proxyInstance.flatMap(proxy -> getProxiedResponse(params, proxy, requestId, headers));
                    } else {
                        return getNonProxiedClientResponse(params, requestId);
                    }
                });
    }

    private Mono<Result> connectionMobileProxyErrorResult(Throwable e, ProxyRequestParams params, String requestId, Map<String, String> headers, String system) {
        log.warn("Trying to send request with another random proxy. Exception - " + e.getMessage());
        return proxyService.getRandomMobileProxy(0L)
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        Mono<ProxyInstance> proxyInstanceMono;
                        if (StringUtils.hasText(system)) {
                            proxyInstanceMono = proxyService.getMobileProxyBySystem(system, params.getTimeout());
                        } else {
                            proxyInstanceMono = proxyService.getRandomMobileProxy(params.getTimeout());
                        }
                        return proxyInstanceMono.flatMap(proxy ->
                                getMobileProxyProxiedResponse(params, proxy, requestId, headers));
                    } else {
                        return getNonProxiedClientResponse(params, requestId)
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
                .flatMap(body -> Mono.error(new OriginalRequestException("Proxy request failed", body,
                        response.rawStatusCode())));

    }

    private Mono<? extends Throwable> getForbiddenError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .onErrorResume(Objects::nonNull, e -> {
                    log.error("Unknown error", e);
                    return Mono.error(new ProxyGlobalException(e.getMessage(), e));
                })
                .flatMap(body -> Mono.error(new ProxyForbiddenException(("Proxy request forbidden, " +
                        "response code from proxied client - %s").formatted(response.rawStatusCode()), body,
                        response.rawStatusCode())));

    }

    private Mono<? extends Throwable> getTooManyRequestError(ClientResponse response) {
        return response.bodyToMono(String.class)
                .onErrorResume(Objects::nonNull, e -> {
                    log.error("Unknown error", e);
                    return Mono.error(new TooManyRequestException());
                })
                .flatMap(body -> Mono.error(new TooManyRequestException(("Proxy too many request, " +
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
            retriesRequest.setTimeout(retriesTimeout);
            return Mono.just(retriesRequest);
        }
    }

    private Mono<ProxyInstance> getRandomOrMobileProxy(ProxyRequestParams params, long timeout) {
        boolean isMarket = params.getContext()
                .stream()
                .filter(it -> it.getKey().equals("market"))
                .anyMatch(it -> it.getValue().equals("KE") || it.getValue().equals("UZUM"));
        if (isMarket) {
            params.setProxySource(ProxySource.MOBILE_PROXY);
        } else {
            params.setProxySource(ProxySource.PROXY_HOUSE); // Это сделано чтобы не попадать в мобильные прокси, в целом можно будет удалить
        }
        Mono<ProxyInstance> proxyInstance;
        if (isMarket) {
            String market = params.getContext()
                    .stream()
                    .filter(it -> it.getKey().equals("market"))
                    .filter(it -> it.getValue().equals("KE") || it.getValue().equals("UZUM"))
                    .map(ProxyRequestParams.ContextValue::getValue)
                    .findFirst()
                    .map(String::valueOf)
                    .orElseThrow();
            proxyInstance = proxyService.getMobileProxyBySystem(market, timeout);
        } else {
            proxyInstance =
                    ProxySource.MOBILE_PROXY.equals(params.getProxySource())
                            ? proxyService.getRandomMobileProxy(params.getTimeout())
                            : proxyService.getRandomProxyWithoutMobile(params.getUrl())
                            .delaySubscription(Duration.ofMillis(timeout));
        }
        return proxyInstance;
    }

    private Mono<ProxyInstance> getProxy(ProxyRequestParams params, Map<String, String> headers, long timeout) {
        String contextId = headers.get("X-Context-Id");
        String appId = headers.get("X-App-Id");
        Mono<ProxyInstance> proxyInstance;
        if (StringUtils.hasText(contextId) && StringUtils.hasText(appId)) {
            log.info("Get proxy for app {}", appId);
            proxyInstance = proxyService.getByContextId(contextId, appId);
        } else {
            proxyInstance = getRandomOrMobileProxy(params, timeout);
        }
        return proxyInstance;
    }

    private boolean needToChangeIp(Throwable e) {
        return (e instanceof ProxyForbiddenException || (e.getCause() != null && e.getCause() instanceof ProxyForbiddenException))
                || (!(e instanceof ReadTimeoutException)
                || !(e.getCause() != null && e.getCause() instanceof ReadTimeoutException)
                || !(e.getCause() != null && e.getCause() instanceof PrematureCloseException));
    }

    private boolean originalRequestException(Throwable e) {
        return (e instanceof TooManyRequestException || (e.getCause() != null && e.getCause() instanceof TooManyRequestException) ||
                (e instanceof ProxyForbiddenException || (e.getCause() != null && e.getCause() instanceof ProxyForbiddenException)));
    }
}
