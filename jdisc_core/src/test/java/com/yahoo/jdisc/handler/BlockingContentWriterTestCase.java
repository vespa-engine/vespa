// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author Simon Thoresen Hult
 */
public class BlockingContentWriterTestCase {

    @Test
    void requireThatContentChannelIsNotNull() {
        try {
            new BlockingContentWriter(null);
            fail();
        } catch (NullPointerException e) {

        }
    }

    @Test
    void requireThatWriteDeliversBuffer() throws InterruptedException {
        MyContent content = MyContent.newNonBlockingContent();
        BlockingContentWriter writer = new BlockingContentWriter(content);
        ByteBuffer buf = ByteBuffer.allocate(69);
        writer.write(buf);
        assertSame(buf, content.writeBuf);
    }

    @Test
    void requireThatWriteIsBlocking() throws Exception {
        MyContent content = MyContent.newBlockingContent();
        BlockingContentWriter writer = new BlockingContentWriter(content);
        FutureTask<Boolean> task = new FutureTask<>(new WriteTask(writer, ByteBuffer.allocate(69)));
        Executors.newSingleThreadExecutor().submit(task);
        content.writeLatch.await(600, TimeUnit.SECONDS);
        try {
            task.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }
        content.writeCompletion.completed();
        assertTrue(task.get(600, TimeUnit.SECONDS));
    }

    @Test
    void requireThatWriteExceptionIsThrown() throws Exception {
        Throwable throwMe = new RuntimeException();
        try {
            new BlockingContentWriter(MyContent.newFailedContent(throwMe)).write(ByteBuffer.allocate(69));
        } catch (Throwable t) {
            assertSame(throwMe, t);
        }
        throwMe = new Error();
        try {
            new BlockingContentWriter(MyContent.newFailedContent(throwMe)).write(ByteBuffer.allocate(69));
        } catch (Throwable t) {
            assertSame(throwMe, t);
        }
        throwMe = new Exception();
        try {
            new BlockingContentWriter(MyContent.newFailedContent(throwMe)).write(ByteBuffer.allocate(69));
        } catch (Throwable t) {
            assertNotSame(throwMe, t);
            assertSame(throwMe, t.getCause());
        }
    }

    @Test
    void requireThatCloseIsBlocking() throws Exception {
        MyContent content = MyContent.newBlockingContent();
        BlockingContentWriter writer = new BlockingContentWriter(content);
        FutureTask<Boolean> task = new FutureTask<>(new CloseTask(writer));
        Executors.newSingleThreadExecutor().submit(task);
        content.closeLatch.await(600, TimeUnit.SECONDS);
        try {
            task.get(100, TimeUnit.MILLISECONDS);
            fail();
        } catch (TimeoutException e) {

        }
        content.closeCompletion.completed();
        assertTrue(task.get(600, TimeUnit.SECONDS));
    }

    @Test
    void requireThatCloseExceptionIsThrown() throws Exception {
        Throwable throwMe = new RuntimeException();
        try {
            new BlockingContentWriter(MyContent.newFailedContent(throwMe)).close();
        } catch (Throwable t) {
            assertSame(throwMe, t);
        }
        throwMe = new Error();
        try {
            new BlockingContentWriter(MyContent.newFailedContent(throwMe)).close();
        } catch (Throwable t) {
            assertSame(throwMe, t);
        }
        throwMe = new Exception();
        try {
            new BlockingContentWriter(MyContent.newFailedContent(throwMe)).close();
        } catch (Throwable t) {
            assertNotSame(throwMe, t);
            assertSame(throwMe, t.getCause());
        }
    }

    private static class MyContent implements ContentChannel {

        final CountDownLatch writeLatch = new CountDownLatch(1);
        final CountDownLatch closeLatch = new CountDownLatch(1);
        final Throwable eagerFailure;
        final boolean eagerCompletion;
        CompletionHandler writeCompletion;
        CompletionHandler closeCompletion;
        ByteBuffer writeBuf;

        MyContent(boolean eagerCompletion, Throwable eagerFailure) {
            this.eagerCompletion = eagerCompletion;
            this.eagerFailure = eagerFailure;
        }

        @Override
        public void write(ByteBuffer buf, CompletionHandler handler) {
            writeBuf = buf;
            if (eagerFailure != null) {
                handler.failed(eagerFailure);
            } else if (eagerCompletion) {
                handler.completed();
            } else {
                writeCompletion = handler;
            }
            writeLatch.countDown();
        }

        @Override
        public void close(CompletionHandler handler) {
            if (eagerFailure != null) {
                handler.failed(eagerFailure);
            } else if (eagerCompletion) {
                handler.completed();
            } else {
                closeCompletion = handler;
            }
            closeLatch.countDown();
        }

        static MyContent newBlockingContent() {
            return new MyContent(false, null);
        }

        static MyContent newNonBlockingContent() {
            return new MyContent(true, null);
        }

        static MyContent newFailedContent(Throwable e) {
            return new MyContent(false, e);
        }
    }

    private static class WriteTask implements Callable<Boolean> {

        final BlockingContentWriter writer;
        final ByteBuffer buf;

        WriteTask(BlockingContentWriter writer, ByteBuffer buf) {
            this.writer = writer;
            this.buf = buf;
        }

        @Override
        public Boolean call() {
            try {
                writer.write(buf);
            } catch (Throwable t) {
                return false;
            }
            return true;
        }
    }

    private static class CloseTask implements Callable<Boolean> {

        final BlockingContentWriter writer;

        CloseTask(BlockingContentWriter writer) {
            this.writer = writer;
        }

        @Override
        public Boolean call() {
            try {
                writer.close();
            } catch (Throwable t) {
                return false;
            }
            return true;
        }
    }
}
