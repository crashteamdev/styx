package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.service.proxy.provider.MobileProxyService;
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
public class MobileProxyIPChanger {

    private final MobileProxyService mobileProxyService;

    //@Scheduled(cron = "${integration.mobile-proxy.change-ip-cron}")
    //@SchedulerLock(name = "changeMobileProxyIp")
    public void fillRedisCacheOnSchedule() {
        LockAssert.assertLocked();
        mobileProxyService.changeIp();
    }
}
