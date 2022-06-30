package dev.crashteam.styx.service.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.crashteam.styx.exception.NoContentTypeHeaderException;
import dev.crashteam.styx.exception.NonValidHttpMethodException;
import dev.crashteam.styx.model.ContextKey;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import dev.crashteam.styx.util.AdvancedProxyUtils;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class WebClientService {

    @Value("${app.proxy.timeout}")
    private int proxyConnectionTimeout;
    @Value("${app.timeout-handler}")
    private int handlerTimeout;

    private final ObjectMapper objectMapper;

    private final int BUFFER_SIZE = 2 * 1024 * 1024;

    public WebClient.RequestHeadersSpec<?> getProxiedWebclientWithHttpMethod(ProxyRequestParams params, ProxyInstance proxy, Map<String, String> headers) {
        HttpMethod method = HttpMethod.resolve(params.getHttpMethod());
        if (method == null) throw new NonValidHttpMethodException("No such http method - " + params.getHttpMethod());
        if (!AdvancedProxyUtils.contextKeyExists(params.getContext(), ContextKey.CONTENT)) {
            return WebClient.builder()
                    .exchangeStrategies(getMaxBufferSize())
                    .defaultHeaders(getHeadersConsumerWithPattern(headers))
                    .baseUrl(params.getUrl())
                    .clientConnector(getProxiedConnector(proxy))
                    .build()
                    .method(method);
        } else {
            return WebClient.builder()
                    .exchangeStrategies(getMaxBufferSize())
                    .defaultHeaders(getHeadersConsumerWithPattern(headers))
                    .baseUrl(params.getUrl())
                    .clientConnector(getProxiedConnector(proxy))
                    .build()
                    .method(method)
                    .body(Mono.just(getBody(params.getContext()))
                            .flatMap(it -> getObjFromJson((String) it)), Object.class);
        }
    }

    public WebClient.RequestHeadersSpec<?> getWebclientWithHttpMethod(ProxyRequestParams params, Map<String, String> headers) {
        HttpMethod method = HttpMethod.resolve(params.getHttpMethod());
        if (method == null) throw new NonValidHttpMethodException("No such http method - " + params.getHttpMethod());
        if (!AdvancedProxyUtils.contextKeyExists(params.getContext(), ContextKey.CONTENT)) {
            return WebClient.builder()
                    .exchangeStrategies(getMaxBufferSize())
                    .defaultHeaders(getHeadersConsumerWithPattern(headers))
                    .baseUrl(params.getUrl())
                    .build()
                    .method(method);
        } else {
            return WebClient.builder()
                    .exchangeStrategies(getMaxBufferSize())
                    .defaultHeaders(getHeadersConsumerWithPattern(headers))
                    .baseUrl(params.getUrl())
                    .build()
                    .method(method)
                    .body(Mono.just(getBody(params.getContext()))
                            .flatMap(it -> getObjFromJson((String) it)), Object.class);
        }

    }

    private Mono<Object> getObjFromJson(String json) {
        return Mono.fromCallable(() -> objectMapper.readValue(json, Object.class));
    }

    public WebClient getProxiedWebClient(String url, ProxyInstance proxy, Map<String, String> headers) {
        return WebClient.builder()
                .exchangeStrategies(getMaxBufferSize())
                .defaultHeaders(getHeadersConsumerWithPattern(headers))
                .baseUrl(url)
                .clientConnector(getProxiedConnector(proxy))
                .build();
    }

    public WebClient getWebClient(String url, Map<String, String> headers) {
        return WebClient.builder()
                .exchangeStrategies(getMaxBufferSize())
                .defaultHeaders(getHeadersConsumerWithPattern(headers))
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

    public Map<String, String> getHeaders(List<ProxyRequestParams.ContextValue> context) {
        Map<String, String> headers = new HashMap<>();
        var optValue = context.stream()
                .filter(it -> it.getKey()
                        .equalsIgnoreCase(ContextKey.HEADERS.getValue()))
                .map(ProxyRequestParams.ContextValue::getValue)
                .findFirst();
        optValue.ifPresent(value -> {
            Mono.fromCallable(() -> {
                        final String json = objectMapper.writeValueAsString(value);
                        final Map map = objectMapper.readValue(json, Map.class);
                        headers.putAll(map);
                        return map;
                    }
            ).subscribe();
        });
        return headers;
    }

    private String getContentType(List<ProxyRequestParams.ContextValue> context) {
        return (getHeaders(context).get("Content-Type"));
    }

    private Object getBody(List<ProxyRequestParams.ContextValue> context) {
        String contentType = getContentType(context);
        if (!StringUtils.hasText(contentType))
            throw new NoContentTypeHeaderException("Specify header for content");
        var valueOptional = context.stream()
                .filter(it -> it.getKey().equalsIgnoreCase(ContextKey.CONTENT.getValue()))
                .map(ProxyRequestParams.ContextValue::getValue)
                .map(value -> switch (getContentType(context)) {
                    case MediaType.APPLICATION_JSON_VALUE -> base64toJsonString((String) value);
                    default -> value;
                })
                .findFirst();
        return valueOptional.orElse("");
    }

    private String base64toJsonString(String value) {
        return new String(Base64.getDecoder().decode(value));
    }

    private Consumer<HttpHeaders> getHeadersConsumer(Map<String, String> headers) {
        return httpHeaders -> headers.forEach(httpHeaders::add);
    }

    private Consumer<HttpHeaders> getHeadersConsumerWithPattern(Map<String, String> headers) {
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
