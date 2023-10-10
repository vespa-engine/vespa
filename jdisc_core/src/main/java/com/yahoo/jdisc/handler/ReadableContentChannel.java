// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Queue;

/**
 * <p>This class implements a {@link ContentChannel} that has a blocking <em>read</em> interface. Use this class if you
 * intend to consume the content of the ContentChannel yourself. If you intend to forward the content to another
 * ContentChannel, use {@link BufferedContentChannel} instead. If you <em>might</em> want to consume the content, return
 * a {@link BufferedContentChannel} up front, and {@link BufferedContentChannel#connectTo(ContentChannel) connect} that
 * to a ReadableContentChannel at the point where you decide to consume the data.</p>
 *
 * @author Simon Thoresen Hult
 */
public final class ReadableContentChannel implements ContentChannel, Iterable<ByteBuffer> {

    private final Object lock = new Object();
    private Queue<Entry> queue = new LinkedList<>();
    private boolean closed = false;

    @Override
    public void write(ByteBuffer buf, CompletionHandler handler) {
        Objects.requireNonNull(buf, "buf");
        synchronized (lock) {
            if (closed || queue == null) {
                throw new IllegalStateException(this + " is closed");
            }
            queue.add(new Entry(buf, handler));
            lock.notifyAll();
        }
    }

    @Override
    public void close(CompletionHandler handler) {
        synchronized (lock) {
            if (closed || queue == null) {
                throw new IllegalStateException(this + " is already closed");
            }
            closed = true;
            queue.add(new Entry(null, handler));
            lock.notifyAll();
        }
    }

    @Override
    public Iterator<ByteBuffer> iterator() {
        return new MyIterator();
    }

    /**
     * <p>Returns a lower-bound estimate on the number of bytes available to be {@link #read()} without blocking. If
     * the returned number is larger than zero, the next call to {@link #read()} is guaranteed to not block.</p>
     *
     * @return The number of bytes available to be read without blocking.
     */
    public int available() {
        Entry entry;
        synchronized (lock) {
            if (queue == null) {
                return 0;
            }
            entry = queue.peek();
        }
        if (entry == null || entry.buf == null) {
            return 0;
        }
        return entry.buf.remaining();
    }

    /**
     * <p>Returns the next ByteBuffer in the internal queue. Before returning, this method calls {@link
     * CompletionHandler#completed()} on the {@link CompletionHandler} that was submitted along with the ByteBuffer. If
     * there are no ByteBuffers in the queue, this method waits indefinitely for either {@link
     * #write(ByteBuffer, CompletionHandler)} or {@link #close(CompletionHandler)} to be called. Once closed and the
     * internal queue drained, this method returns null.</p>
     *
     * @return The next ByteBuffer in queue, or null if this ReadableContentChannel is closed.
     * @throws IllegalStateException If the current thread is interrupted while waiting.
     */
    public ByteBuffer read() {
        Entry entry;
        synchronized (lock) {
            try {
                while (queue != null && queue.isEmpty()) {
                    lock.wait();
                }
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            if (queue == null) {
                return null;
            }
            entry = queue.poll();
            if (entry.buf == null) {
                queue = null;
            }
        }
        if (entry.handler != null) {
            entry.handler.completed();
        }
        return entry.buf;
    }

    /**
     * <p>This method calls {@link CompletionHandler#failed(Throwable)} on all pending {@link CompletionHandler}s, and
     * blocks all future operations to this ContentChannel (i.e. calls to {@link #write(ByteBuffer, CompletionHandler)}
     * and {@link #close(CompletionHandler)} throw IllegalStateExceptions).</p>
     *
     * <p>This method will also notify any thread waiting in {@link #read()}.</p>
     *
     * @param t The Throwable to pass to all pending CompletionHandlers.
     * @throws IllegalStateException If this method is called more than once.
     */
    public void failed(Throwable t) {
        Queue<Entry> queue;
        synchronized (lock) {
            if ((queue = this.queue) == null) {
                throw new IllegalStateException();
            }
            this.queue = null;
            lock.notifyAll();
        }
        for (Entry entry : queue) {
            entry.handler.failed(t);
        }
    }

    /**
     * <p>Creates a {@link ContentInputStream} that wraps this ReadableContentChannel.</p>
     *
     * @return The new ContentInputStream that wraps this.
     */
    public ContentInputStream toStream() {
        return new ContentInputStream(this);
    }

    private class MyIterator implements Iterator<ByteBuffer> {

        ByteBuffer next;

        @Override
        public boolean hasNext() {
            if (next != null) {
                return true;
            }
            next = read();
            return next != null;
        }

        @Override
        public ByteBuffer next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            ByteBuffer ret = next;
            next = null;
            return ret;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
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
