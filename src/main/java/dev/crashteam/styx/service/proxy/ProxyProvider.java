package dev.crashteam.styx.service.proxy;


import dev.crashteam.styx.model.proxy.ProxyInstance;
import reactor.core.publisher.Flux;

public interface ProxyProvider {
    Flux<ProxyInstance> getProxy();
}
