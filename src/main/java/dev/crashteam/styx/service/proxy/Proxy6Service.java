package dev.crashteam.styx.service.proxy;


import dev.crashteam.styx.model.proxy.Proxy6Dto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class Proxy6Service implements ProxyProvider {

    @Value("${app.proxy.url}")
    private String proxyUrl;

    @Value("${app.proxy.api-key}")
    private String apiKey;

    @Override
    public Flux<Proxy6Dto> getProxy() {
        WebClient webClient = WebClient.builder()
                .baseUrl(proxyUrl + apiKey + "/getproxy")
                .build();
        return webClient.get()
                .retrieve()
                .bodyToFlux(Proxy6Dto.class)
                .doOnError(throwable -> log.error("Exception caught on webclient process with cause:", throwable));

    }
}
