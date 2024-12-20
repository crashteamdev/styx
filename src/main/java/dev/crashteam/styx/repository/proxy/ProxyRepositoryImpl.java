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
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Repository
@SuppressWarnings("unused")
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

    public Flux<ProxyInstance> getRandomProxyNotIncludeForbiddenAndMobile(String rootUrl) {
        return hashOperations
                .values(RedisKey.PROXY_KEY.getValue())
                .filter(it -> !it.getProxySource().equals(ProxySource.MOBILE_PROXY))
                .filterWhen(proxy -> forbiddenProxyRepository.notExistsByKey(proxy, rootUrl));
    }

    public Mono<ProxyInstance> getProxyByContextId(String contextId) {
        return hashOperations
                .values(RedisKey.PROXY_KEY.getValue())
                .filter(it -> !it.getProxySource().equals(ProxySource.MOBILE_PROXY))
                .filter(it -> it.getUserContext() != null
                        && it.getUserContext().stream().anyMatch(context -> context.getContextId().equals(contextId)))
                .next();
    }

    public Mono<ProxyInstance> getAndSetProxyByContextId(String contextId, String appId) {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue())
                .filter(it -> !it.getProxySource().equals(ProxySource.MOBILE_PROXY))
                .filter(it -> it.getUserContext() != null && it.getUserContext().stream().noneMatch(context -> appId.equals(context.getAppId())))
                .next()
                .hasElement()
                .flatMap(hasElement -> {
                    if (hasElement) {
                        return hashOperations.values(RedisKey.PROXY_KEY.getValue())
                                .filter(it -> it.getUserContext() != null && it.getUserContext().stream().noneMatch(context -> appId.equals(context.getAppId())))
                                .next()
                                .doOnNext(it -> {
                                    saveProxyContext(it, contextId, appId);
                                });
                    }
                    // Считаем что все элементы уже имеют данный AppId, берем случайный
                    return hashOperations.randomEntry(RedisKey.PROXY_KEY.getValue())
                            .map(Map.Entry::getValue)
                            .doOnNext(it -> {
                                saveProxyContext(it, contextId, appId);
                            });
                });
    }

    private void saveProxyContext(ProxyInstance proxyInstance, String contextId, String appId) {
        List<ProxyInstance.UserContext> contextIds = proxyInstance.getUserContext();
        if (!CollectionUtils.isEmpty(contextIds)) {
            List<ProxyInstance.UserContext> userContexts = contextIds.stream()
                    .filter(it -> appId.equals(it.getAppId())).toList();
            if (!CollectionUtils.isEmpty(userContexts)) {
                log.info("Deleting all existing entries for appId - {}", appId);
                proxyInstance.getUserContext().removeAll(userContexts);
            }
            log.info("Add context for proxy - {}:{}", proxyInstance.getHost(), proxyInstance.getPort());
            ProxyInstance.UserContext userContext = new ProxyInstance.UserContext();
            userContext.setContextId(contextId);
            userContext.setInUse(true);
            userContext.setCreatedTime(System.currentTimeMillis());
            userContext.setAppId(appId);
            contextIds.add(userContext);
        } else {
            log.info("Created new context for proxy - {}:{}", proxyInstance.getHost(), proxyInstance.getPort());
            List<ProxyInstance.UserContext> context = new ArrayList<>();
            ProxyInstance.UserContext userContext = new ProxyInstance.UserContext();
            userContext.setContextId(contextId);
            userContext.setInUse(true);
            userContext.setCreatedTime(System.currentTimeMillis());
            userContext.setAppId(appId);
            context.add(userContext);
            proxyInstance.setUserContext(context);
        }
        saveExisting(proxyInstance).subscribe();
    }

    public Flux<ProxyInstance> getRandomProxyNotIncludeForbidden(String rootUrl) {
        return hashOperations
                .values(RedisKey.PROXY_KEY.getValue())
                .filterWhen(proxy -> forbiddenProxyRepository.notExistsByKey(proxy, rootUrl));
    }

    public Flux<ProxyInstance> getMobileProxies() {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue())
                .filter(it -> ProxySource.MOBILE_PROXY.equals(it.getProxySource()) && it.getBadProxyPoint() < 10);
    }

    public Flux<ProxyInstance> getBadMobileProxies() {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue())
                .filter(it -> ProxySource.MOBILE_PROXY.equals(it.getProxySource()) && it.getBadProxyPoint() >= 10);
    }

    public Mono<ProxyInstance> getMobileProxyByKey(String proxyKey) {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue())
                .filter(it -> ProxySource.MOBILE_PROXY.equals(it.getProxySource()))
                .filter(it -> proxyKey.equals(it.getProxyKey()))
                .next();
    }

    public Mono<ProxyInstance> getMobileProxyBySystem(String system) {
        return hashOperations.values(RedisKey.PROXY_KEY.getValue())
                .filter(it -> ProxySource.MOBILE_PROXY.equals(it.getProxySource()))
                .filter(it -> system.equals(it.getSystem()))
                .next();
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
