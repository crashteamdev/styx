package dev.crashteam.styx.service.proxy.provider;

import dev.crashteam.styx.model.proxy.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class ProxyIoService implements ProxyProvider {

    @Value("${integration.proxys-io.url}")
    private String proxyUrl;
    @Value("${integration.proxys-io.api-key}")
    private String apiKey;

    @Override
    public Flux<ProxyInstance> getProxy() {
        return this.getProxyFromSource()
                .map(ProxyIoResponse::getData)
                .flatMap(Flux::fromIterable)
                .map(p -> {
                    List<ProxyInstance> proxyInstances = new ArrayList<>();
                    for (ProxyIoResponse.ProxyIo proxy : p.getProxies()) {
                        ProxyInstance proxyInstance = new ProxyInstance();
                        proxyInstance.setHost(proxy.getIp());
                        proxyInstance.setPort(proxy.getPortHttp());
                        proxyInstance.setActive(true);
                        proxyInstance.setProxySource(ProxySource.PROXYS_IO);
                        proxyInstance.setUser(p.getUsername());
                        proxyInstance.setBadUrls(new CopyOnWriteArrayList<>());
                        proxyInstance.setPassword(p.getPassword());
                        proxyInstance.setCountryCode(p.getCountryCode());
                        proxyInstance.setProxyKey(null);
                        proxyInstances.add(proxyInstance);
                    }
                    return proxyInstances;
                })
                .flatMap(Flux::fromIterable);
    }

    public Flux<ProxyIoResponse> getProxyFromSource() {
        WebClient webClient = WebClient.builder()
                .baseUrl(proxyUrl + "/ip?key=" + apiKey)
                .build();
        return webClient.get()
                .retrieve()
                .bodyToFlux(ProxyIoResponse.class)
                .doOnError(throwable -> log.error("Exception caught on webclient process with cause:", throwable))
                .onErrorResume(throwable -> throwable instanceof Exception, e -> Flux.empty());

    }


}
