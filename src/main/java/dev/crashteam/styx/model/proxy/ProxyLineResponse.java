package dev.crashteam.styx.model.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProxyLineResponse {

    private int count;
    private List<ProxyLineResult> results;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProxyLineResult {
        private long id;
        private String ip;
        @JsonProperty("port_http")
        private String portHttp;
        @JsonProperty("port_socks5")
        private String portSocks5;
        private String user;
        private String username;
        private String password;
        @JsonProperty("order_id")
        private Long orderId;
    }

}
