// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 *
 * @since 5.1.23
 */
public class ByteLimitedInputStream extends InputStream {

    private final InputStream wrappedStream;
    private int remaining;

    public ByteLimitedInputStream(InputStream wrappedStream, int limit) {
        this.wrappedStream = wrappedStream;
        if (limit < 0) {
            throw new IllegalArgumentException("limit cannot be 0");
        }
        this.remaining = limit;
    }

    @Override
    public int read() throws IOException {
        if (remaining <= 0) {
            return -1;
        }
        int retval = wrappedStream.read();
        if (retval < 0) {
            remaining = 0;
        } else {
            --remaining;
        }
        return retval;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) {
            throw new NullPointerException();
        } else if (off < 0 || len < 0 || len > b.length - off) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return 0;
        }

        if (remaining <= 0) {
            return -1;
        }

        int bytesToRead = Math.min(remaining, len);
        int retval = wrappedStream.read(b, off, bytesToRead);

        if (retval < 0) {
            //end of underlying stream was reached, and nothing was read.
            remaining = 0;
        } else {
            remaining -= retval;
        }
        return retval;
    }

    @Override
    public int available() throws IOException {
        return remaining;
    }

    @Override
    public void close() throws IOException {
        //we will never close the underlying stream
        if (remaining <= 0) {
            return;
        }
        while (remaining > 0) {
            skip(remaining);
        }
    }

}
