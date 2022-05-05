// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * <p>This class provides a non-blocking, awaitable <em>write</em>-interface to a {@link ContentChannel}.
 * The {@link CompletableFuture<Boolean>} interface can be used to await
 * the asynchronous completion of all pending operations. Any asynchronous
 * failure will be rethrown when calling either of the get() methods on
 * this class.</p>
 * <p>Please notice that the Future implementation of this class will NEVER complete unless {@link #close()} has been
 * called; please use try-with-resources to ensure that close() is called.</p>
 *
 * @author Simon Thoresen Hult
 */
public class FastContentWriter extends CompletableFuture<Boolean> implements AutoCloseable {

    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger numPendingCompletions = new AtomicInteger();
    private final CompletionHandler completionHandler = new SimpleCompletionHandler();
    private final ContentChannel out;

    /**
     * <p>Creates a new FastContentWriter that encapsulates a given {@link ContentChannel}.</p>
     *
     * @param out The ContentChannel to encapsulate.
     * @throws NullPointerException If the <em>content</em> argument is null.
     */
    public FastContentWriter(ContentChannel out) {
        Objects.requireNonNull(out, "out");
        this.out = out;
    }

    /**
     * <p>This is a convenience method to convert the given string to a ByteBuffer of UTF8 bytes, and then passing that
     * to {@link #write(ByteBuffer)}.</p>
     *
     * @param str The string to write.
     */
    public void write(String str) {
        write(str.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * <p>This is a convenience method to convert the given byte array into a ByteBuffer object, and then passing that
     * to {@link #write(java.nio.ByteBuffer)}.</p>
     *
     * @param buf The bytes to write.
     */
    public void write(byte[] buf) {
        write(buf, 0, buf.length);
    }

    /**
     * <p>This is a convenience method to convert a subarray of the given byte array into a ByteBuffer object, and then
     * passing that to {@link #write(java.nio.ByteBuffer)}.</p>
     *
     * @param buf    The bytes to write.
     * @param offset The offset of the subarray to be used.
     * @param length The length of the subarray to be used.
     */
    public void write(byte[] buf, int offset, int length) {
        write(ByteBuffer.wrap(buf, offset, length));
    }

    /**
     * <p>Writes to the underlying {@link ContentChannel}. If {@link CompletionHandler#failed(Throwable)} is called,
     * either of the get() methods will rethrow that Throwable.</p>
     *
     * @param buf The ByteBuffer to write.
     */
    public void write(ByteBuffer buf) {
        numPendingCompletions.incrementAndGet();
        try {
            out.write(buf, completionHandler);
        } catch (Throwable t) {
            completeExceptionally(t);
            throw t;
        }
    }

    /**
     * <p>Closes the underlying {@link ContentChannel}. If {@link CompletionHandler#failed(Throwable)} is called,
     * either of the get() methods will rethrow that Throwable.</p>
     */
    @Override
    public void close() {
        numPendingCompletions.incrementAndGet();
        closed.set(true);
        try {
            out.close(completionHandler);
        } catch (Throwable t) {
            completeExceptionally(t);
            throw t;
        }
    }

    public void addListener(Runnable listener, Executor executor) {
        whenCompleteAsync((__, ___) -> listener.run(), executor);
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    private class SimpleCompletionHandler implements CompletionHandler {

        @Override
        public void completed() {
            numPendingCompletions.decrementAndGet();
            if (closed.get() && numPendingCompletions.get() == 0) {
                complete(true);
            }
        }

        @Override
        public void failed(Throwable t) {
            completeExceptionally(t);
        }
    }
}
