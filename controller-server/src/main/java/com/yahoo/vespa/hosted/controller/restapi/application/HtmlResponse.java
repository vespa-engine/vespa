// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.application;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author freva
 */
public class HtmlResponse extends HttpResponse {

    private final String content;

    public HtmlResponse(String content) {
        super(200);
        this.content = content;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        stream.write(content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getContentType() { return "text/html"; }

}
