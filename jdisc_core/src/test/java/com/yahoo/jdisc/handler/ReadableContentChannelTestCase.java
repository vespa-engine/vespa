// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.Test;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.*;

import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/**
 * @author Simon Thoresen Hult
 */
public class ReadableContentChannelTestCase {

    @Test
    public void requireThatWriteNullThrowsException() {
        ReadableContentChannel content = new ReadableContentChannel();
        try {
            content.write(null, new MyCompletion());
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    public void requireThatWriteAfterCloseThrowsException() {
        ReadableContentChannel content = new ReadableContentChannel();
        content.close(null);
        try {
            content.write(ByteBuffer.allocate(69), new MyCompletion());
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void requireThatWriteAfterFailedThrowsException() {
        ReadableContentChannel content = new ReadableContentChannel();
        content.failed(new RuntimeException());
        try {
            content.write(ByteBuffer.allocate(69), new MyCompletion());
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void requireThatCloseAfterCloseThrowsException() {
        ReadableContentChannel content = new ReadableContentChannel();
        content.close(null);
        try {
            content.close(null);
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void requireThatCloseAfterFailedThrowsException() {
        ReadableContentChannel content = new ReadableContentChannel();
        content.failed(new RuntimeException());
        try {
            content.close(null);
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void requireThatFailedAfterFailedThrowsException() {
        ReadableContentChannel content = new ReadableContentChannel();
        content.failed(new RuntimeException());
        try {
            content.failed(new RuntimeException());
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void requireThatIteratorDoesNotSupportRemove() {
        try {
            new ReadableContentChannel().iterator().remove();
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    public void requireThatWrittenBufferCanBeRead() {
        ReadableContentChannel content = new ReadableContentChannel();
        ByteBuffer buf = ByteBuffer.allocate(69);
        content.write(buf, null);
        assertSame(buf, content.read());
    }

    @Test
    public void requireThatWrittenBuffersAreReadInOrder() {
        ReadableContentChannel content = new ReadableContentChannel();
        ByteBuffer foo = ByteBuffer.allocate(69);
        content.write(foo, null);
        ByteBuffer bar = ByteBuffer.allocate(69);
        content.write(bar, null);
        content.close(null);
        assertSame(foo, content.read());
        assertSame(bar, content.read());
    }

    @Test
    public void requireThatReadAfterCloseIsNull() {
        ReadableContentChannel content = new ReadableContentChannel();
        content.close(null);
        assertNull(content.read());
        assertNull(content.read());
    }

    @Test
    public void requireThatWrittenBufferCanBeReadByIterator() {
        ReadableContentChannel content = new ReadableContentChannel();
        ByteBuffer foo = ByteBuffer.allocate(69);
        content.write(foo, null);
        ByteBuffer bar = ByteBuffer.allocate(69);
        content.write(bar, null);
        content.close(null);

        Iterator<ByteBuffer> it = content.iterator();
        assertTrue(it.hasNext());
        assertSame(foo, it.next());
        assertTrue(it.hasNext());
        assertSame(bar, it.next());
        assertFalse(it.hasNext());
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {

        }
    }

    @Test
    public void requireThatReadAfterFailedIsNull() {
        ReadableContentChannel content = new ReadableContentChannel();
        content.failed(new RuntimeException());
        assertNull(content.read());
        assertNull(content.read());
    }

    @Test
    public void requireThatReadCallsCompletion() {
        ReadableContentChannel content = new ReadableContentChannel();
        ByteBuffer buf = ByteBuffer.allocate(69);
        MyCompletion completion = new MyCompletion();
        content.write(buf, completion);
        assertFalse(completion.completed);
        assertSame(buf, content.read());
        assertTrue(completion.completed);

        completion = new MyCompletion();
        content.close(completion);
        assertFalse(completion.completed);
        assertNull(content.read());
        assertTrue(completion.completed);
    }

    @Test
    public void requireThatReadWaitsForWrite() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ReadableContentChannel content = new ReadableContentChannel();
        Future<ByteBuffer> readBuf = executor.submit(new ReadTask(content));
        try {
            readBuf.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }
        ByteBuffer buf = ByteBuffer.allocate(69);
        content.write(buf, null);
        assertSame(buf, readBuf.get(600, TimeUnit.SECONDS));
    }

    @Test
    public void requireThatCloseNotifiesRead() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ReadableContentChannel content = new ReadableContentChannel();
        Future<ByteBuffer> buf = executor.submit(new ReadTask(content));
        try {
            buf.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }
        content.close(null);
        assertNull(buf.get(600, TimeUnit.SECONDS));
    }

    @Test
    public void requireThatFailedNotifiesRead() throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        ReadableContentChannel content = new ReadableContentChannel();
        Future<ByteBuffer> buf = executor.submit(new ReadTask(content));
        try {
            buf.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }
        content.failed(new RuntimeException());
        assertNull(buf.get(600, TimeUnit.SECONDS));
    }

    @Test
    public void requireThatFailedCallsPendingCompletions() {
        MyCompletion foo = new MyCompletion();
        MyCompletion bar = new MyCompletion();
        ReadableContentChannel content = new ReadableContentChannel();
        content.write(ByteBuffer.allocate(69), foo);
        content.write(ByteBuffer.allocate(69), bar);
        RuntimeException e = new RuntimeException();
        content.failed(e);
        assertSame(e, foo.failed);
        assertSame(e, bar.failed);
    }

    @Test
    public void requireThatAvailableIsNotBlocking() {
        ReadableContentChannel content = new ReadableContentChannel();
        assertEquals(0, content.available());
        ByteBuffer buf = ByteBuffer.wrap(new byte[] { 6, 9 });
        content.write(buf, null);
        assertTrue(content.available() > 0);
        assertSame(buf, content.read());
        assertEquals(0, content.available());
        content.close(null);
        assertNull(content.read());
        assertEquals(0, content.available());
    }

    @Test
    public void requireThatContentIsThreadSafe() {
        ExecutorService executor = Executors.newFixedThreadPool(100);
        for (int run = 0; run < 69; ++run) {
            List<ByteBuffer> bufs = new LinkedList<>();
            for (int buf = 0; buf < 100; ++buf) {
                bufs.add(ByteBuffer.allocate(buf));
            }
            ReadableContentChannel content = new ReadableContentChannel();
            for (ByteBuffer buf : bufs) {
                executor.execute(new WriteTask(content, buf));
            }
            for (int buf = 0; buf < 100; ++buf) {
                assertTrue(bufs.remove(content.read()));
            }
            content.close(null);
            assertNull(content.read());
        }
    }

    private static class MyCompletion implements CompletionHandler {

        boolean completed = false;
        Throwable failed = null;

        @Override
        public void completed() {
            completed = true;
        }

        @Override
        public void failed(Throwable t) {
            failed = t;
        }
    }

    private static class ReadTask implements Callable<ByteBuffer> {

        final ReadableContentChannel content;

        ReadTask(ReadableContentChannel content) {
            this.content = content;
        }

        @Override
        public ByteBuffer call() throws Exception {
            return content.read();
        }
    }

    private static class WriteTask implements Runnable {

        final ContentChannel content;
        final ByteBuffer buf;

        WriteTask(ContentChannel content, ByteBuffer buf) {
            this.content = content;
            this.buf = buf;
        }

        @Override
        public void run() {
            content.write(buf, null);
        }
    }
}
