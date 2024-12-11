package dev.crashteam.styx.model.proxy;

import lombok.Data;

import java.util.List;

@Data
public class ProxyInstance {

    private String host;
    private String port;
    private String proxyKey;
    private Boolean active;
    private int badProxyPoint;
    private ProxySource proxySource;
    private String user;
    private String password;
    private String countryCode;
    private String userAgent;
    private String system;
    private List<UserContext> userContext;
    private List<BadUrl> badUrls;

    @Data
    public static class BadUrl {
        private int point;
        private String url;
    }

    @Data
    public static class UserContext {
        private String contextId;
        private Long createdTime;
        private String appId;
        private Boolean inUse;
    }
}
