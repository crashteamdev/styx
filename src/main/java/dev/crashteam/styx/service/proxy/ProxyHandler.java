package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.model.proxy.ProxyInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableAsync
public class ProxyHandler {

    private final List<ProxyProvider> proxyProviders;
    private final CachedProxyService proxyService;

    @Scheduled(cron = "${application.scheduler.redis.cache-cron}")
    @SchedulerLock(name = "fillRedisCache")
    public void fillRedisCacheOnSchedule() {
        log.info("Filling redis cache with proxy values...");
        LockAssert.assertLocked();
        fillRedisCache();
    }

    @PostConstruct
    private void fillRedisCache() {
        final Flux<ProxyInstance> defaultProxyFlux = Flux.fromIterable(proxyProviders)
                .flatMap(ProxyProvider::getProxy);
        proxyService.saveAll(defaultProxyFlux)
                .subscribe();
    }

}
