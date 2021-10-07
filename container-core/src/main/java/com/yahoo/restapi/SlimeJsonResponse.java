// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.slime.JsonFormat;
import com.yahoo.slime.Slime;

import java.io.IOException;
import java.io.OutputStream;

/**
 * A generic Json response using Slime for JSON encoding
 * 
 * @author bratseth
 */
public class SlimeJsonResponse extends HttpResponse {

    protected final Slime slime;

    public SlimeJsonResponse() {
        this(new Slime());
    }

    public SlimeJsonResponse(Slime slime) {
        super(200);
        this.slime = slime;
    }

    public SlimeJsonResponse(int statusCode, Slime slime) {
        super(statusCode);
        this.slime = slime;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(true).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
