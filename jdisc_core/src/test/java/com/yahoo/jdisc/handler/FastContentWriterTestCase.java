// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class FastContentWriterTestCase {

    @Test
    void requireThatContentCanBeWritten() throws ExecutionException, InterruptedException {
        ReadableContentChannel content = new ReadableContentChannel();
        FastContentWriter out = new FastContentWriter(content);

        ByteBuffer foo = ByteBuffer.allocate(69);
        out.write(foo);
        ByteBuffer bar = ByteBuffer.allocate(69);
        out.write(bar);
        out.close();

        assertFalse(out.isDone());
        assertSame(foo, content.read());
        assertFalse(out.isDone());
        assertSame(bar, content.read());
        assertFalse(out.isDone());
        assertNull(content.read());
        assertTrue(out.isDone());
    }

    @Test
    void requireThatStringsAreUtf8Encoded() {
        ReadableContentChannel content = new ReadableContentChannel();
        FastContentWriter out = new FastContentWriter(content);

        String in = "\u6211\u80FD\u541E\u4E0B\u73BB\u7483\u800C\u4E0D\u4F24\u8EAB\u4F53\u3002";
        out.write(in);
        out.close();

        ByteBuffer buf = content.read();
        byte[] arr = new byte[buf.remaining()];
        buf.get(arr);
        assertArrayEquals(in.getBytes(StandardCharsets.UTF_8), arr);
    }

    @Test
    void requireThatCancelThrowsUnsupportedOperation() {
        try {
            new FastContentWriter(Mockito.mock(ContentChannel.class)).cancel(true);
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    @Test
    void requireThatCancelIsAlwaysFalse() {
        FastContentWriter writer = new FastContentWriter(Mockito.mock(ContentChannel.class));
        assertFalse(writer.isCancelled());
        try {
            writer.cancel(true);
            fail();
        } catch (UnsupportedOperationException e) {

        }
        assertFalse(writer.isCancelled());
    }

    @Test
    void requireThatGetThrowsTimeoutUntilCloseCompletionHandlerIsCalled() throws Exception {
        ReadableContentChannel buf = new ReadableContentChannel();
        FastContentWriter out = new FastContentWriter(buf);

        out.write(new byte[]{6, 9});
        assertFalse(out.isDone());
        try {
            out.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }

        assertNotNull(buf.read());
        assertFalse(out.isDone());
        try {
            out.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }

        out.close();
        assertFalse(out.isDone());
        try {
            out.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }

        assertNull(buf.read());
        assertTrue(out.isDone());
        assertTrue(out.get(600, TimeUnit.SECONDS));
        assertTrue(out.get());
    }

    @Test
    void requireThatSyncWriteExceptionFailsFuture() throws InterruptedException {
        IllegalStateException expected = new IllegalStateException();
        ContentChannel content = Mockito.mock(ContentChannel.class);
        Mockito.doThrow(expected)
                .when(content).write(Mockito.any(ByteBuffer.class), Mockito.any(CompletionHandler.class));
        FastContentWriter out = new FastContentWriter(content);
        try {
            out.write("foo");
            fail();
        } catch (Throwable t) {
            assertSame(expected, t);
        }
        try {
            out.get();
            fail();
        } catch (ExecutionException e) {
            assertSame(expected, e.getCause());
        }
    }

    @Test
    void requireThatSyncCloseExceptionFailsFuture() throws InterruptedException {
        IllegalStateException expected = new IllegalStateException();
        ContentChannel content = Mockito.mock(ContentChannel.class);
        Mockito.doThrow(expected)
                .when(content).close(Mockito.any(CompletionHandler.class));
        FastContentWriter out = new FastContentWriter(content);
        try {
            out.close();
            fail();
        } catch (Throwable t) {
            assertSame(expected, t);
        }
        try {
            out.get();
            fail();
        } catch (ExecutionException e) {
            assertSame(expected, e.getCause());
        }
    }

    @Test
    void requireThatAsyncExceptionFailsFuture() throws InterruptedException {
        IllegalStateException expected = new IllegalStateException();
        ReadableContentChannel content = new ReadableContentChannel();
        FastContentWriter out = new FastContentWriter(content);
        out.write("foo");
        content.failed(expected);
        try {
            out.get();
            fail();
        } catch (ExecutionException e) {
            assertSame(expected, e.getCause());
        }
    }

    @Test
    void requireThatWriterCanBeListenedTo() throws InterruptedException {
        ReadableContentChannel buf = new ReadableContentChannel();
        FastContentWriter out = new FastContentWriter(buf);
        RunnableLatch listener = new RunnableLatch();
        out.addListener(listener, Runnable::run);

        out.write(new byte[]{6, 9});
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        assertNotNull(buf.read());
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        out.close();
        assertFalse(listener.await(100, TimeUnit.MILLISECONDS));
        assertNull(buf.read());
        assertTrue(listener.await(600, TimeUnit.SECONDS));
    }

    @Test
    void requireThatWriterIsThreadSafe() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final ReadableContentChannel content = new ReadableContentChannel();
        Future<Integer> read = Executors.newSingleThreadExecutor().submit(new Callable<Integer>() {

            @Override
            public Integer call() throws Exception {
                latch.countDown();
                latch.await(600, TimeUnit.SECONDS);

                int bufCnt = 0;
                while (content.read() != null) {
                    ++bufCnt;
                }
                return bufCnt;
            }
        });
        Future<Integer> write = Executors.newSingleThreadExecutor().submit(new Callable<Integer>() {

            @Override
            public Integer call() throws Exception {
                FastContentWriter out = new FastContentWriter(content);
                ByteBuffer buf = ByteBuffer.wrap(new byte[69]);
                int bufCnt = 4096 + new Random().nextInt(4096);

                latch.countDown();
                latch.await(600, TimeUnit.SECONDS);
                for (int i = 0; i < bufCnt; ++i) {
                    out.write(buf.slice());
                }
                out.close();
                return bufCnt;
            }
        });
        assertEquals(read.get(600, TimeUnit.SECONDS),
                write.get(600, TimeUnit.SECONDS));
    }
}
