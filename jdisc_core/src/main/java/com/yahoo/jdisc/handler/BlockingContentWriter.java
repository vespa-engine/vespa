// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.Objects;

/**
 * This class provides a blocking <em>write</em>-interface to a {@link ContentChannel}. Both {@link
 * #write(ByteBuffer)} and {@link #close()} methods provide an internal {@link CompletionHandler} to the decorated
 * {@link ContentChannel} calls, and wait for these to be called before returning. If {@link
 * CompletionHandler#failed(Throwable)} is called, the corresponding Throwable is thrown to the caller.
 *
 * @author Simon Thoresen Hult
 * @see FastContentWriter
 */
public final class BlockingContentWriter {

    private final ContentChannel channel;

    /**
     * Creates a new BlockingContentWriter that encapsulates a given {@link ContentChannel}.
     *
     * @param content The ContentChannel to encapsulate.
     * @throws NullPointerException If the <em>content</em> argument is null.
     */
    public BlockingContentWriter(ContentChannel content) {
        Objects.requireNonNull(content, "content");
        this.channel = content;
    }

    /**
     * Writes to the underlying {@link ContentChannel} and waits for the operation to complete.
     *
     * @param buf The ByteBuffer to write.
     * @throws InterruptedException If the thread was interrupted while waiting.
     * @throws RuntimeException     If the operation failed to complete, see cause for details.
     */
    public void write(ByteBuffer buf) throws InterruptedException {
        try {
            FutureCompletion future = new FutureCompletion();
            channel.write(buf, future);
            future.get();
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            if (t instanceof Error) {
                throw (Error)t;
            }
            throw new RuntimeException(t);
        }
    }

    /**
     * Closes the underlying {@link ContentChannel} and waits for the operation to complete.
     *
     * @throws InterruptedException If the thread was interrupted while waiting.
     * @throws RuntimeException     If the operation failed to complete, see cause for details.
     */
    public void close() throws InterruptedException {
        try {
            FutureCompletion future = new FutureCompletion();
            channel.close(future);
            future.get();
        } catch (ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException)t;
            }
            if (t instanceof Error) {
                throw (Error)t;
            }
            throw new RuntimeException(t);
        }
    }

}
