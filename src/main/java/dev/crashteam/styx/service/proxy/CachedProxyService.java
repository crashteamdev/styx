package dev.crashteam.styx.service.proxy;


import dev.crashteam.styx.model.proxy.CachedProxy;
import dev.crashteam.styx.repository.proxy.ProxyRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedProxyService {

    private final ProxyRepositoryImpl proxyRepository;

    public Flux<CachedProxy> getActive() {
        return proxyRepository.findActive();
    }

    public Flux<CachedProxy> getAll() {
        return proxyRepository.findAll();
    }

    public Mono<CachedProxy> save(CachedProxy proxy) {
        log.info("Saving proxy [{}, {}] with values - Active: {}. Bad proxy points: {}", proxy.getHost(), proxy.getPort(),
                proxy.getActive(), proxy.getBadProxyPoint());
        return proxyRepository.save(proxy);
    }

    public Flux<CachedProxy> saveAll(List<CachedProxy> proxies) {
        return proxyRepository.saveAll(proxies);
    }

    public Flux<CachedProxy> saveAll(Publisher<CachedProxy> entityStream) {
        return proxyRepository.saveAll(entityStream);
    }

    public void setBadProxyOnError(CachedProxy proxy, Throwable ex) {
        proxy.setBadProxyPoint(proxy.getBadProxyPoint() + 1);
        if (proxy.getBadProxyPoint() == 3) {
            proxy.setActive(false);
        }
        this.save(proxy).subscribe();
        log.error("Proxy - [{}:{}] marked as unstable", proxy.getHost(), proxy.getPort(), ex);
    }
}
