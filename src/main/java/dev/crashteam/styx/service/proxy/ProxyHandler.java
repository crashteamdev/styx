package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.model.proxy.ProxyInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
@RequiredArgsConstructor
@EnableAsync
public class ProxyHandler {

    private final List<ProxyProvider> proxyProviders;
    private final CachedProxyService proxyService;

    @Scheduled(cron = "${application.redis.cache-cron}")
    @PostConstruct
    public void fillRedisCache() {
        final Flux<ProxyInstance> defaultProxyFlux = Flux.fromIterable(proxyProviders)
                .flatMap(ProxyProvider::getProxy);
        proxyService.saveAll(defaultProxyFlux)
                .subscribe();
    }

}
