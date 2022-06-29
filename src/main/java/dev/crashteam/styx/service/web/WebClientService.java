package dev.crashteam.styx.service.web;

import dev.crashteam.styx.exception.NonValidHttpMethodException;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.util.Map;
import java.util.function.Consumer;

@Service
public class WebClientService {

    @Value("${app.proxy.timeout}")
    private int proxyConnectionTimeout;
    @Value("${app.timeout-handler}")
    private int handlerTimeout;

    private final int BUFFER_SIZE = 2 * 1024 * 1024;

    public WebClient.RequestHeadersSpec<?> getProxiedWebclientWithHttpMethod(ProxyRequestParams params, ProxyInstance proxy, Map<String, String> headers) {
        HttpMethod method = HttpMethod.resolve(params.getHttpMethod());
        if (method == null) throw new NonValidHttpMethodException(params.getHttpMethod());
        if (params.getBody() == null) {
            return WebClient.builder()
                    .exchangeStrategies(getMaxBufferSize())
                    .defaultHeaders(getHeadersConsumer(headers))
                    .baseUrl(params.getUrl())
                    .clientConnector(getProxiedConnector(proxy))
                    .build()
                    .method(method);
        } else {
            return WebClient.builder()
                    .exchangeStrategies(getMaxBufferSize())
                    .defaultHeaders(getHeadersConsumer(headers))
                    .baseUrl(params.getUrl())
                    .clientConnector(getProxiedConnector(proxy))
                    .build()
                    .method(method)
                    .body(Mono.just(params.getBody()), Object.class);
        }

    }

    public WebClient.RequestHeadersSpec<?> getWebclientWithHttpMethod(ProxyRequestParams params, Map<String, String> headers) {
        HttpMethod method = HttpMethod.resolve(params.getHttpMethod());
        if (method == null) throw new NonValidHttpMethodException(params.getHttpMethod());
        if (params.getBody() == null) {
            return WebClient.builder()
                    .exchangeStrategies(getMaxBufferSize())
                    .defaultHeaders(getHeadersConsumer(headers))
                    .baseUrl(params.getUrl())
                    .build()
                    .method(method);
        } else {
            return WebClient.builder()
                    .exchangeStrategies(getMaxBufferSize())
                    .defaultHeaders(getHeadersConsumer(headers))
                    .baseUrl(params.getUrl())
                    .build()
                    .method(method)
                    .body(Mono.just(params.getBody()), Object.class);
        }

    }

    public WebClient getProxiedWebClient(String url, ProxyInstance proxy, Map<String, String> headers) {
        return WebClient.builder()
                .exchangeStrategies(getMaxBufferSize())
                .defaultHeaders(getHeadersConsumer(headers))
                .baseUrl(url)
                .clientConnector(getProxiedConnector(proxy))
                .build();
    }

    public WebClient getWebClient(String url, Map<String, String> headers) {
        return WebClient.builder()
                .exchangeStrategies(getMaxBufferSize())
                .defaultHeaders(getHeadersConsumer(headers))
                .baseUrl(url)
                .clientConnector(getConnector())
                .build();
    }

    private ReactorClientHttpConnector getConnector() {
        HttpClient httpClient = HttpClient.create()
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(handlerTimeout))
                        .addHandlerLast(new WriteTimeoutHandler(handlerTimeout)))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, proxyConnectionTimeout);
        return new ReactorClientHttpConnector(httpClient);
    }

    private ReactorClientHttpConnector getProxiedConnector(ProxyInstance proxy) {
        HttpClient httpClient = HttpClient.create()
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(handlerTimeout))
                        .addHandlerLast(new WriteTimeoutHandler(handlerTimeout)))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, proxyConnectionTimeout)
                .proxy(p ->
                        p.type(ProxyProvider.Proxy.HTTP)
                                .host(proxy.getHost())
                                .port(Integer.parseInt(proxy.getPort()))
                                .username(proxy.getUser())
                                .password(f -> proxy.getPassword()));
        return new ReactorClientHttpConnector(httpClient);
    }

    private Consumer<HttpHeaders> getHeadersConsumer(Map<String, String> headers) {
        return httpHeaders -> headers.forEach((k, v) -> {
            if (k.startsWith("X-")) {
                httpHeaders.add(k.substring(2), v);
            }
        });
    }

    private ExchangeStrategies getMaxBufferSize() {
        return ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(BUFFER_SIZE))
                .build();

    }
}
