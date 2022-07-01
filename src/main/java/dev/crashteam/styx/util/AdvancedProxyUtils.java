package dev.crashteam.styx.util;

import dev.crashteam.styx.model.ContextKey;
import dev.crashteam.styx.model.content.BaseResolver;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;

public class AdvancedProxyUtils {

    public static Object getObjectValueByContentType(List<BaseResolver> resolvers, Object value, String contentType) {
        Optional<BaseResolver> resolverOptional = resolvers.stream()
                .filter(resolver -> resolver.getMediaType().equalsIgnoreCase(contentType))
                .findFirst();
        if (resolverOptional.isPresent()) {
            return resolverOptional.get().formObjectValue(value);
        }
        return value;
    }

    public static boolean contextKeyExists(List<ProxyRequestParams.ContextValue> context, ContextKey contextKey) {
        if (CollectionUtils.isEmpty(context)) return false;
        return context.stream()
                .anyMatch(it -> it.getKey().equalsIgnoreCase(contextKey.getValue()));
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
}
