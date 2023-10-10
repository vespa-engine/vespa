// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.text.Utf8;

import java.io.IOException;
import java.io.OutputStream;

public class ErrorHttpResponse extends HttpResponse {

    private final String msg;

    public ErrorHttpResponse(final int statusCode, final String msg) {
        super(statusCode);
        this.msg = msg;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        outputStream.write(Utf8.toBytes(msg));
    }

}
