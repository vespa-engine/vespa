// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class StaticResponse extends HttpResponse {
    private final String contentType;
    private final InputStream body;

    /**
     * @param body    Ownership is passed to StaticResponse (is responsible for closing it)
     */
    public StaticResponse(int status, String contentType, InputStream body) {
        super(status);
        this.contentType = contentType;
        this.body = body;
    }

    public StaticResponse(int status, String contentType, String body) {
        this(status, contentType, new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        body.transferTo(outputStream);
        body.close();
    }

    @Override
    public String getContentType() {
        return contentType;
    }
}
