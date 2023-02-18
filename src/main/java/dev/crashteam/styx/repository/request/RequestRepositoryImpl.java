package dev.crashteam.styx.repository.request;

import dev.crashteam.styx.model.RedisKey;
import dev.crashteam.styx.model.request.RetriesRequest;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class RequestRepositoryImpl implements RequestRepository {

    private final ReactiveRedisOperations<String, RetriesRequest> redisOperations;
    private final ReactiveHashOperations<String, String, RetriesRequest> hashOperations;

    @Autowired
    public RequestRepositoryImpl(ReactiveRedisOperations<String, RetriesRequest> redisRetriesRequestOperations) {
        this.redisOperations = redisRetriesRequestOperations;
        this.hashOperations = redisRetriesRequestOperations.opsForHash();
    }

    @Override
    public <S extends RetriesRequest> Mono<S> save(S entity) {
        return hashOperations.put(RedisKey.REQUEST_KEY.getValue(), entity.getRequestId(), entity).thenReturn(entity);
    }

    @Override
    public <S extends RetriesRequest> Flux<S> saveAll(Iterable<S> entities) {
        return Flux.fromIterable(entities)
                .flatMap(this::save);
    }

    @Override
    public Mono<Void> deleteByRequestId(String requestId) {
        return hashOperations.remove(RedisKey.REQUEST_KEY.getValue(), requestId).then();
    }

    @Override
    public Mono<Boolean> existsByRequestId(String requestId) {
        return hashOperations.hasKey(RedisKey.REQUEST_KEY.getValue(), requestId);
    }

    @Override
    public <S extends RetriesRequest> Flux<S> saveAll(Publisher<S> entityStream) {
        return null;
    }

    @Override
    public Mono<RetriesRequest> findById(String s) {
        return hashOperations.get(RedisKey.REQUEST_KEY.getValue(), s);
    }

    @Override
    public Mono<RetriesRequest> findById(Publisher<String> id) {
        return null;
    }

    @Override
    public Mono<Boolean> existsById(String s) {
        return null;
    }

    @Override
    public Mono<Boolean> existsById(Publisher<String> id) {
        return null;
    }

    @Override
    public Flux<RetriesRequest> findAll() {
        return null;
    }

    @Override
    public Flux<RetriesRequest> findAllById(Iterable<String> strings) {
        return null;
    }

    @Override
    public Flux<RetriesRequest> findAllById(Publisher<String> idStream) {
        return null;
    }

    @Override
    public Mono<Long> count() {
        return null;
    }

    @Override
    public Mono<Void> deleteById(String s) {
        return null;
    }

    @Override
    public Mono<Void> deleteById(Publisher<String> id) {
        return null;
    }

    @Override
    public Mono<Void> delete(RetriesRequest entity) {
        return null;
    }

    @Override
    public Mono<Void> deleteAllById(Iterable<? extends String> strings) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll(Iterable<? extends RetriesRequest> entities) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll(Publisher<? extends RetriesRequest> entityStream) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll() {
        return null;
    }

    @Override
    public Mono<RetriesRequest> findByRequestId(String requestId) {
        return hashOperations.values(RedisKey.REQUEST_KEY.getValue())
                .filter(retriesRequest -> requestId.equals(retriesRequest.getRequestId()))
                .singleOrEmpty();
    }
}