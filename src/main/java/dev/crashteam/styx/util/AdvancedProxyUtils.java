package dev.crashteam.styx.util;

import dev.crashteam.styx.model.ContextKey;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Random;

public class AdvancedProxyUtils {

    public static boolean contextKeyExists(List<ProxyRequestParams.ContextValue> context, ContextKey contextKey) {
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
