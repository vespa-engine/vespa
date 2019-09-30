// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

/**
 * Returns a response containing an array of links to sub-resources
 * 
 * @author bratseth
 */
public class ResourceResponse extends HttpResponse {

    private final Slime slime = new Slime();

    public ResourceResponse(URI parentUrl, String ... subResources) {
        super(200);
        Cursor resourceArray = slime.setObject().setArray("resources");
        for (String subResource : subResources) {
            Cursor resourceEntry = resourceArray.addObject();
            resourceEntry.setString("url", new Uri(parentUrl).append(subResource)
                                                             .withTrailingSlash()
                                                             .toString());
        }
    }

    public ResourceResponse(HttpRequest request, String ... subResources) {
        this(request.getUri(), subResources);
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
