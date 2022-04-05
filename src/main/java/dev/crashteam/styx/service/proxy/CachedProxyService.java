package dev.crashteam.styx.service.proxy;


import dev.crashteam.styx.model.proxy.ProxyInstance;
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

    public Flux<ProxyInstance> getActive() {
        return proxyRepository.findActive();
    }

    public Flux<ProxyInstance> getAll() {
        return proxyRepository.findAll();
    }

    public Mono<ProxyInstance> save(ProxyInstance proxy) {
        log.info("Saving proxy [{}, {}] with values - Active: {}. Bad proxy points: {}", proxy.getHost(), proxy.getPort(),
                proxy.getActive(), proxy.getBadProxyPoint());
        return proxyRepository.save(proxy);
    }

    public Flux<ProxyInstance> saveAll(List<ProxyInstance> proxies) {
        return proxyRepository.saveAll(proxies);
    }

    public Flux<ProxyInstance> saveAll(Publisher<ProxyInstance> entityStream) {
        return proxyRepository.saveAll(entityStream);
    }

    public void setBadProxyOnError(ProxyInstance proxy, Throwable ex) {
        proxy.setBadProxyPoint(proxy.getBadProxyPoint() + 1);
        if (proxy.getBadProxyPoint() == 3) {
            proxy.setActive(false);
        }
        this.save(proxy).subscribe();
        log.error("Proxy - [{}:{}] marked as unstable", proxy.getHost(), proxy.getPort(), ex);
    }
}
