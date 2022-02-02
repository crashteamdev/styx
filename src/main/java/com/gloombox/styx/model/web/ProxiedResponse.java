package com.gloombox.styx.model.web;

import lombok.Data;

@Data
public class ProxiedResponse {

    private int originalStatus;
    private String url;
    private Object body;

}
