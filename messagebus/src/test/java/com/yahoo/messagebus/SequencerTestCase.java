// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.messagebus.test.SimpleMessage;
import org.junit.jupiter.api.Test;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class SequencerTestCase {

    @Test
    void testSyncNone() {
        TestQueue src = new TestQueue();
        TestQueue dst = new TestQueue();
        QueueSender sender = new QueueSender(dst);
        Sequencer seq = new Sequencer(sender);

        seq.handleMessage(src.createMessage(false, 0));
        seq.handleMessage(src.createMessage(false, 0));
        seq.handleMessage(src.createMessage(false, 0));
        seq.handleMessage(src.createMessage(false, 0));
        seq.handleMessage(src.createMessage(false, 0));
        assertEquals(0, src.size());
        assertEquals(5, dst.size());

        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        assertEquals(5, src.size());
        assertEquals(0, dst.size());

        src.checkReply(false, 0);
        src.checkReply(false, 0);
        src.checkReply(false, 0);
        src.checkReply(false, 0);
        src.checkReply(false, 0);
        assertEquals(0, src.size());
        assertEquals(0, dst.size());
    }

    @Test
    void testSyncId() {
        TestQueue src = new TestQueue();
        TestQueue dst = new TestQueue();
        QueueSender sender = new QueueSender(dst);
        Sequencer seq = new Sequencer(sender);

        seq.handleMessage(src.createMessage(true, 1L));
        seq.handleMessage(src.createMessage(true, 2L));
        seq.handleMessage(src.createMessage(true, 3L));
        seq.handleMessage(src.createMessage(true, 4L));
        seq.handleMessage(src.createMessage(true, 5L));
        assertEquals(0, src.size());
        assertEquals(5, dst.size());

        seq.handleMessage(src.createMessage(true, 1L));
        seq.handleMessage(src.createMessage(true, 5L));
        seq.handleMessage(src.createMessage(true, 2L));
        seq.handleMessage(src.createMessage(true, 10L));
        seq.handleMessage(src.createMessage(true, 4L));
        seq.handleMessage(src.createMessage(true, 3L));
        assertEquals(0, src.size());
        assertEquals(6, dst.size());

        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        assertEquals(5, src.size());
        assertEquals(6, dst.size());

        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        dst.replyNext();
        assertEquals(11, src.size());
        assertEquals(0, dst.size());

        src.checkReply(true, 1);
        src.checkReply(true, 2);
        src.checkReply(true, 3);
        src.checkReply(true, 4);
        src.checkReply(true, 5);
        src.checkReply(true, 10);
        src.checkReply(true, 1);
        src.checkReply(true, 2);
        src.checkReply(true, 3);
        src.checkReply(true, 4);
        src.checkReply(true, 5);
        assertEquals(0, src.size());
        assertEquals(0, dst.size());
    }

    @Test
    void testRecursiveSending() throws InterruptedException {
        // This test queues up a lot of replies, and then has them all ready to return at once.
        int n = 10000;
        CountDownLatch latch = new CountDownLatch(n);
        CountDownLatch started = new CountDownLatch(1);
        AtomicReference<Reply> waiting = new AtomicReference<>();
        Executor executor = Executors.newSingleThreadExecutor();
        MessageHandler sender = message -> {
            Runnable task = () -> {
                Reply reply = new EmptyReply();
                reply.swapState(message);
                reply.setMessage(message);
                if (waiting.compareAndSet(null, reply)) started.countDown();
                else reply.popHandler().handleReply(reply);
            };
            if (Math.random() < 0.5) executor.execute(task); // Usually, RPC thread runs this.
            else task.run();                                 // But on, e.g., timeouts, it runs in the caller thread instead.
        };

        Queue<Message> answered = new ConcurrentLinkedQueue<>();
        ReplyHandler handler = reply -> {
            answered.add(reply.getMessage());
            latch.countDown();
        };

        Messenger messenger = new Messenger();
        messenger.start();
        Sequencer sequencer = new Sequencer(sender, messenger); // Not using the messenger results in a stack overflow error.

        Queue<Message> sent = new ConcurrentLinkedQueue<>();
        for (int i = 0; i < 10000; i++) {
            Message message = new MyMessage(true, 1);
            message.pushHandler(handler);
            sequencer.handleMessage(message);
            sent.add(message);
        }

        assertTrue(started.await(10, TimeUnit.SECONDS));
        waiting.get().popHandler().handleReply(waiting.get());
        assertTrue(latch.await(10, TimeUnit.SECONDS), "All messages should obtain a reply within 10s");
        assertEquals(Set.copyOf(sent), Set.copyOf(answered)); // Order is not guaranteed at all!
        messenger.destroy();
    }

    private static class TestQueue extends LinkedList<Routable> implements ReplyHandler {

        void checkReply(boolean hasSeqId, long seqId) {
            if (size() == 0) {
                throw new IllegalStateException("No routable in queue.");
            }
            Routable obj = remove();
            assertTrue(obj instanceof Reply);

            Reply reply = (Reply)obj;
            Message msg = reply.getMessage();
            assertNotNull(msg);

            assertEquals(hasSeqId, msg.hasSequenceId());
            if (hasSeqId) {
                assertEquals(seqId, msg.getSequenceId());
            }
        }

        public void handleReply(Reply reply) {
            add(reply);
        }

        void replyNext() {
            Routable obj = remove();
            assertTrue(obj instanceof Message);
            Message msg = (Message)obj;

            Reply reply = new EmptyReply();
            reply.swapState(msg);
            reply.setMessage(msg);
            ReplyHandler handler = reply.popHandler();
            handler.handleReply(reply);
        }

        Message createMessage(final boolean hasSeqId, final long seqId) {
            Message ret = new MyMessage(hasSeqId, seqId);
            ret.pushHandler(this);
            return ret;
        }
    }

    private static class QueueSender implements MessageHandler {

        Queue<Routable> queue;

        QueueSender(Queue<Routable> queue) {
            this.queue = queue;
        }

        @Override
        public void handleMessage(Message msg) {
            queue.offer(msg);
        }
    }

    private static class MyMessage extends SimpleMessage {

        final boolean hasSeqId;
        final long seqId;

        MyMessage(boolean hasSeqId, long seqId) {
            super("foo");
            this.hasSeqId = hasSeqId;
            this.seqId = seqId;
        }

        @Override
        public boolean hasSequenceId() {
            return hasSeqId;
        }

        @Override
        public long getSequenceId() {
            return seqId;
        }
    }

}

