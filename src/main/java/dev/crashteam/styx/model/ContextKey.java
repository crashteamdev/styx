package dev.crashteam.styx.model;

public enum ContextKey {

    HEADERS("headers"),
    CONTENT("content");

    private String value;

    ContextKey(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
