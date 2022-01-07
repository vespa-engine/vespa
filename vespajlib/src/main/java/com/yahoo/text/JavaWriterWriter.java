// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.io.IOException;
import java.io.Writer;

/**
 * Wraps a simple java.lang.Writer. Of course you loose the possible optimizations.
 *
 * @author baldersheim
 */
public final class JavaWriterWriter extends GenericWriter {

    final Writer out;

    public JavaWriterWriter(Writer writer) {
        out = writer;
    }

    @Override
    public void write(char[] c, int offset, int bytes) {
        try {
            out.write(c, offset, bytes);
        } catch (IOException e) {
            throw new RuntimeException("Caught exception in Java writer.write.", e);
        }
    }

    @Override
    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Caught exception in Java writer.flush.", e);
        }
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            throw new RuntimeException("Caught exception in Java writer.close.", e);
        }
    }

    public Writer getWriter() { return out; }

}
