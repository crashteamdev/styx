package dev.crashteam.styx.repository.proxy;

import dev.crashteam.styx.model.RedisKey;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class ProxyRepositoryImpl implements ProxyRepository {

    private final ReactiveRedisOperations<String, ProxyInstance> redisOperations;
    private final ReactiveHashOperations<String, String, ProxyInstance> hashOperations;

    @Autowired
    public ProxyRepositoryImpl(ReactiveRedisOperations<String, ProxyInstance> redisOperations) {
        this.redisOperations = redisOperations;
        this.hashOperations = redisOperations.opsForHash();
    }

    @Override
    public Flux<ProxyInstance> findActive() {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue())
                .filter(ProxyInstance::getActive);
    }

    @Override
    public Flux<ProxyInstance> findAll() {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue());
    }

    @Override
    public <S extends ProxyInstance> Mono<S> save(S entity) {
        return hashOperations.put(RedisKey.PROXY_KEY.getValue(), getRedisHashKey(entity), entity)
                .thenReturn(entity);
    }

    @Override
    public <S extends ProxyInstance> Flux<S> saveAll(Iterable<S> entities) {
        return Flux.fromIterable(entities)
                .flatMap(this::save);
    }

    @Override
    public <S extends ProxyInstance> Flux<S> saveAll(Publisher<S> entityStream) {
        return Flux.from(entityStream)
                .flatMap(this::save);
    }

    @Override
    public Mono<ProxyInstance> findById(String s) {
        return null;
    }

    @Override
    public Mono<ProxyInstance> findById(Publisher<String> id) {
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
    public Flux<ProxyInstance> findAllById(Iterable<String> strings) {
        return null;
    }

    @Override
    public Flux<ProxyInstance> findAllById(Publisher<String> idStream) {
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
    public Mono<Void> delete(ProxyInstance entity) {
        return null;
    }

    @Override
    public Mono<Void> deleteAllById(Iterable<? extends String> strings) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll(Iterable<? extends ProxyInstance> entities) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll(Publisher<? extends ProxyInstance> entityStream) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll() {
        return null;
    }

    private String getRedisHashKey(ProxyInstance proxy) {
        return proxy.getHost() + ":" + proxy.getPort();

    }
}
