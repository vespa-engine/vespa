// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * @author bratseth
 */
public class StringResponse extends HttpResponse {

    private final String message;
    
    public StringResponse(String message) {
        super(200);
        this.message = message;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        stream.write(message.getBytes(StandardCharsets.UTF_8));
    }

}
