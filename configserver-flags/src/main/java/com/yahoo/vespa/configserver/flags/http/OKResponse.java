// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.configserver.flags.http;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.jdisc.Response;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author hakonhall
 */
public class OKResponse extends HttpResponse {

    private static final byte[] EMPTY_JSON_OBJECT = {'{', '}'};

    public OKResponse() {
        super(Response.Status.OK);
    }

    @Override
    public String getContentType() {
        return "application/json";
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        outputStream.write(EMPTY_JSON_OBJECT);
    }

}
