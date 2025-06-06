package dev.crashteam.styx.service.web;

import dev.crashteam.styx.exception.HeadersParseException;
import dev.crashteam.styx.exception.NoContentTypeHeaderException;
import dev.crashteam.styx.exception.NonValidHttpMethodException;
import dev.crashteam.styx.model.ContextKey;
import dev.crashteam.styx.model.content.BaseResolver;
import dev.crashteam.styx.model.proxy.ProxyInstance;
import dev.crashteam.styx.model.web.ProxyRequestParams;
import dev.crashteam.styx.util.AdvancedProxyUtils;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebClientService {

    @Value("${app.proxy.timeout}")
    private int proxyConnectionTimeout;
    @Value("${app.timeout-handler}")
    private int handlerTimeout;

    private final List<BaseResolver> resolvers;

    private final int BUFFER_SIZE = 5 * 1024 * 1024;

    public WebClient.RequestHeadersSpec<?> getProxiedWebclientWithHttpMethod(ProxyRequestParams params, ProxyInstance proxy) {
        HttpMethod method = HttpMethod.valueOf(params.getHttpMethod());
        if (method == null) throw new NonValidHttpMethodException("No such http method - " + params.getHttpMethod());
        List<ProxyRequestParams.ContextValue> context = params.getContext();
        WebClient.RequestBodyUriSpec client = WebClient.builder()
                .exchangeStrategies(getExchangeStrategies())
                .defaultHeaders(getHeadersConsumer(getHeaders(proxy, context)))
                .baseUrl(params.getUrl())
                //.filter(logRequest())
                .clientConnector(getProxiedConnector(proxy))
                .build()
                .method(method);
        if (AdvancedProxyUtils.contextKeyExists(context, ContextKey.CONTENT)) {
            return getWebclientWithBody(client, context);
        } else {
            return client;
        }
    }

    private static ExchangeFilterFunction logRequest() {
        return ExchangeFilterFunction.ofRequestProcessor(clientRequest -> {
            log.info("Request: {} {}", clientRequest.method(), clientRequest.url());
            clientRequest.headers().forEach((name, values) -> values.forEach(value -> log.info("{}={}", name, value)));
            return Mono.just(clientRequest);
        });
    }

    public WebClient.RequestHeadersSpec<?> getWebclientWithHttpMethod(ProxyRequestParams params) {
        HttpMethod method = HttpMethod.valueOf(params.getHttpMethod());
        if (method == null) throw new NonValidHttpMethodException("No such http method - " + params.getHttpMethod());
        List<ProxyRequestParams.ContextValue> context = params.getContext();
        WebClient.RequestBodyUriSpec client = WebClient.builder()
                .exchangeStrategies(getExchangeStrategies())
                .defaultHeaders(getHeadersConsumer(getHeaders(context)))
                .baseUrl(params.getUrl())
                .build()
                .method(method);
        if (AdvancedProxyUtils.contextKeyExists(context, ContextKey.CONTENT)) {
            return getWebclientWithBody(client, context);
        } else {
            return client;
        }

    }

    public WebClient getProxiedWebClient(String url, ProxyInstance proxy, Map<String, String> headers) {
        return WebClient.builder()
                .exchangeStrategies(getExchangeStrategies())
                .defaultHeaders(getHeadersConsumerWithPattern(headers))
                .baseUrl(url)
                .clientConnector(getProxiedConnector(proxy))
                .build();
    }

    public WebClient getWebClient(String url, Map<String, String> headers) {
        return WebClient.builder()
                .exchangeStrategies(getExchangeStrategies())
                .defaultHeaders(getHeadersConsumerWithPattern(headers))
                .baseUrl(url)
                .clientConnector(getConnector())
                .build();
    }

    private ReactorClientHttpConnector getConnector() {
//        ConnectionProvider connectionProvider = ConnectionProvider.builder("withMaxIdleTime")
//                .evictInBackground(Duration.ofSeconds(handlerTimeout))
//                .maxIdleTime(Duration.ofSeconds(handlerTimeout - 1)).build();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.SO_KEEPALIVE, true)
                .followRedirect(true)
                .protocol(HttpProtocol.H2, HttpProtocol.HTTP11)
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(handlerTimeout))
                        .addHandlerLast(new WriteTimeoutHandler(handlerTimeout)))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 20000);
        return new ReactorClientHttpConnector(httpClient);
    }

    private ReactorClientHttpConnector getProxiedConnector(ProxyInstance proxy) {
//        ConnectionProvider connectionProvider = ConnectionProvider.builder("withMaxIdleTime")
//                .evictInBackground(Duration.ofSeconds(handlerTimeout))
//                .maxIdleTime(Duration.ofSeconds(handlerTimeout - 1)).build();
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.SO_KEEPALIVE, true)
                .followRedirect(true)
                .protocol(HttpProtocol.H2, HttpProtocol.HTTP11)
                .responseTimeout(Duration.ofMillis(20000))
                .doOnConnected(conn -> conn
                        .addHandlerLast(new ReadTimeoutHandler(handlerTimeout))
                        .addHandlerLast(new WriteTimeoutHandler(handlerTimeout)))
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 20000)
                .proxy(p ->
                        p.type(ProxyProvider.Proxy.HTTP)
                                .host(proxy.getHost())
                                .port(Integer.parseInt(proxy.getPort()))
                                .username(proxy.getUser())
                                .password(f -> proxy.getPassword())
                                .connectTimeoutMillis(proxyConnectionTimeout));
        return new ReactorClientHttpConnector(httpClient);
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> getHeaders(List<ProxyRequestParams.ContextValue> context) {
        if (CollectionUtils.isEmpty(context)) {
            return Collections.emptyMap();
        }
        Map<String, String> headers = new HashMap<>();
        var optValue = context.stream()
                .filter(it -> it.getKey()
                        .equalsIgnoreCase(ContextKey.HEADERS.getValue()))
                .map(ProxyRequestParams.ContextValue::getValue)
                .findFirst();
        if (optValue.isPresent()) {
            try {
                Map<String, String> map = (Map<String, String>) optValue.get();
                headers.putAll(map);
            } catch (Exception e) {
                throw new HeadersParseException(e.getMessage(), e.getCause());
            }
        }
        applyHeaderFunctions(headers);
        return headers;
    }

    public Map<String, String> getHeaders(ProxyInstance proxy, List<ProxyRequestParams.ContextValue> context) {
        Map<String, String> headers = getHeaders(context);
        if (StringUtils.hasText(proxy.getUserAgent())) {
            headers.put("User-Agent", proxy.getUserAgent());
        }
        return headers;
    }

    private void applyHeaderFunctions(Map<String, String> headers) {
        headers.forEach((k, v) -> {
            if (v.equals("random_uuid()")) {
                headers.put(k, UUID.randomUUID().toString());
            }
        });
    }

    private String getContentType(List<ProxyRequestParams.ContextValue> context) {
        return (getHeaders(context).get("Content-Type"));
    }

    private WebClient.RequestHeadersSpec<?> getWebclientWithBody(WebClient.RequestBodyUriSpec webclient, List<ProxyRequestParams.ContextValue> context) {
        String contentType = getContentType(context);
        if (!StringUtils.hasText(contentType))
            throw new NoContentTypeHeaderException("Specify header for content");
        Optional<Object> optional = context.stream()
                .filter(it -> it.getKey().equalsIgnoreCase(ContextKey.CONTENT.getValue()))
                .map(ProxyRequestParams.ContextValue::getValue)
                .findFirst();
        if (optional.isPresent()) {
            Object value = AdvancedProxyUtils.getObjectValueByContentType(resolvers, optional.get(), contentType);
            return webclient.body(Mono.just(value), String.class);
        }
        return webclient;
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

    private ExchangeStrategies getExchangeStrategies() {
        return ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(BUFFER_SIZE))
                .build();

    }
}
