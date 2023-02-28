package dev.crashteam.styx.repository.forbidden;

import dev.crashteam.styx.model.RedisKey;
import dev.crashteam.styx.model.forbidden_proxy.ForbiddenProxy;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisOperations;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Repository
public class ForbiddenProxyRepository {

    private final ReactiveRedisOperations<String, ForbiddenProxy> redisOperations;
    private final ReactiveHashOperations<String, String, ForbiddenProxy> hashOperations;

    @Autowired
    public ForbiddenProxyRepository(ReactiveRedisOperations<String, ForbiddenProxy> redisOperations) {
        this.redisOperations = redisOperations;
        this.hashOperations = redisOperations.opsForHash();
    }

    public Mono<ForbiddenProxy> save(String url, ProxyInstance proxy, ForbiddenProxy entity) {
        return hashOperations.put(RedisKey.FORBIDDEN_PROXY.getValue(), getRedisHashKey(proxy, url), entity)
                .thenReturn(entity);
    }

    public Mono<Boolean> notExistsByKey(ProxyInstance proxy, String rootUrl) {
        return hashOperations.hasKey(RedisKey.FORBIDDEN_PROXY.getValue(), getRedisHashKey(proxy, rootUrl)).map(it -> !it);
    }

    public Flux<ForbiddenProxy> findAll() {
        return hashOperations.values(RedisKey.FORBIDDEN_PROXY.getValue());
    }

    public Flux<Map.Entry<String, ForbiddenProxy>> findAllWithHashKey() {
        return hashOperations.entries(RedisKey.FORBIDDEN_PROXY.getValue());
    }

    public Mono<Long> deleteByHashKey(String hashKey) {
        return hashOperations.remove(RedisKey.FORBIDDEN_PROXY.getValue(), hashKey);
    }

    private String getRedisHashKey(ProxyInstance proxy, String rootUrl) {
        return proxy.getHost() + ":" + proxy.getPort() + "-" + rootUrl;
    }

}
