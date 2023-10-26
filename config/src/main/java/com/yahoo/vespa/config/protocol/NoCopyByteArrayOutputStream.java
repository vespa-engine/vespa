// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.protocol;

import java.io.ByteArrayOutputStream;

/**
 * Subclass of {@link java.io.ByteArrayOutputStream} that gives effective {@link #toByteArray()} method.
 *
 * @author Ulf Lilleengen
 */
class NoCopyByteArrayOutputStream extends ByteArrayOutputStream {
    public NoCopyByteArrayOutputStream() {
        super();
    }

    public NoCopyByteArrayOutputStream(int initialSize) {
        super(initialSize);
    }

    @Override
    public byte[] toByteArray() {
        return buf;
    }
}
