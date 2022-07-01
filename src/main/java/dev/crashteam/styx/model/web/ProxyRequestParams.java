package dev.crashteam.styx.model.web;

import lombok.Data;

import java.util.List;

@Data
public class ProxyRequestParams {

    private String url;
    private String httpMethod;
    private Long timeout = 0L;
    private List<ContextValue> context;

    @Data
    public static class ContextValue {
        private String key;
        private Object value;
    }
}
