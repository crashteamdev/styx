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
    private String url;
    private String httpMethod;
    private Object body;


    public Result(int code, Integer originalStatus, String url, Object body, String httpMethod) {
        this.code = code;
        this.originalStatus = originalStatus;
        this.url = url;
        this.body = body;
        this.httpMethod = httpMethod;
    }

    public Result(int code, String url) {
        this.code = code;
        this.url = url;
    }

    public Result(int code, Integer originalStatus, String url, Object body) {
        this.code = code;
        this.originalStatus = originalStatus;
        this.url = url;
        this.body = body;
    }

    public static Result success(int originalStatus, String url, Object body, String httpMethod) {
        return new Result(MessageCode.SUCCESS.getCode(), originalStatus, url, body, httpMethod);
    }

    public static Result successNoProxy(int originalStatus, String url, Object body, String httpMethod) {
        return new Result(MessageCode.REQUEST_WITHOUT_PROXY_SUCCESS.getCode(), originalStatus, url, body, httpMethod);
    }
}
