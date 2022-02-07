package dev.crashteam.styx.model.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Result {

    private int code;
    private Integer originalStatus;
    private String message;
    private String url;
    private Object body;

    public Result(int code, Integer originalStatus, String message, String url, Object body) {
        this.code = code;
        this.originalStatus = originalStatus;
        this.message = message;
        this.url = url;
        this.body = body;
    }

    public static Result success(int originalStatus, String url, Object body) {
        return new Result(MessageCode.SUCCESS.getCode(), originalStatus, null, url, body);
    }

    public static Result successNoProxy(int originalStatus, String url, Object body) {
        return new Result(MessageCode.REQUEST_WITHOUT_PROXY_SUCCESS.getCode(), originalStatus, null, url, body);
    }

    public static Result proxyError(int originalStatus, String url) {
        return new Result(MessageCode.PROXY_REQUEST_ERROR.getCode(), originalStatus, MessageCode.PROXY_REQUEST_ERROR.getText(), url, null);
    }

    public static Result noActiveProxyError(String url) {
        return new Result(MessageCode.NO_ACTIVE_PROXIES_ERROR.getCode(), null, MessageCode.NO_ACTIVE_PROXIES_ERROR.getText(), url, null);
    }

    public static Result exhaustedRetriesProxyError(String url) {
        return new Result(MessageCode.RETRIES_EXHAUSTED_ERROR.getCode(), null, MessageCode.RETRIES_EXHAUSTED_ERROR.getText(), url, null);
    }

    public static Result unknownError(int originalStatus, String url) {
        return new Result(MessageCode.UNKNOWN_SERVER_ERROR.getCode(), originalStatus, MessageCode.UNKNOWN_SERVER_ERROR.getText(), url, null);
    }

}
