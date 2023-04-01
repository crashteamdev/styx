package dev.crashteam.styx.service.forbidden;

import dev.crashteam.styx.model.forbidden_proxy.ForbiddenProxy;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.repository.forbidden.ForbiddenProxyRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ForbiddenProxyService {

    @Value("${application.forbidden-expire}")
    private Long expireMinutes;

    private final ForbiddenProxyRepository forbiddenProxyRepository;

    @SneakyThrows
    public void save(String rootUrl, ProxyInstance proxyInstance) {
        long expireTime = LocalDateTime.now().plusMinutes(expireMinutes).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        log.warn("Put forbidden url - [{}] for proxy - [{}:{}], estimated time to be deleted from forbidden - {}",
                rootUrl, proxyInstance.getHost(), proxyInstance.getPort(), expireTime);
        ForbiddenProxy forbiddenProxy = new ForbiddenProxy();
        forbiddenProxy.setExpireTime(expireTime);
        forbiddenProxyRepository.save(rootUrl, proxyInstance, forbiddenProxy).subscribe();
    }

    public void deleteByHashKey(String hashKey) {
        log.info("Deleting forbidden proxy with hash key - {}", hashKey);
        forbiddenProxyRepository.deleteByHashKey(hashKey).subscribe();
    }

    public Mono<Boolean> notExistsByKey(ProxyInstance proxyInstance, String url) {
        return forbiddenProxyRepository.notExistsByKey(proxyInstance, url);
    }

    public Flux<ForbiddenProxy> findAll() {
        return forbiddenProxyRepository.findAll();
    }

    public Flux<Map.Entry<String, ForbiddenProxy>> findAllWithKeys() {
        return forbiddenProxyRepository.findAllWithHashKey();
    }


}
