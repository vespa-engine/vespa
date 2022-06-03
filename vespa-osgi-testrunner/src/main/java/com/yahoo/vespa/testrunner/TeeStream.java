package com.yahoo.vespa.testrunner;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Used to replace System.out and System.err, providing the ability to forward output to an additional sink.
 *
 * @author jonmv
 */
public class TeeStream extends OutputStream {

    private final AtomicReference<OutputStream> tee = new AtomicReference<>();
    private final OutputStream original;

    private TeeStream(OutputStream original) {
        this.original = original;
    }

    public static TeeStream ofSystemOut() {
        TeeStream teed = new TeeStream(System.out);
        System.setOut(new PrintStream(teed));
        return teed;
    }

    public static TeeStream ofSystemErr() {
        TeeStream teed = new TeeStream(System.err);
        System.setErr(new PrintStream(teed));
        return teed;
    }

    public void setTee(OutputStream tee) {
        if ( ! this.tee.compareAndSet(null, tee)) throw new IllegalStateException("tee already set");
    }

    public OutputStream clearTee() {
        OutputStream tee = this.tee.getAndSet(null);
        if (tee == null) throw new IllegalStateException("tee not set");
        return tee;
    }

    @Override
    public void write(int b) throws IOException {
        OutputStream maybe = tee.get();
        if (maybe != null) maybe.write(b);
        original.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        OutputStream maybe = tee.get();
        if (maybe != null) maybe.write(b, off, len);
        original.write(b, off, len);
    }

    @Override
    public void flush() throws IOException {
        OutputStream maybe = tee.get();
        if (maybe != null) maybe.flush();
        original.flush();
    }

}
