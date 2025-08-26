// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.io.reader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.CharBuffer;
import java.util.List;

/**
 * A reader identified by a name. All reader methods are delegated to the wrapped reader.
 *
 * @author bratseth
 */
public class NamedReader extends Reader {

    private final String name;
    private final Reader reader;

    public NamedReader(String name, Reader reader) {
        this.name = name;
        this.reader = reader;
    }

    public String getName() { return name; }

    public Reader getReader() { return reader; }

    @Override
    public int hashCode() { return name.hashCode(); }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof NamedReader other)) return false;
        return other.name.equals(this.name);
    }

    @Override
    public String toString() { return name; }
    // Need to override static methods in Reader to return NamedReader instances
    public static Reader of(java.lang.CharSequence charSequence) {
        return new NamedReader("of", new java.io.StringReader(charSequence.toString()));
    }

    // The rest is reader method implementations which delegates to the wrapped reader
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

    public static Reader nullReader() { return new NamedReader("nullReader", Reader.nullReader()); }

}
