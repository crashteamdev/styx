package dev.crashteam.styx.model.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxyHouseResponse {
    private boolean successful;
    private ProxyHouseError error;
    private ProxyData data;

    @Data
    public static class ProxyHouseError {
        private String code;
        private String description;
    }

    @Data
    public static class ProxyData {
        private Map<String, ProxyHouseSource> proxies;
    }

    @Data
    public static class ProxyHouseSource {
        public int id;
        public String login;
        private int active;
        public String password;
        public String expired_at;
        public String created_at;
        public String updated_at;
        public String ip;
        public String ip_out;
        public int http_port;
        public int socks_port;
    }
}
