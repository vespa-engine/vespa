// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProxyResponse extends HttpResponse {
    private final String contentType;
    private final InputStream inputStream;

    /**
     *
     * @param status
     * @param contentType
     * @param inputStream    Ownership is passed to ProxyResponse (responsible for closing it)
     */
    public ProxyResponse(int status, String contentType, InputStream inputStream) {
        super(status);
        this.contentType = contentType;
        this.inputStream = inputStream;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        IOUtils.copy(inputStream, outputStream);
        inputStream.close();
    }

    @Override
    public String getContentType() {
        return contentType;
    }
}
