package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.proxy.ProxyLineResponse;
import dev.crashteam.styx.model.proxy.ProxySource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ProxyLineService implements ProxyProvider {

    @Value("${integration.proxy-line.url}")
    private String proxyUrl;

    @Value("${integration.proxy-line.api-key}")
    private String apiKey;

    @Override
    public Flux<ProxyInstance> getProxy() {
        return this.getProxyFromSource()
                .map(ProxyLineResponse::getResults)
                .flatMap(Flux::fromIterable)
                .map((ProxyLineResponse.ProxyLineResult p) -> {
                    ProxyInstance proxyInstance = new ProxyInstance();
                    proxyInstance.setHost(p.getIp());
                    proxyInstance.setPort(p.getPortHttp());
                    proxyInstance.setActive(true);
                    //proxyInstance.setProxySource(ProxySource.PROXY_LINE);
                    proxyInstance.setUser(p.getUser());
                    proxyInstance.setBadUrls(new CopyOnWriteArrayList<>());
                    proxyInstance.setPassword(p.getPassword());
                    return proxyInstance;
                });

    }

    public Flux<ProxyLineResponse> getProxyFromSource() {
        WebClient webClient = WebClient.builder()
                .baseUrl(proxyUrl + "proxies/?api_key=" + apiKey + "&status=active")
                .build();
        return webClient.get()
                .retrieve()
                .bodyToFlux(ProxyLineResponse.class)
                .doOnError(throwable -> log.error("Exception caught on webclient process with cause:", throwable))
                .onErrorResume(throwable -> throwable instanceof Exception, e -> Flux.empty());

    }
}
