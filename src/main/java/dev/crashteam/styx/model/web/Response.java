package dev.crashteam.styx.model.web;

import lombok.Data;

@Data
public class Response {

    private int originalStatus;
    private String url;
    private Object body;

}
