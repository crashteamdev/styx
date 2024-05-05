package dev.crashteam.styx.service.proxy;


import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.proxy.ProxySource;
import dev.crashteam.styx.repository.proxy.ProxyRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CachedProxyService {

    private final ProxyRepositoryImpl proxyRepository;

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

    public void saveExisting(ProxyInstance proxy) {
        String badUrls = !CollectionUtils.isEmpty(proxy.getBadUrls()) ?
                proxy.getBadUrls().stream().map(ProxyInstance.BadUrl::getUrl).collect(Collectors.joining(","))
                : "";
        log.warn("Saving proxy [{}:{}] with bad urls - {}", proxy.getHost(), proxy.getPort(), badUrls);
        proxyRepository.saveExisting(proxy).subscribe();
    }

    public Mono<ProxyInstance> getMobileProxyByKey(String proxyKey) {
        return proxyRepository.getMobileProxyByKey(proxyKey);
    }

    public Mono<ProxyInstance> getRandomProxy(Long timeout) {
        return proxyRepository.getRandomProxy().delaySubscription(Duration.ofMillis(timeout));
    }

    public Mono<ProxyInstance> getRandomProxy(ProxySource proxySource, String url) {
        String rootUrl;
        try {
            rootUrl = new URL(url).toURI().resolve("/").toString();
        } catch (Exception e) {
            log.error("Exception while resolving url - {}", url, e);
            throw new RuntimeException(e);
        }
        Random random = new Random();
        Flux<ProxyInstance> proxies = proxyRepository.getRandomProxyNotIncludeForbidden(proxySource, rootUrl);
        return proxies
                .count()
                .map(s -> {
                    if (s != null && s > 1) {
                        return random.nextLong(s);
                    }
                    return 0L;
                })
                .flatMap(index -> proxies.elementAt(Math.toIntExact(index))
                .switchIfEmpty(Mono.empty()));

    }

    public Mono<ProxyInstance> getRandomMobileProxy(Long timeout) {
        Random random = new Random();
        Flux<ProxyInstance> mobileProxies = proxyRepository.getMobileProxies();
        return mobileProxies
                .delaySubscription(Duration.ofMillis(timeout))
                .count()
                .map(s -> {
                    if (s != null && s > 1) {
                        return random.nextLong(s);
                    }
                    return 0L;
                })
                .flatMap(index -> mobileProxies
                        .count()
                        .filter(size -> size > 0)
                        .flatMap(p -> mobileProxies.elementAt(Math.toIntExact(index))))
                .switchIfEmpty(Mono.empty());
    }

    public Flux<ProxyInstance> getBadMobileProxies() {
        return proxyRepository.getBadMobileProxies();
    }

    public Flux<ProxyInstance> getMobileProxies(Long timeout) {
        return proxyRepository.getMobileProxies();
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
