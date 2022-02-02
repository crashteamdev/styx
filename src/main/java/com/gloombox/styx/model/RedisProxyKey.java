package com.gloombox.styx.model;

public enum RedisProxyKey {

    PROXY_KEY("PROXY_KEY");

    private String value;

    RedisProxyKey(String value) {
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
