package dev.crashteam.styx.model.web;

import dev.crashteam.styx.model.proxy.ProxySource;
import lombok.Data;

import java.util.List;

@Data
public class ProxyRequestParams {

    private String url;
    private String httpMethod;
    private Long timeout = 0L;
    private ProxySource proxySource;
    private List<ContextValue> context;

    @Data
    public static class ContextValue {
        private String key;
        private Object value;
    }
}
