package dev.crashteam.styx.repository.proxy;

import dev.crashteam.styx.model.RedisKey;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.proxy.ProxySource;
import dev.crashteam.styx.repository.forbidden.ForbiddenProxyRepository;
import lombok.extern.slf4j.Slf4j;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@Repository
public class ProxyRepositoryImpl implements ProxyRepository {

    private final ReactiveRedisOperations<String, ProxyInstance> redisOperations;
    private final ReactiveHashOperations<String, String, ProxyInstance> hashOperations;
    private final ForbiddenProxyRepository forbiddenProxyRepository;

    @Autowired
    public ProxyRepositoryImpl(ReactiveRedisOperations<String, ProxyInstance> redisOperations, ForbiddenProxyRepository forbiddenProxyRepository) {
        this.redisOperations = redisOperations;
        this.hashOperations = redisOperations.opsForHash();
        this.forbiddenProxyRepository = forbiddenProxyRepository;
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
        return hashOperations.putIfAbsent(RedisKey.PROXY_KEY.getValue(), getRedisHashKey(entity), entity)
                .thenReturn(entity);
    }

    public <S extends ProxyInstance> Mono<S> saveExisting(S entity) {
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

    public Mono<ProxyInstance> getRandomProxy() {
        return hashOperations
                .randomEntry(RedisKey.PROXY_KEY.getValue())
                .map(Map.Entry::getValue);
    }

    public Mono<ProxyInstance> getRandomProxyNotIncludeForbidden(String rootUrl, int retry) {
        if (retry <= 0) {
            return Mono.empty();
        }
        retry--;
        return hashOperations
                .randomEntry(RedisKey.PROXY_KEY.getValue())
                .map(Map.Entry::getValue)
                .filter(it -> !ProxySource.MOBILE_PROXY.equals(it.getProxySource()))
                .filterWhen(proxy -> forbiddenProxyRepository.notExistsByKey(proxy, rootUrl))
                .switchIfEmpty(getRandomProxyNotIncludeForbidden(rootUrl, retry));
    }

    public Flux<ProxyInstance> getMobileProxies() {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue())
                .filter(it -> ProxySource.MOBILE_PROXY.equals(it.getProxySource()));
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

    public Mono<Long> deleteByHashKey(ProxyInstance proxy) {
        return hashOperations.remove(RedisKey.PROXY_KEY.getValue(), getRedisHashKey(proxy));
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

    public Mono<Boolean> deleteAllByKey() {
        return hashOperations.delete(RedisKey.PROXY_KEY.getValue());
    }

    private String getRedisHashKey(ProxyInstance proxy) {
        return proxy.getHost() + ":" + proxy.getPort();

    }
}
