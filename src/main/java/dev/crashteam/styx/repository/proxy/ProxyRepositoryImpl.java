package dev.crashteam.styx.repository.proxy;

import dev.crashteam.styx.model.RedisKey;
import dev.crashteam.styx.model.proxy.CachedProxy;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public class ProxyRepositoryImpl implements ProxyRepository {

    private final ReactiveRedisOperations<String, CachedProxy> redisOperations;
    private final ReactiveHashOperations<String, String, CachedProxy> hashOperations;

    @Autowired
    public ProxyRepositoryImpl(ReactiveRedisOperations<String, CachedProxy> redisOperations) {
        this.redisOperations = redisOperations;
        this.hashOperations = redisOperations.opsForHash();
    }

    @Override
    public Flux<CachedProxy> findActive() {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue())
                .filter(CachedProxy::getActive);
    }

    @Override
    public Flux<CachedProxy> findAll() {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue());
    }

    @Override
    public <S extends CachedProxy> Mono<S> save(S entity) {
        return hashOperations.put(RedisKey.PROXY_KEY.getValue(), getRedisHashKey(entity), entity)
                .thenReturn(entity);
    }

    @Override
    public <S extends CachedProxy> Flux<S> saveAll(Iterable<S> entities) {
        return Flux.fromIterable(entities)
                .flatMap(this::save);
    }

    @Override
    public <S extends CachedProxy> Flux<S> saveAll(Publisher<S> entityStream) {
        return Flux.from(entityStream)
                .flatMap(this::save);
    }

    @Override
    public Mono<CachedProxy> findById(String s) {
        return null;
    }

    @Override
    public Mono<CachedProxy> findById(Publisher<String> id) {
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
    public Flux<CachedProxy> findAllById(Iterable<String> strings) {
        return null;
    }

    @Override
    public Flux<CachedProxy> findAllById(Publisher<String> idStream) {
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
    public Mono<Void> delete(CachedProxy entity) {
        return null;
    }

    @Override
    public Mono<Void> deleteAllById(Iterable<? extends String> strings) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll(Iterable<? extends CachedProxy> entities) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll(Publisher<? extends CachedProxy> entityStream) {
        return null;
    }

    @Override
    public Mono<Void> deleteAll() {
        return null;
    }

    private String getRedisHashKey(CachedProxy proxy) {
        return proxy.getHost() + ":" + proxy.getPort();

    }
}
