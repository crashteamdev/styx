package dev.crashteam.styx.model.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MobileProxyResponse {

    @JsonProperty("proxy_independent_http_hostname")
    private String host;
    @JsonProperty("proxy_independent_socks5_hostname")
    private String hostSocks5;
    @JsonProperty("proxy_independent_port")
    private String port;
    @JsonProperty("proxy_login")
    private String login;
    @JsonProperty("proxy_pass")
    private String password;
    @JsonProperty("proxy_key")
    private String proxyKey;

}
