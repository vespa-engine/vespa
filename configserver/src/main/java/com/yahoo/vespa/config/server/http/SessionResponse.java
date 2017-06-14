// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;

import static com.yahoo.jdisc.http.HttpResponse.Status.OK;

/**
 * Superclass for responses from session HTTP handlers.  Implements the
 * render method.
 *
 * @author hmusum
 * @since 5.1.14
 */
public class SessionResponse extends HttpResponse {
    private final Slime slime;
    protected final Cursor root;

    public SessionResponse() {
        super(OK);
        slime = new Slime();
        root = slime.setObject();
    }

    public SessionResponse(Slime slime, Cursor root) {
        super(OK);
        this.slime = slime;
        this.root = root;
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        new JsonFormat(true).encode(outputStream, slime);
    }

    @Override
    public String getContentType() {
        return HttpConfigResponse.JSON_CONTENT_TYPE;
    }
}
