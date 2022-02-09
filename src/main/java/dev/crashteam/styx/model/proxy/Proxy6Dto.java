package dev.crashteam.styx.model.proxy;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Proxy6Dto {

    private String status;
    @JsonProperty("user_id")
    private String userId;
    private String balance;
    private String currency;
    @JsonProperty("list_count")
    private Long listCount;
    @JsonProperty("list")
    private Map<String, ProxyData> proxies;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ProxyData {
        private String id;
        private String ip;
        private String host;
        private String port;
        private String user;
        private String pass;
        private String type;
        private String country;
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime date;
        @JsonProperty("date_end")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dateEnd;
        @JsonProperty("unixtime")
        private String unixTime;
        private String descr;
        private Integer active;

    }
}
