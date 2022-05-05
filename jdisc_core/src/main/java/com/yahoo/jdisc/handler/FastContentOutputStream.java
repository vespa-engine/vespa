// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * <p>This class extends the {@link AbstractContentOutputStream}, and forwards all write() and close() calls to a {@link
 * FastContentWriter}. This means that once {@link #close()} has been called, the asynchronous completion of all pending
 * operations can be awaited using the {@link Future} interface of this class. Any asynchronous failure will be
 * rethrown when calling either of the get() methods on this class.</p>
 * <p>Please notice that the Future implementation of this class will NEVER complete unless {@link #close()} has been
 * called.</p>
 *
 * @author Simon Thoresen Hult
 */
public class FastContentOutputStream extends AbstractContentOutputStream implements Future<Boolean> {

    private final FastContentWriter out;

    /**
     * <p>Constructs a new FastContentOutputStream that writes into the given {@link ContentChannel}.</p>
     *
     * @param out The ContentChannel to write the stream into.
     */
    public FastContentOutputStream(ContentChannel out) {
        this(new FastContentWriter(out));
    }

    /**
     * <p>Constructs a new FastContentOutputStream that writes into the given {@link FastContentWriter}.</p>
     *
     * @param out The ContentWriter to write the stream into.
     */
    public FastContentOutputStream(FastContentWriter out) {
        Objects.requireNonNull(out, "out");
        this.out = out;
    }

    @Override
    protected void doFlush(ByteBuffer buf) {
        out.write(buf);
    }

    @Override
    protected void doClose() {
        out.close();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return out.cancel(mayInterruptIfRunning);
    }

    @Override
    public boolean isCancelled() {
        return out.isCancelled();
    }

    @Override
    public boolean isDone() {
        return out.isDone();
    }

    @Override
    public Boolean get() throws InterruptedException, ExecutionException {
        return out.get();
    }

    @Override
    public Boolean get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return out.get(timeout, unit);
    }

    public void addListener(Runnable listener, Executor executor) {
        out.addListener(listener, executor);
    }
}
