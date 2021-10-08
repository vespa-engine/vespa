// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.horizon;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author valerijf
 */
public class HorizonResponse implements AutoCloseable {

    private final int code;
    private final InputStream inputStream;

    public HorizonResponse(int code, InputStream inputStream) {
        this.code = code;
        this.inputStream = inputStream;
    }

    public int code() {
        return code;
    }

    public InputStream inputStream() {
        return inputStream;
    }

    public static HorizonResponse empty() {
        return new HorizonResponse(200, InputStream.nullInputStream());
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
    }
}
