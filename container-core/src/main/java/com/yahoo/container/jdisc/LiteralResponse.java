// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class LiteralResponse extends HttpResponse {
    private final String body;

    public LiteralResponse(int code, String body) {
        super(code);
        this.body = body;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        outputStream.write(body.getBytes(StandardCharsets.UTF_8));
    }
}
