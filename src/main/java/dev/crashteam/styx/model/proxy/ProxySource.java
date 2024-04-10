package dev.crashteam.styx.model.proxy;

public enum ProxySource {
    PROXY_LINE("PROXY_LINE"),
    MOBILE_PROXY("MOBILE_PROXY"),
    EXTERNAL_SOURCE("External source"),
    PROXYS_IO("PROXYS_IO");

    ProxySource(String value) {
        this.value = value;
    }

    private String value;

    public String getValue() {
        return value;
    }
}
