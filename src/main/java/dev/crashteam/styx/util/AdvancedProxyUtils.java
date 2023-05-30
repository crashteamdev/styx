package dev.crashteam.styx.util;

import dev.crashteam.styx.exception.*;
import dev.crashteam.styx.model.ContextKey;
import dev.crashteam.styx.model.content.BaseResolver;
import dev.crashteam.styx.model.content.DefaultResolver;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.web.ErrorResult;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import dev.crashteam.styx.model.web.Result;
import io.netty.handler.proxy.HttpProxyHandler;
import io.netty.handler.proxy.ProxyConnectException;
import io.netty.handler.ssl.SslHandshakeTimeoutException;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Random;

public class AdvancedProxyUtils {

    public static Object getObjectValueByContentType(List<BaseResolver> resolvers, Object value, String contentType) {
        BaseResolver resolver = resolvers.stream()
                .filter(it -> contentType.equalsIgnoreCase(it.getMediaType()))
                .findFirst()
                .orElseGet(DefaultResolver::new);
        return resolver.formObjectValue(value);
    }

    public static boolean contextKeyExists(List<ProxyRequestParams.ContextValue> context, ContextKey contextKey) {
        if (CollectionUtils.isEmpty(context)) return false;
        return context.stream()
                .anyMatch(it -> it.getKey().equalsIgnoreCase(contextKey.getValue()));
    }

    public static ResponseEntity<Result> getResponseEntityWithStatus(Result result) {
        if (result instanceof ErrorResult errorResult) {
            Throwable exception = errorResult.getException();
            if (exception instanceof HeadersParseException ||
                    exception instanceof KeyNotSupportedException ||
                    exception instanceof NoContentTypeHeaderException ||
                    exception instanceof NonValidHttpMethodException) {
                return ResponseEntity.badRequest().body(result);
            } else {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(result);
            }
        }
        return ResponseEntity.ok(result);
    }

    public static boolean badProxyError(Throwable throwable) {
        return throwable instanceof ConnectException
                || throwable instanceof SslHandshakeTimeoutException
                || throwable instanceof ReadTimeoutException
                || throwable instanceof ProxyForbiddenException
                || (throwable.getCause() != null && throwable.getCause() instanceof ConnectException)
                || (throwable.getCause() != null && throwable.getCause() instanceof SslHandshakeTimeoutException)
                || (throwable.getCause() != null && throwable.getCause() instanceof HttpProxyHandler.HttpProxyConnectException)
                || (throwable.getCause() != null && throwable.getCause() instanceof ReadTimeoutException)
                || (throwable.getCause() != null && throwable.getCause() instanceof ProxyForbiddenException);
    }

    public static Mono<ProxyInstance> getRandomProxy(Long timeout, Flux<ProxyInstance> activeProxies) {
        Random random = new Random();
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

    @SneakyThrows
    public static String getRootUrl(String url) {
        return new URL(url).toURI().resolve("/").toString();
    }
}
