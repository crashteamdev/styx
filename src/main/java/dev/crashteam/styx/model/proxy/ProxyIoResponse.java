package dev.crashteam.styx.model.proxy;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ProxyIoResponse {
    private boolean success;
    private List<ProxyIoData> data;

    @Data
    public static class ProxyIoData {
        private String username;
        private String password;
        @JsonProperty("country_code")
        private String countryCode;
        @JsonProperty("list_ip")
        private List<ProxyIo> proxies;
    }

    @Data
    public static class ProxyIo {
        private String ip;
        @JsonProperty("port_socks5")
        private String portSocks5;
        @JsonProperty("port_http")
        private String portHttp;
        @JsonProperty("port_https")
        private String portHttps;
    }

}
