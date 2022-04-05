package dev.crashteam.styx.repository.proxy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.crashteam.styx.model.RedisKey;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Deprecated
@RequiredArgsConstructor
public class RedisRepository {

    private final ReactiveRedisTemplate<String, String> proxyReactiveRedisTemplate;
    private final ObjectMapper objectMapper;

    public Mono<Boolean> hasKey(RedisKey key) {
        return proxyReactiveRedisTemplate.hasKey(key.getValue());
    }

    public void saveProxies(List<ProxyInstance> proxy, RedisKey key) {

        proxyListToJson(proxy)
                .subscribe(v -> proxyReactiveRedisTemplate.opsForValue().set(key.getValue(), v));
    }

    public void saveBadProxies(List<ProxyInstance> proxies, RedisKey key) {
        proxyReactiveRedisTemplate.hasKey("")
                .filter(v -> v)
                .flatMap(v -> getMonoListCachedProxyByKey(key))
                .doOnNext(proxies::addAll)
                .flatMap(v -> proxyListToJson(proxies))
                .subscribe(v -> {
                    proxyReactiveRedisTemplate.opsForValue().set(key.getValue(), v);
                });
    }

    public Flux<ProxyInstance> getFLuxCachedProxyByKey(RedisKey key) {
        return proxyReactiveRedisTemplate.opsForValue().get(key.getValue())
                .flatMap(this::getMappedProxyList)
                .flatMapIterable(list -> list);
    }

    public Mono<List<ProxyInstance>> getMonoListCachedProxyByKey(RedisKey key) {
        return proxyReactiveRedisTemplate.opsForValue().get(key.getValue())
                .flatMap(this::getMappedProxyList);
    }

    private Mono<String> proxyListToJson(List<ProxyInstance> proxy) {
        return Mono.fromCallable(() ->
                objectMapper.writeValueAsString(proxy)
        );
    }

    private Mono<List<ProxyInstance>> getMappedProxyList(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(
                json, new TypeReference<>() {
                }));
    }
}
