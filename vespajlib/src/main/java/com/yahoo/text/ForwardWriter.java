// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.text;

import java.io.IOException;

/**
 * Wraps another writer and converts IOException to RuntimeExceptions.
 *
 * @author baldersheim
 */
public class ForwardWriter extends GenericWriter {

    private final GenericWriter out;

    public ForwardWriter(GenericWriter writer) {
        super();
        this.out = writer;
    }

    @Override
    public void write(char[] c, int offset, int bytes) {
        try {
            out.write(c, offset, bytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public GenericWriter write(AbstractUtf8Array v) {
        try {
            out.write(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
    @Override
    public void write(String v) {
        try {
            out.write(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public GenericWriter write(CharSequence c) {
        try {
            out.write(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }


    @Override
    public GenericWriter write(double d) {
        try {
            out.write(d);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public GenericWriter write(float f) {
        try {
            out.write(f);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public GenericWriter write(long v) {
        try {
            out.write(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
    @Override
    public void write(int v) {
        try {
            out.write(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    @Override
    public GenericWriter write(short v) {
        try {
            out.write(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
    @Override
    public GenericWriter write(char c) {
        try {
            out.write(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }
    @Override
    public GenericWriter write(byte b) {
        try {
            out.write(b);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }


    @Override
    public GenericWriter write(boolean v) {
        try {
            out.write(v);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return this;
    }

    @Override
    public void flush() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Gives access to the wrapped writer.
     * @return wrapped writer.
     */
    public GenericWriter getWriter() { return out; }

}
