// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io.reader;

import com.google.common.annotations.Beta;

import java.io.IOException;
import java.io.Reader;
import java.util.List;

/**
 * A reader with a name. All reader methods are delegated to the wrapped reader.
 *
 * @author <a href="mailto:bratseth@yahoo-inc.com">Jon Bratseth</a>
 */
@Beta
public class NamedReader extends Reader {

    private final String name;
    private final Reader reader;

    public NamedReader(String name, Reader reader) {
        this.name = name;
        this.reader = reader;
    }

    public String getName() { return name; }

    public Reader getReader() { return reader; }

    /** Returns the name */
    public @Override String toString() {
        return name;
    }

    // The rest is reader method implementations which delegates to the wrapped reader
    public @Override int read(java.nio.CharBuffer charBuffer) throws java.io.IOException { return reader.read(charBuffer); }
    public @Override int read() throws java.io.IOException { return reader.read(); }
    public @Override int read(char[] chars) throws java.io.IOException { return reader.read(chars); }
    public @Override int read(char[] chars, int i, int i1) throws java.io.IOException { return reader.read(chars,i,i1); }
    public @Override long skip(long l) throws java.io.IOException { return reader.skip(l); }
    public @Override boolean ready() throws java.io.IOException { return reader.ready(); }
    public @Override boolean markSupported() { return reader.markSupported(); }
    public @Override void mark(int i) throws java.io.IOException { reader.mark(i); }
    public @Override void reset() throws java.io.IOException { reader.reset(); }
    public @Override void close() throws java.io.IOException { reader.close(); }

    /** Convenience method for closing a list of readers. Does nothing if the given reader list is null. */
    public static void closeAll(List<NamedReader> readers) {
        if (readers==null) return;
        for (Reader reader : readers) {
            try {
                reader.close();
            }
            catch (IOException e) {
                // Nothing to do about it
            }
        }
    }

}