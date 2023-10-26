// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    private final boolean compact;

    public SlimeJsonResponse() {
        this(new Slime());
    }

    public SlimeJsonResponse(Slime slime) { this(200, slime, true); }

    public SlimeJsonResponse(Slime slime, boolean compact) { this(200, slime, compact); }

    public SlimeJsonResponse(int statusCode, Slime slime) { this(statusCode, slime, true); }

    public SlimeJsonResponse(int statusCode, Slime slime, boolean compact) {
        super(statusCode);
        this.slime = slime;
        this.compact = compact;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        new JsonFormat(compact).encode(stream, slime);
    }

    @Override
    public String getContentType() { return "application/json"; }

}
