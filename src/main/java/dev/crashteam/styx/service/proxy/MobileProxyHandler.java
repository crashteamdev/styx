package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.service.proxy.provider.MobileProxyService;
import dev.crashteam.styx.util.RandomUserAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@EnableAsync
@RequiredArgsConstructor
public class MobileProxyHandler {

    private final MobileProxyService mobileProxyService;
    private final CachedProxyService proxyService;

    @Scheduled(cron = "${application.scheduler.mobile-proxy.change-ip-cron}")
    @SchedulerLock(name = "changeMobileProxyIp")
    public void changeMobileProxyIp() {
        LockAssert.assertLocked();
        proxyService.getBadMobileProxies()
                .doOnNext(it -> {
                    it.setUserAgent(RandomUserAgent.getRandomUserAgent());
                    proxyService.saveExisting(it);
                })
                .doOnNext(mobileProxyService::changeIp).subscribe();
    }

    @Scheduled(cron = "${application.scheduler.mobile-proxy.reload-proxy}")
    @SchedulerLock(name = "reloadProxy")
    public void reloadProxy() {
        LockAssert.assertLocked();
        proxyService.getMobileProxies()
                .doOnNext(mobileProxyService::reloadProxy).subscribe();

    }
}
