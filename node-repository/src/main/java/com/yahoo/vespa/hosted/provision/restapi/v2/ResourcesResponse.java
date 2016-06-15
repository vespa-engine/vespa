// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;

/** A response which lists a set of subresources as full urls */
public class ResourcesResponse extends HttpResponse {

    private final URI parentUrl;

    private final String[] subResources;

    public ResourcesResponse(URI parentUrl, String[] subResources) {
        super(200);
        this.parentUrl = parentUrl;
        this.subResources = subResources;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        String parentUrlString = parentUrl.toString();
        if ( ! parentUrlString.endsWith("/"))
            parentUrlString = parentUrlString + "/";

        Slime slime = new Slime();
        Cursor root = slime.setObject();
        Cursor array = root.setArray("resources");
        for (String subResource : subResources) {
            array.addObject().setString("url", parentUrlString + subResource + "/");
        }
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

    public static ResourcesResponse fromStrings(URI parentUrl, String ... subResources) {
        return new ResourcesResponse(parentUrl, subResources);
    }

}
