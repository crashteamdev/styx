package dev.crashteam.styx.model.web;

public enum MessageCode {

    SUCCESS(0, "Request successfully proxied and retrieved."),
    REQUEST_WITHOUT_PROXY_SUCCESS(-1, "Request successfully retrieved, but no proxy was used"),
    UNKNOWN_SERVER_ERROR(-2, "Unknown error acquired"),
    PROXY_REQUEST_ERROR(-3, "Proxy request error"),
    RETRIES_EXHAUSTED_ERROR(-4, "Retries exhausted");

    private int code;
    private String text;

    MessageCode(int code, String text) {
        this.code = code;
        this.text = text;
    }

    public int getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    public static MessageCode valueOf(int code) {
        for (MessageCode message : MessageCode.values())
            if (message.getCode() == code)
                return message;

        return null;
    }
}
