package dev.crashteam.styx.repository.request;

import dev.crashteam.styx.model.request.RetriesRequest;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import reactor.core.publisher.Mono;

public interface RequestRepository extends ReactiveCrudRepository<RetriesRequest, String> {

    Mono<RetriesRequest> findByRequestId(String requestId);

    Mono<Void> deleteByRequestId(String requestId);

    Mono<Boolean> existsByRequestId(String requestId);
}
