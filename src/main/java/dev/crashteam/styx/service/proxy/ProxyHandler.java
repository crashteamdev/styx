package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.model.proxy.ProxyInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

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

    @Scheduled(cron = "${application.scheduler.redis.forbidden-url-cron}")
    @SchedulerLock(name = "cleanForbiddenUrls")
    public void cleanForbiddenUrlsOnSchedule() {
        LockAssert.assertLocked();
        cleanForbiddenUrls();
    }

    private void cleanForbiddenUrls() {
        proxyService.findAll()
                .filter(Objects::nonNull)
                .filter(proxy -> !CollectionUtils.isEmpty(proxy.getNotAvailableUrls()))
                .flatMap(proxy -> {
                    Set<ProxyInstance.Forbidden> expired = new HashSet<>();
                    proxy.getNotAvailableUrls().forEach(it -> {
                        LocalDateTime dateTime = LocalDateTime
                                .ofInstant(Instant.ofEpochMilli(it.getExpireTime()), TimeZone.getDefault().toZoneId());
                        if (LocalDateTime.now().isAfter(dateTime)) {
                            expired.add(it);
                        }
                    });
                    for (var forbidden : expired) {
                        log.info("Removing forbidden url - [{}], for proxy [{}:{}]", forbidden.getUrl(),
                                proxy.getHost(), proxy.getPort());
                        proxy.getNotAvailableUrls().remove(forbidden);
                    }
                    return proxyService.saveExisting(proxy);
                }).subscribe();
    }

    @PostConstruct
    private void fillRedisCache() {
        final Flux<ProxyInstance> defaultProxyFlux = Flux.fromIterable(proxyProviders)
                .flatMap(ProxyProvider::getProxy);
        proxyService.saveAll(defaultProxyFlux)
                .subscribe();
    }

}
