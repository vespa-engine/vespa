// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.container.jdisc.HttpResponse;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import static com.yahoo.jdisc.http.HttpResponse.Status.*;

/**
 * Represents a response for a request to read contents of a file.
 *
 * @author lulf
 * @since 5.1
 */
public class SessionContentReadResponse extends HttpResponse {
    private final ApplicationFile file;

    public SessionContentReadResponse(ApplicationFile file) {
        super(OK);
        this.file = file;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        try (InputStream inputStream = file.createInputStream()) {
            IOUtils.copyLarge(inputStream, outputStream, new byte[1]);
        }
    }

    @Override
    public String getContentType() {
        return HttpResponse.DEFAULT_MIME_TYPE;
    }
}
