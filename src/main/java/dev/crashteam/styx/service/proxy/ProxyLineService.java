package dev.crashteam.styx.service.proxy;

import dev.crashteam.styx.model.proxy.ProxyLineResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class ProxyLineService implements ProxyProvider {

    @Value("${app.proxy.proxy6.url}")
    private String proxyUrl;

    @Value("${app.proxy.proxy6.api-key}")
    private String apiKey;

    @Override
    public Flux<ProxyLineResponse> getProxy() {
        WebClient webClient = WebClient.builder()
                .baseUrl(proxyUrl + "proxies/?api_key=" + apiKey)
                .build();
        return webClient.get()
                .retrieve()
                .bodyToFlux(ProxyLineResponse.class)
                .doOnError(throwable -> log.error("Exception caught on webclient process with cause:", throwable));

    }
}
