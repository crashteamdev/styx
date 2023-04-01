package dev.crashteam.styx.model;

public enum RedisKey {

    REQUEST_KEY("REQUEST_KEY"),
    PROXY_KEY("PROXY_KEY"),
    FORBIDDEN_PROXY("FORBIDDEN_PROXY_KEY");

    private String value;

    RedisKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "RedisProxyKey{" +
                "key='" + value + '\'' +
                '}';
    }
}
