package com.gloombox.styx.service.proxy;


import com.gloombox.styx.model.proxy.CachedProxy;
import com.gloombox.styx.repository.ProxyRepositoryImpl;
import lombok.RequiredArgsConstructor;
import org.reactivestreams.Publisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CachedProxyService {

    private final ProxyRepositoryImpl proxyRepository;

    public Flux<CachedProxy> getActive() {
        return proxyRepository.findActive();
    }

    public Flux<CachedProxy> getAll() {
        return proxyRepository.findAll();
    }

    public Mono<CachedProxy> save(CachedProxy proxy) {
        return proxyRepository.save(proxy);
    }

    public Flux<CachedProxy> saveAll(List<CachedProxy> proxies) {
        return proxyRepository.saveAll(proxies);
    }

    public Flux<CachedProxy> saveAll(Publisher<CachedProxy> entityStream) {
        return proxyRepository.saveAll(entityStream);
    }
}
