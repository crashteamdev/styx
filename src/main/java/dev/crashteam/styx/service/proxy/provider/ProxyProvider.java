package dev.crashteam.styx.service.proxy.provider;


import dev.crashteam.styx.model.proxy.ProxyInstance;
import reactor.core.publisher.Flux;

public interface ProxyProvider {
    Flux<ProxyInstance> getProxy();
}
