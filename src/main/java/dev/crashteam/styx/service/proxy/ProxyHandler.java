package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.service.forbidden.ForbiddenProxyService;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import dev.crashteam.styx.service.proxy.provider.ProxyProvider;
import dev.crashteam.styx.util.RandomUserAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
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
    private final ForbiddenProxyService forbiddenProxyService;

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

    @Scheduled(cron = "${application.scheduler.redis.update-user-agents}")
    @SchedulerLock(name = "updateUserAgents")
    public void updateUserAgents() {
        LockAssert.assertLocked();
        proxyService.findAll()
                .doOnNext(it -> {
                    it.setUserAgent(RandomUserAgent.getRandomUserAgent());
                    proxyService.saveExisting(it);
                }).subscribe();
    }

    private void cleanForbiddenUrls() {
        forbiddenProxyService.findAllWithKeys()
                .map(entry -> {
                    if (entry.getValue() != null) {
                        LocalDateTime dateTime = LocalDateTime
                                .ofInstant(Instant.ofEpochMilli(entry.getValue().getExpireTime()),
                                        TimeZone.getDefault().toZoneId());
                        if (LocalDateTime.now().isAfter(dateTime)) {
                            return entry.getKey();
                        }
                    }
                    return "";
                })
                .filter(StringUtils::hasText)
                .doOnNext(forbiddenProxyService::deleteByHashKey).subscribe();
    }

    @PostConstruct
    private void fillRedisCache() {
        final Flux<ProxyInstance> defaultProxyFlux = Flux.fromIterable(proxyProviders)
                .flatMap(ProxyProvider::getProxy);
        proxyService.saveAll(defaultProxyFlux)
                .subscribe();
    }
    public void fillRedisCacheWithinTransaction() {
        final Flux<ProxyInstance> defaultProxyFlux = Flux.fromIterable(proxyProviders)
                .flatMap(ProxyProvider::getProxy);
        proxyService.saveAll(defaultProxyFlux)
                .subscribe();
    }

}
