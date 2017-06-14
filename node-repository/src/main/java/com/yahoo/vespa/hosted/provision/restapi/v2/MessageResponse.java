// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A 200 ok response with a message in JSON
 *
 * @author bratseth
 */
public class MessageResponse extends HttpResponse {

    private final Slime slime = new Slime();

    public MessageResponse(String message) {
        super(200);
        slime.setObject().setString("message", message);
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
