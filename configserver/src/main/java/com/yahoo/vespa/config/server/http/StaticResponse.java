// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class StaticResponse extends HttpResponse {

    private final String contentType;
    private final byte[] body;

    public StaticResponse(int status, String contentType, byte[] body) {
        super(status);
        this.contentType = contentType;
        this.body = body;
    }

    public StaticResponse(int status, String contentType, String body) {
        this(status, contentType, body.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        outputStream.write(body);
    }

    @Override
    public String getContentType() {
        return contentType;
    }

}
