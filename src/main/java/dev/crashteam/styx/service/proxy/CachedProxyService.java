package dev.crashteam.styx.service.proxy;


import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.repository.forbidden.ForbiddenProxyRepository;
import dev.crashteam.styx.repository.proxy.ProxyRepositoryImpl;
import dev.crashteam.styx.util.AdvancedProxyUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedProxyService {

    private final ProxyRepositoryImpl proxyRepository;
    private final ForbiddenProxyRepository forbiddenProxyRepository;

    public Flux<ProxyInstance> getActive() {
        return proxyRepository.findActive();
    }

    public Flux<ProxyInstance> findAll() {
        return proxyRepository.findAll();
    }

    public void deleteByHashKey(ProxyInstance proxy) {
        log.warn("Deleting unstable proxy [{}:{}]", proxy.getHost(), proxy.getPort());
        proxyRepository.deleteByHashKey(proxy).subscribe();
    }

    public Mono<ProxyInstance> saveExisting(ProxyInstance proxy) {
        return proxyRepository.saveExisting(proxy);
    }

    public Mono<ProxyInstance> getRandomProxy(Long timeout) {
        return proxyRepository.getRandomProxy().delaySubscription(Duration.ofMillis(timeout));
    }

    @SneakyThrows
    public Mono<ProxyInstance> getRandomProxy(Long timeout, String url) {
        String rootUrl = new URL(url).toURI().resolve("/").toString();
        Flux<ProxyInstance> proxyInstanceFlux = proxyRepository.findAll()
                .filterWhen(proxy -> forbiddenProxyRepository.notExistsByKey(proxy, rootUrl));
        return AdvancedProxyUtils.getRandomProxy(timeout, proxyInstanceFlux);
    }

    public Mono<ProxyInstance> save(ProxyInstance proxy) {
        log.info("Saving proxy [{}:{}] with values - Active: {}. Bad proxy points: {}", proxy.getHost(), proxy.getPort(),
                proxy.getActive(), proxy.getBadProxyPoint());
        return proxyRepository.save(proxy);
    }

    public Flux<ProxyInstance> saveAll(List<ProxyInstance> proxies) {
        return proxyRepository.saveAll(proxies);
    }

    public Flux<ProxyInstance> saveAll(Publisher<ProxyInstance> entityStream) {
        return proxyRepository.saveAll(entityStream);
    }

    public Mono<Boolean> deleteAll() {
        return proxyRepository.deleteAllByKey();
    }

    public void setBadProxyOnError(ProxyInstance proxy, Throwable ex) {
        proxy.setBadProxyPoint(proxy.getBadProxyPoint() + 1);
        if (proxy.getBadProxyPoint() == 2) {
            proxy.setActive(false);
            this.deleteByHashKey(proxy);
            return;
        }
        this.save(proxy).subscribe();
        log.warn("Proxy - [{}:{}] marked as unstable", proxy.getHost(), proxy.getPort(), ex);
    }
}
