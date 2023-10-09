// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;

/**
 * Response that contains some utility stuff for rendering json.
 *
 * @author Ulf Lilleengen
 * @since 5.3
 */
public class JSONResponse extends HttpResponse {
    private final Slime slime = new Slime();
    protected final Cursor object;
    public JSONResponse(int status) {
        super(status);
        this.object = slime.setObject();
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
