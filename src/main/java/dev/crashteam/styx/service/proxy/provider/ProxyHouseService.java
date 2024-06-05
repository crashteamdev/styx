package dev.crashteam.styx.service.proxy.provider;

import dev.crashteam.styx.model.proxy.ProxyHouseResponse;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.proxy.ProxySource;
import dev.crashteam.styx.util.RandomUserAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ProxyHouseService implements ProxyProvider {

    @Value("${integration.proxy-house.url}")
    private String proxyUrl;
    @Value("${integration.proxy-house.api-key}")
    private String apiKey;

    @Override
    public Flux<ProxyInstance> getProxy() {
        return this.getProxyFromSource()
                .map(it -> it.getData().getProxies())
                .map(Map::values)
                .flatMap(Flux::fromIterable)
                .filter(it -> it.getActive() != 0)
                .map(p -> {
                    ProxyInstance proxyInstance = new ProxyInstance();
                    proxyInstance.setHost(p.getIp());
                    proxyInstance.setPort(String.valueOf(p.getHttp_port()));
                    proxyInstance.setActive(true);
                    proxyInstance.setProxySource(ProxySource.PROXY_HOUSE);
                    proxyInstance.setUser(p.getLogin());
                    proxyInstance.setBadUrls(new CopyOnWriteArrayList<>());
                    proxyInstance.setPassword(p.getPassword());
                    proxyInstance.setCountryCode(null);
                    proxyInstance.setProxyKey(null);
                    proxyInstance.setUserAgent(RandomUserAgent.getRandomUserAgent());
                    return proxyInstance;
                });
    }

    public Flux<ProxyHouseResponse> getProxyFromSource() {
        WebClient webClient = WebClient.builder()
                .baseUrl(proxyUrl + "proxy/list?limit=1000")
                .defaultHeaders(httpHeaders -> httpHeaders.add("Auth-Token", apiKey))
                .build();
        return webClient.get()
                .retrieve()
                .bodyToFlux(ProxyHouseResponse.class)
                .doOnError(throwable -> log.error("Exception caught on webclient process with cause:", throwable))
                .onErrorResume(throwable -> throwable instanceof Exception, e -> Flux.empty());

    }
}
