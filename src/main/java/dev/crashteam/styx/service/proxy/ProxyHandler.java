package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.model.proxy.CachedProxy;
import dev.crashteam.styx.model.proxy.Proxy6Dto;
import dev.crashteam.styx.model.proxy.ProxySource;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import javax.annotation.PostConstruct;
import java.util.Map;

@Service
@RequiredArgsConstructor
@EnableAsync
public class ProxyHandler {

    private final Proxy6Service proxy6Service;
    private final CachedProxyService proxyService;

    @Async
    //@Scheduled(cron = "${application.redis.cache-cron}")
    @PostConstruct
    public void fillRedisCachedProxy() {
        proxyService.saveAll(getAllProxies())
                .subscribe();
    }

    private Flux<CachedProxy> getAllProxies() {
        return getProxy6Values();
    }

    private Flux<CachedProxy> getProxy6Values() {
        return proxy6Service.getProxy()
                .map(Proxy6Dto::getProxies)
                .filter(p -> !p.values().isEmpty())
                .map(Map::values)
                .flatMap(Flux::fromIterable)
                .map(p -> {
                    CachedProxy cachedProxy = new CachedProxy();
                    cachedProxy.setHost(p.getHost());
                    cachedProxy.setPort(p.getPort());
                    cachedProxy.setActive(true);
                    cachedProxy.setProxySource(ProxySource.PROXY6);
                    cachedProxy.setUser(p.getUser());
                    cachedProxy.setPassword(p.getPass());
                    return cachedProxy;
                });

    }

}
