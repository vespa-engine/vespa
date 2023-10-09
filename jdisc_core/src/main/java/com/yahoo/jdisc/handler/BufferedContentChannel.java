// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * This class implements an unlimited, non-blocking content queue. All {@link ContentChannel} methods are implemented
 * by pushing to a thread-safe internal queue. All of the queued calls are forwarded to another ContentChannel when
 * {@link #connectTo(ContentChannel)} is called. Once connected, this class becomes a non-buffering proxy for the
 * connected ContentChannel.
 *
 * @author Simon Thoresen Hult
 */
public final class BufferedContentChannel implements ContentChannel {

    private final Object lock = new Object();
    private List<Entry> queue = new LinkedList<>();
    private ContentChannel content = null;
    private boolean closed = false;
    private CompletionHandler closeCompletion = null;

    /**
     * <p>Connects this BufferedContentChannel to a ContentChannel. First, this method forwards all queued calls to the
     * connected ContentChannel. Once this method has been called, all future calls to {@link #write(ByteBuffer,
     * CompletionHandler)} and {@link #close(CompletionHandler)} are synchronously forwarded to the connected
     * ContentChannel.</p>
     *
     * @param content The ContentChannel to connect to.
     * @throws NullPointerException  If the <em>content</em> argument is null.
     * @throws IllegalStateException If another ContentChannel has already been connected.
     */
    public void connectTo(ContentChannel content) {
        Objects.requireNonNull(content, "content");
        boolean closed;
        List<Entry> queue;
        synchronized (lock) {
            if (this.content != null || this.queue == null) {
                throw new IllegalStateException();
            }
            closed = this.closed;
            queue = this.queue;
            this.queue = null;
        }
        for (Entry entry : queue) {
            content.write(entry.buf, entry.handler);
        }
        if (closed) {
            content.close(closeCompletion);
        }
        synchronized (lock) {
            this.content = content;
            lock.notifyAll();
        }
    }

    /**
     * <p>Returns whether or not {@link #connectTo(ContentChannel)} has been called. Even if this method returns false,
     * calling {@link #connectTo(ContentChannel)} might still throw an IllegalStateException if there is a race.</p>
     *
     * @return True if {@link #connectTo(ContentChannel)} has been called.
     */
    public boolean isConnected() {
        synchronized (lock) {
            return content != null;
        }
    }

    /**
     * <p>Creates a {@link ReadableContentChannel} and {@link #connectTo(ContentChannel) connects} to it. </p>
     *
     * @return The new ReadableContentChannel that this connected to.
     */
    public ReadableContentChannel toReadable() {
        ReadableContentChannel ret = new ReadableContentChannel();
        connectTo(ret);
        return ret;
    }

    /**
     * <p>Creates a {@link ContentInputStream} and {@link #connectTo(ContentChannel) connects} to its internal
     * ContentChannel.</p>
     *
     * @return The new ContentInputStream that this connected to.
     */
    public ContentInputStream toStream() {
        return toReadable().toStream();
    }

    @Override
    public void write(ByteBuffer buf, CompletionHandler handler) {
        ContentChannel content;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException();
            }
            if (queue != null) {
                queue.add(new Entry(buf, handler));
                return;
            }
            try {
                while (this.content == null) {
                    lock.wait(); // waiting for connectTo()
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            if (closed) {
                throw new IllegalStateException();
            }
            content = this.content;
        }
        content.write(buf, handler);
    }

    @Override
    public void close(CompletionHandler handler) {
        ContentChannel content;
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException();
            }
            if (queue != null) {
                closed = true;
                closeCompletion = handler;
                return;
            }
            try {
                while (this.content == null) {
                    lock.wait(); // waiting for connectTo()
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            if (closed) {
                throw new IllegalStateException();
            }
            closed = true;
            content = this.content;
        }
        content.close(handler);
    }

    private static class Entry {

        final ByteBuffer buf;
        final CompletionHandler handler;

        Entry(ByteBuffer buf, CompletionHandler handler) {
            this.handler = handler;
            this.buf = buf;
        }
    }

}
