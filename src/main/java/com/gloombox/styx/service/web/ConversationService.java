package com.gloombox.styx.service.web;

import com.gloombox.styx.exception.FailedProxyRequestException;
import com.gloombox.styx.model.proxy.CachedProxy;
import com.gloombox.styx.model.web.ProxiedResponse;
import com.gloombox.styx.service.proxy.CachedProxyService;
import io.netty.channel.ChannelOption;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final CachedProxyService proxyService;

    public Mono<ProxiedResponse> getProxiedResponse(String url, Map<String, String> headers) {
        return getRandomProxy()
                .flatMap(proxy -> getWebClientResponse(url, proxy, headers))
                .map(response -> {
                    ProxiedResponse proxiedResponse = new ProxiedResponse();
                    proxiedResponse.setBody(response.getBody());
                    proxiedResponse.setUrl(url);
                    proxiedResponse.setOriginalStatus(response.getStatusCodeValue());
                    return proxiedResponse;
                });
    }

    private Mono<ResponseEntity<String>> getWebClientResponse(String url, CachedProxy proxy, Map<String, String> headers) {
        return getWebClient(url, proxy).get()
                .headers(httpHeaders -> headers.forEach(httpHeaders::add))
                .retrieve()
                .onStatus(HttpStatus::is5xxServerError,
                        response -> Mono.error(new FailedProxyRequestException("Proxy request error", response.rawStatusCode())))
                .toEntity(String.class)
                .retryWhen(Retry.backoff(2, Duration.ofSeconds(1))
                        .filter(throwable -> throwable instanceof FailedProxyRequestException));

    }

    private WebClient getWebClient(String url, CachedProxy proxy) {
        return WebClient.builder()
                .baseUrl(url)
                .clientConnector(getConnector(proxy))
                .build();
    }

    private ReactorClientHttpConnector getConnector(CachedProxy proxy) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 3000)
                .proxy(p ->
                        p.type(ProxyProvider.Proxy.HTTP)
                                .host(proxy.getHost())
                                .port(Integer.parseInt(proxy.getPort()))
                                .username(proxy.getUser())
                                .password(f -> proxy.getPassword()));
        return new ReactorClientHttpConnector(httpClient);

    }

    private Mono<CachedProxy> getRandomProxy() {
        Random random = new Random();

        return proxyService.getActive()
                .count()
                .map(random::nextLong)
                .flatMap(index -> proxyService.getActive().elementAt(Math.toIntExact(index)));
    }
}
