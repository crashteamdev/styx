package dev.crashteam.styx.model.proxy;

public enum ProxySource {
    PROXY6("Proxy6"),
    EXTERNAL_SOURCE("External source");

    ProxySource(String value) {
        this.value = value;
    }

    private String value;

    public String getValue() {
        return value;
    }
}
