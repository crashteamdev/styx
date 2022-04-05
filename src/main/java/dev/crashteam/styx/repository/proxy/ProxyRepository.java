package dev.crashteam.styx.repository.proxy;

import dev.crashteam.styx.model.proxy.ProxyInstance;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Flux;

public interface ProxyRepository extends ReactiveCrudRepository<ProxyInstance, String> {

    Flux<ProxyInstance> findActive();
}
