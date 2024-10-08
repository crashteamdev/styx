package dev.crashteam.styx.service.proxy.provider;

import dev.crashteam.styx.model.proxy.*;
import dev.crashteam.styx.service.proxy.CachedProxyService;
import dev.crashteam.styx.util.RandomUserAgent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class MobileProxyService implements ProxyProvider {

    @Value("${integration.mobile-proxy.url}")
    private String proxyUrl;
    @Value("${integration.mobile-proxy.api-key}")
    private String apiKey;
    private final CachedProxyService proxyService;


    public Flux<ProxyInstance> getProxy() {
        return getProxyFromSource()
                .map(it -> Arrays.stream(it).toList())
                .flatMapMany(Flux::fromIterable)
                .map(p -> {
                    ProxyInstance proxyInstance = new ProxyInstance();
                    proxyInstance.setHost(p.getHost());
                    proxyInstance.setPort(p.getPort());
                    proxyInstance.setActive(true);
                    proxyInstance.setProxySource(ProxySource.MOBILE_PROXY);
                    proxyInstance.setUser(p.getLogin());
                    proxyInstance.setBadUrls(new CopyOnWriteArrayList<>());
                    proxyInstance.setPassword(p.getPassword());
                    proxyInstance.setProxyKey(p.getProxyKey());
                    proxyInstance.setCountryCode("RU");
                    proxyInstance.setUserAgent(RandomUserAgent.getRandomUserAgent());
                    if (p.getHost().equals("mproxy.site")) {
                        proxyInstance.setSystem("KE");
                    } else {
                        proxyInstance.setSystem("UZUM");
                    }
                    return proxyInstance;
                });
    }

    public Mono<MobileProxyResponse[]> getProxyFromSource() {
        WebClient webClient = WebClient.builder()
                .baseUrl(proxyUrl + "?command=get_my_proxy")
                .defaultHeaders(httpHeaders -> httpHeaders.add("Authorization", "Bearer " + apiKey))
                .build();
        return webClient.get()
                .retrieve()
                .bodyToMono(MobileProxyResponse[].class)
                .doOnError(throwable -> log.error("Exception caught on webclient process with cause:", throwable))
                .onErrorResume(throwable -> throwable instanceof Exception, e -> Mono.empty());

    }

    public void changeIp() {
        proxyService.getMobileProxies()
                .flatMap(it -> {
                    WebClient webClient = WebClient.builder()
                            .baseUrl("https://changeip.mobileproxy.space/?proxy_key=%s&format=json".formatted(it.getProxyKey()))
                            .defaultHeaders(httpHeaders -> httpHeaders.add("Authorization", "Bearer " + apiKey))
                            .build();
                    return webClient.get().retrieve().toBodilessEntity();
                }).doOnError(e -> log.error("Error while changing mobile proxies ip")).subscribe();
    }

    public void changeIp(ProxyInstance proxy) {
        log.info("Changing ip of proxy - {}", proxy.getHost());
        proxyService.getMobileProxyByKey(proxy.getProxyKey())
                .filter(it -> it.getProxyKey().equals(proxy.getProxyKey()))
                .flatMap(it -> {
                    WebClient webClient = WebClient.builder()
                            .baseUrl("https://changeip.mobileproxy.space/?proxy_key=%s&format=json".formatted(it.getProxyKey()))
                            .defaultHeaders(httpHeaders -> httpHeaders.add("Authorization", "Bearer " + apiKey))
                            .build();
                    return webClient
                            .get()
                            .retrieve()
                            .bodyToMono(MobileProxyChangeIpResponse.class);

                })
                .flatMap(it -> proxyService.getMobileProxyByKey(proxy.getProxyKey()))
                .doOnNext(it -> {
                    log.info("Ip of proxy {} changed", proxy.getHost());
                    it.setBadProxyPoint(0);
                    proxyService.saveExisting(it);
                })
                .doOnError(e -> log.error("Error while changing mobile proxies ip"))
                .subscribe();
    }

    public void reloadProxy(ProxyInstance proxy) {
        log.info("Reloading mobile proxy - {}", proxy.getHost());
        proxyService.getMobileProxyByKey(proxy.getProxyKey())
                .filter(it -> it.getProxyKey().equals(proxy.getProxyKey()))
                .flatMap(it -> {
                    WebClient webClient = WebClient.builder()
                            .baseUrl("https://mobileproxy.space/api.html?command=reboot_proxy&proxy_id=%s".formatted(it.getProxyKey()))
                            .defaultHeaders(httpHeaders -> httpHeaders.add("Authorization", "Bearer " + apiKey))
                            .build();
                    return webClient
                            .get()
                            .retrieve()
                            .bodyToMono(MobileProxyReloadResponse.class);

                })
                .doOnNext(it -> log.info("Reloaded proxy with status - {}", it.getStatus()))
                .doOnError(e -> log.error("Error while reloading mobile proxies ip", e))
                .subscribe();
    }

}
