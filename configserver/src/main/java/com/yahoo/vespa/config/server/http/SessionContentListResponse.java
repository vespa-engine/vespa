// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http;

import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Represents a request for listing files within an application package.
 *
 * @author lulf
 * @since 5.1
 */
class SessionContentListResponse extends SessionResponse {
    private final Slime slime = new Slime();

    public SessionContentListResponse(String urlBase, List<ApplicationFile> files) {
        super();
        Cursor array = slime.setArray();
        for (ApplicationFile file : files) {
            array.addString(urlBase + file.getPath() + (file.isDirectory() ? "/" : ""));
        }
    }

    @Override
    public void render(OutputStream outputStream) throws IOException {
        new JsonFormat(true).encode(outputStream, slime);
    }
}
