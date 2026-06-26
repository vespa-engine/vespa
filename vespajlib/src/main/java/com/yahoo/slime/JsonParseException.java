// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.slime;

/**
 * @author hakonhall
 */
public class JsonParseException extends RuntimeException {

    private static final long serialVersionUID = 1586949558L;

    private final BufferedInput input;

    JsonParseException(BufferedInput input) {
        super(input.getErrorMessage());
        this.input = input;
    }

    public byte[] getOffendingBytes() {
        // potentially expensive array copy
        return input.getOffending();
    }

}
