// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.configserver;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * @author valerijf
 */
public class ProxyResponse extends HttpResponse {

    private final String content;
    private final String contentType;

    public ProxyResponse(String content, String contentType, int status) {
        super(status);
        this.content = content;
        this.contentType = contentType;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        outputStream.write(content.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String getContentType() {
        return Optional.ofNullable(contentType).orElseGet(super::getContentType);
    }
}
