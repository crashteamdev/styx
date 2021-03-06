package dev.crashteam.styx.service.proxy;


import dev.crashteam.styx.model.proxy.ProxyInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ExternalSourceProxyService {

    private final CachedProxyService cachedProxyService;

    public Flux<ProxyInstance> saveProxyFromExternalSource(List<ProxyInstance> cachedProxies) {
        return cachedProxyService.saveAll(cachedProxies);
    }
}
