package com.yahoo.io;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

/**
 * Input stream wrapping an input stream supplier, which doesn't have content yet at declaration time.
 *
 * @author jonmv
 */
public class LazyInputStream extends InputStream {

    private Supplier<InputStream> source;
    private InputStream delegate;

    public LazyInputStream(Supplier<InputStream> source) {
        this.source = source;
    }

    private InputStream in() {
        if (delegate == null) {
            delegate = source.get();
            source = null;
        }
        return delegate;
    }

    @Override
    public int read() throws IOException { return in().read(); }

    @Override
    public int read(byte[] b, int off, int len) throws IOException { return in().read(b, off, len); }

    @Override
    public long skip(long n) throws IOException { return in().skip(n); }

    @Override
    public int available() throws IOException { return in().available(); }

    @Override
    public void close() throws IOException { in().close(); }

    @Override
    public synchronized void mark(int readlimit) { in().mark(readlimit); }

    @Override
    public synchronized void reset() throws IOException { in().reset(); }

    @Override
    public boolean markSupported() { return in().markSupported(); }

}
