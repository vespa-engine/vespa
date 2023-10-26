// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.http.v2.response;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.server.http.HttpConfigResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

/**
 * Response that lists applications.
 *
 * @author Ulf Lilleengen
 */
public class ListApplicationsResponse extends HttpResponse {

    private final Slime slime = new Slime();

    public ListApplicationsResponse(int status, Collection<String> applications) {
        super(status);
        Cursor array = slime.setArray();
        for (String url : applications) {
            array.addString(url);
        }
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
