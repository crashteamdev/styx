package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.model.proxy.CachedProxy;
import dev.crashteam.styx.model.proxy.ProxyLineResponse;
import dev.crashteam.styx.model.proxy.ProxySource;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;

@Service
@RequiredArgsConstructor
@EnableAsync
public class ProxyHandler {

    private final ProxyLineService proxyLineService;
    private final CachedProxyService proxyService;

    @Async
    //@Scheduled(cron = "${application.redis.cache-cron}")
    @PostConstruct
    public void fillRedisCachedProxy() {
        proxyService.saveAll(getAllProxies())
                .subscribe();
    }

    private Flux<CachedProxy> getAllProxies() {
        return getProxyLineValues();
    }

    private Flux<CachedProxy> getProxyLineValues() {
        return proxyLineService.getProxy()
                .map(ProxyLineResponse::getResults)
                .flatMap(Flux::fromIterable)
                .map((ProxyLineResponse.ProxyLineResult p) -> {
                    CachedProxy cachedProxy = new CachedProxy();
                    cachedProxy.setHost(p.getIp());
                    cachedProxy.setPort(p.getPortHttp());
                    cachedProxy.setActive(true);
                    cachedProxy.setProxySource(ProxySource.PROXY_LINE);
                    cachedProxy.setUser(p.getUser());
                    cachedProxy.setPassword(p.getPassword());
                    return cachedProxy;
                });

    }

}
