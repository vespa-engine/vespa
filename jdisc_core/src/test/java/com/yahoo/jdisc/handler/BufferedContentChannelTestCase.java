// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class BufferedContentChannelTestCase {

    @Test
    void requireThatIsConnectedWorks() {
        MyContent target = new MyContent();
        BufferedContentChannel content = new BufferedContentChannel();
        assertFalse(content.isConnected());
        content.connectTo(target);
        assertTrue(content.isConnected());
    }

    @Test
    void requireThatConnectToNullThrowsException() {
        BufferedContentChannel content = new BufferedContentChannel();
        try {
            content.connectTo(null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    void requireThatWriteAfterCloseThrowsException() {
        BufferedContentChannel content = new BufferedContentChannel();
        content.close(null);
        try {
            content.write(ByteBuffer.allocate(69), null);
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    void requireThatCloseAfterCloseThrowsException() {
        BufferedContentChannel content = new BufferedContentChannel();
        content.close(null);
        try {
            content.close(null);
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    void requireThatConnecToAfterConnecToThrowsException() {
        BufferedContentChannel content = new BufferedContentChannel();
        content.connectTo(new MyContent());
        try {
            content.connectTo(new MyContent());
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    void requireThatWriteBeforeConnectToWritesToTarget() {
        BufferedContentChannel content = new BufferedContentChannel();
        ByteBuffer buf = ByteBuffer.allocate(69);
        MyCompletion completion = new MyCompletion();
        content.write(buf, completion);
        MyContent target = new MyContent();
        content.connectTo(target);
        assertSame(buf, target.writeBuf);
        assertSame(completion, target.writeCompletion);
    }

    @Test
    void requireThatWriteAfterConnectToWritesToTarget() {
        MyContent target = new MyContent();
        BufferedContentChannel content = new BufferedContentChannel();
        content.connectTo(target);
        ByteBuffer buf = ByteBuffer.allocate(69);
        MyCompletion completion = new MyCompletion();
        content.write(buf, completion);
        assertSame(buf, target.writeBuf);
        assertSame(completion, target.writeCompletion);
    }

    @Test
    void requireThatCloseBeforeConnectToClosesTarget() {
        BufferedContentChannel content = new BufferedContentChannel();
        MyCompletion completion = new MyCompletion();
        content.close(completion);
        MyContent target = new MyContent();
        content.connectTo(target);
        assertTrue(target.closed);
        assertSame(completion, target.closeCompletion);
    }

    @Test
    void requireThatCloseAfterConnectToClosesTarget() {
        MyContent target = new MyContent();
        BufferedContentChannel content = new BufferedContentChannel();
        content.connectTo(target);
        MyCompletion completion = new MyCompletion();
        content.close(completion);
        assertTrue(target.closed);
        assertSame(completion, target.closeCompletion);
    }

    @Test
    void requireThatIsConnectedIsTrueWhenConnectedBeforeClose() {
        BufferedContentChannel content = new BufferedContentChannel();
        assertFalse(content.isConnected());
        content.connectTo(new MyContent());
        assertTrue(content.isConnected());
        content.close(null);
        assertTrue(content.isConnected());
    }

    @Test
    void requireThatIsConnectedIsTrueWhenClosedBeforeConnected() {
        BufferedContentChannel content = new BufferedContentChannel();
        assertFalse(content.isConnected());
        content.close(null);
        assertFalse(content.isConnected());
        content.connectTo(new MyContent());
        assertTrue(content.isConnected());
    }

    @Test
    void requireThatContentIsThreadSafe() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(101);
        for (int run = 0; run < 69; ++run) {
            List<ByteBuffer> bufs = new LinkedList<>();
            for (int buf = 0; buf < 100; ++buf) {
                bufs.add(ByteBuffer.allocate(buf));
            }
            BufferedContentChannel content = new BufferedContentChannel();
            List<Callable<Boolean>> tasks = new LinkedList<>();
            for (ByteBuffer buf : bufs) {
                tasks.add(new WriteTask(content, buf));
            }
            MyConcurrentContent target = new MyConcurrentContent();
            tasks.add(new ConnectTask(content, target));
            List<Future<Boolean>> results = executor.invokeAll(tasks);
            for (Future<Boolean> result : results) {
                assertTrue(result.get());
            }
            assertEquals(bufs.size(), target.bufs.size());
            for (ByteBuffer buf : target.bufs) {
                assertTrue(bufs.remove(buf));
            }
            assertTrue(bufs.isEmpty());
        }
    }

    private static class WriteTask implements Callable<Boolean> {

        final Random rnd = new Random();
        final BufferedContentChannel content;
        final ByteBuffer buf;

        WriteTask(BufferedContentChannel content, ByteBuffer buf) {
            this.content = content;
            this.buf = buf;
        }

        @Override
        public Boolean call() throws Exception {
            if (rnd.nextBoolean()) {
                Thread.sleep(rnd.nextInt(5));
            }
            content.write(buf, null);
            return Boolean.TRUE;
        }
    }

    private static class ConnectTask implements Callable<Boolean> {

        final BufferedContentChannel content;
        final ContentChannel target;

        ConnectTask(BufferedContentChannel content, ContentChannel target) {
            this.content = content;
            this.target = target;
        }

        @Override
        public Boolean call() throws Exception {
            content.connectTo(target);
            return Boolean.TRUE;
        }
    }

    private static class MyContent implements ContentChannel {

        ByteBuffer writeBuf = null;
        CompletionHandler writeCompletion;
        CompletionHandler closeCompletion;
        boolean closed = false;

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            writeBuf = buf;
            writeCompletion = handler;
        }

        @Override
        public void close(CompletionHandler handler) {
            closeCompletion = handler;
            closed = true;
        }
    }

    private static class MyConcurrentContent implements ContentChannel {

        ConcurrentLinkedQueue<ByteBuffer> bufs = new ConcurrentLinkedQueue<>();

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            bufs.add(buf);
        }

        @Override
        public void close(CompletionHandler handler) {

        }
    }

    private static class MyCompletion implements CompletionHandler {

        @Override
        public void completed() {

        }

        @Override
        public void failed(Throwable throwable) {

        }
    }
}
