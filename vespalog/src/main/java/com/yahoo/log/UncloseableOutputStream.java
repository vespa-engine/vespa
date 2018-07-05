// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import java.io.IOException;
import java.io.OutputStream;

/**
 * @author Simon Thoresen Hult
 * @since 5.1.14
 */
class UncloseableOutputStream extends OutputStream {

    private final OutputStream out;

    public UncloseableOutputStream(OutputStream out) {
        this.out = out;
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
    }

    @Override
    public void close() {
        // ignore
    }
}
