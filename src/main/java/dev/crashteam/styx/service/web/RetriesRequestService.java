package dev.crashteam.styx.service.web;

import dev.crashteam.styx.model.request.RetriesRequest;
import dev.crashteam.styx.repository.request.RequestRepositoryImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class RetriesRequestService {

    private final RequestRepositoryImpl repository;

    public Mono<RetriesRequest> save(RetriesRequest request) {
        return repository.save(request);
    }

    public Mono<RetriesRequest> findByRequestId(String requestId) {
        return repository.findByRequestId(requestId);
    }

    public Mono<Void> deleteByRequestId(String requestId) {
        return repository.deleteByRequestId(requestId);
    }

    public Mono<Boolean> existsByRequestId(String requestId) {
        return repository.existsByRequestId(requestId);
    }
}
