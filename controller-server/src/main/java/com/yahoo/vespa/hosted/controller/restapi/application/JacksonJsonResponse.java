// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author bratseth
 */
public class JacksonJsonResponse extends HttpResponse {

    private final JsonNode node;

    public JacksonJsonResponse(JsonNode node) {
        super(200);
        this.node = node;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new ObjectMapper().writeValue(stream, node);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
