package dev.crashteam.styx.service.proxy;


import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.repository.proxy.ProxyRepositoryImpl;
import dev.crashteam.styx.util.AdvancedProxyUtils;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedProxyService {

    private final ProxyRepositoryImpl proxyRepository;

    @Value("${application.forbidden-expire}")
    private Long expireMinutes;

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

    @SneakyThrows
    public void putForbiddenUrl(String url, ProxyInstance proxy) {
        String rootUrl = new URL(url).toURI().resolve("/").toString();
        log.warn("Put forbidden url - [{}] for proxy - [{}:{}]", rootUrl, proxy.getHost(), proxy.getPort());
        if (!CollectionUtils.isEmpty(proxy.getNotAvailableUrls())) {
            proxy.getNotAvailableUrls().put(rootUrl, LocalDateTime.now().plusMinutes(expireMinutes));
        } else {
            Map<String, LocalDateTime> forbiddenUrlsMap = new ConcurrentHashMap<>();
            forbiddenUrlsMap.put(rootUrl, LocalDateTime.now().plusMinutes(expireMinutes));
            proxy.setNotAvailableUrls(forbiddenUrlsMap);
        }
        proxyRepository.save(proxy).subscribe();
    }

    public Mono<ProxyInstance> getRandomProxy(Long timeout) {
        return proxyRepository.getRandomProxy().delaySubscription(Duration.ofMillis(timeout));
    }

    @SneakyThrows
    public Mono<ProxyInstance> getRandomProxy(Long timeout, String url) {
        String urlRoot = new URL(url).toURI().resolve("/").toString();
        Flux<ProxyInstance> proxyInstanceFlux = proxyRepository.findAll()
                .filter(proxy -> !(!CollectionUtils.isEmpty(proxy.getNotAvailableUrls())
                        && proxy.getNotAvailableUrls().keySet().stream().anyMatch(urlRoot::equals)));
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
