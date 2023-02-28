package dev.crashteam.styx.model.proxy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
public class ProxyInstance {

    private String host;
    private String port;
    private Boolean active;
    private int badProxyPoint;
    private ProxySource proxySource;
    private String user;
    private String password;
}
