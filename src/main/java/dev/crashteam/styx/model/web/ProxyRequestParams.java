package dev.crashteam.styx.model.web;

import lombok.Data;

@Data
public class ProxyRequestParams {

    private String url;
    private String httpMethod;
    private Long timeout = 0L;
    private Object body;

}
