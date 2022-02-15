package dev.crashteam.styx.repository.proxy;

import dev.crashteam.styx.model.proxy.CachedProxy;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ProxyRepository extends ReactiveCrudRepository<CachedProxy, String> {

    Flux<CachedProxy> findActive();
}
