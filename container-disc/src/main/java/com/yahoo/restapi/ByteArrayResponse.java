// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.restapi;

import com.yahoo.container.jdisc.HttpResponse;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author freva
 */
public class ByteArrayResponse extends HttpResponse {

    private final byte[] data;

    public ByteArrayResponse(byte[] data) {
        super(200);
        this.data = data;
    }

    @Override
    public void render(OutputStream stream) throws IOException {
        stream.write(data);
    }

}
