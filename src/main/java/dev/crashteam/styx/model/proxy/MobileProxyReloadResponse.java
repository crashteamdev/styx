package dev.crashteam.styx.model.proxy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class MobileProxyReloadResponse {
    private String status;
}
