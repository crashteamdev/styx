package dev.crashteam.styx.model.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResult extends Result {

    private String errorMessage;
    @JsonIgnore
    private Throwable exception;

    public ErrorResult(int code, String errorMessage, String url, Throwable e) {
        super(code, url);
        this.errorMessage = errorMessage;
        this.exception = e;
    }

    public ErrorResult(int code, int originalStatus, String errorMessage, String url, Throwable e, Object body) {
        super(code, originalStatus, url, body);
        this.errorMessage = errorMessage;
        this.exception = e;
    }

    public static ErrorResult originalRequestError(int originalStatus, String url, Throwable e, Object body) {
        return new ErrorResult(MessageCode.PROXY_REQUEST_ERROR.getCode(), originalStatus,
                MessageCode.PROXY_REQUEST_ERROR.getText(), url, e, body);
    }

    public static ErrorResult unknownError(String url, Throwable e) {
        return new ErrorResult(MessageCode.UNKNOWN_SERVER_ERROR.getCode(),
                MessageCode.UNKNOWN_SERVER_ERROR.getText(), url, e);
    }

    public static ErrorResult proxyConnectionError(String url, Throwable e) {
        return new ErrorResult(MessageCode.PROXY_CONNECTION_EXCEPTION.getCode(),
                MessageCode.PROXY_CONNECTION_EXCEPTION.getText(), url, e);
    }
}
