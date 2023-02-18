package dev.crashteam.styx.service.web;

import dev.crashteam.styx.model.request.RetriesRequest;
import dev.crashteam.styx.repository.request.RequestRepositoryImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class RetriesRequestService {

    private final RequestRepositoryImpl repository;

    public Mono<RetriesRequest> save(RetriesRequest request) {
        log.warn("Request with UUID - [{}] failed, trying to retry with another proxy. Retries left - {}", request.getRequestId(),
                request.getRetries());
        return repository.save(request);
    }

    public Mono<RetriesRequest> findByRequestId(String requestId) {
        return repository.findByRequestId(requestId);
    }

    public Mono<Void> deleteByRequestId(String requestId) {
        log.warn("Deleting retries request with UUID - [{}]", requestId);
        return repository.deleteByRequestId(requestId);
    }

    public Mono<Void> deleteIfExistByRequestId(String requestId) {
        return existsByRequestId(requestId)
                .flatMap(exist -> {
                    if (exist) {
                        return deleteByRequestId(requestId);
                    }
                    return Mono.empty();
                });
    }

    public Mono<Boolean> existsByRequestId(String requestId) {
        return repository.existsByRequestId(requestId);
    }
}
