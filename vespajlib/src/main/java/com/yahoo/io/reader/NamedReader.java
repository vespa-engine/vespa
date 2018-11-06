// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io.reader;

import com.google.common.annotations.Beta;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.List;

/**
 * A reader with a name. All reader methods are delegated to the wrapped reader.
 *
 * @author bratseth
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
    @Override
    public String toString() {
        return name;
    }

    // The rest is reader method implementations which delegates to the wrapped reader
    public static Reader nullReader() { return new NamedReader("nullReader", Reader.nullReader()); }
    @Override
    public int read(CharBuffer charBuffer) throws IOException { return reader.read(charBuffer); }
    @Override
    public int read() throws IOException { return reader.read(); }
    @Override
    public int read(char[] chars) throws IOException { return reader.read(chars); }
    @Override
    public int read(char[] chars, int i, int i1) throws IOException { return reader.read(chars,i,i1); }
    @Override
    public long skip(long l) throws IOException { return reader.skip(l); }
    @Override
    public boolean ready() throws IOException { return reader.ready(); }
    @Override
    public boolean markSupported() { return reader.markSupported(); }
    @Override
    public void mark(int i) throws IOException { reader.mark(i); }
    @Override
    public void reset() throws IOException { reader.reset(); }
    @Override
    public void close() throws IOException { reader.close(); }
    @Override
    public long transferTo(Writer out) throws IOException { return reader.transferTo(out); }

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
