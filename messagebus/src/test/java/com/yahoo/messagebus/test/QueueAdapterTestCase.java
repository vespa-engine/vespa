// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.test;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.text.Utf8String;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class QueueAdapterTestCase {

    private static final int NO_WAIT = 0;
    private static final int WAIT_FOREVER = 60;

    @Test
    void requireThatAccessorsWork() {
        QueueAdapter queue = new QueueAdapter();
        assertTrue(queue.isEmpty());
        assertEquals(0, queue.size());

        Message msg = new MyMessage();
        queue.handleMessage(msg);
        assertFalse(queue.isEmpty());
        assertEquals(1, queue.size());

        MyReply reply = new MyReply();
        queue.handleReply(reply);
        assertFalse(queue.isEmpty());
        assertEquals(2, queue.size());

        assertSame(msg, queue.dequeue());
        assertSame(reply, queue.dequeue());
    }

    @Test
    void requireThatSizeCanBeWaitedFor() {
        final QueueAdapter queue = new QueueAdapter();
        assertTrue(queue.waitSize(0, NO_WAIT));
        assertFalse(queue.waitSize(1, NO_WAIT));
        queue.handleMessage(new MyMessage());
        assertFalse(queue.waitSize(0, NO_WAIT));
        assertTrue(queue.waitSize(1, NO_WAIT));

        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    queue.handleMessage(new MyMessage());
                } catch (InterruptedException e) {

                }
            }
        };
        thread.start();
        assertTrue(queue.waitSize(2, WAIT_FOREVER));
    }

    @Test
    void requireThatWaitCanBeInterrupted() throws InterruptedException {
        final QueueAdapter queue = new QueueAdapter();
        final AtomicReference<Boolean> result = new AtomicReference<>();
        Thread thread = new Thread() {

            @Override
            public void run() {
                result.set(queue.waitSize(1, WAIT_FOREVER));
            }
        };
        thread.start();
        thread.interrupt();
        thread.join();
        assertEquals(Boolean.FALSE, result.get());
    }

    private static class MyMessage extends Message {

        @Override
        public Utf8String getProtocol() {
            return null;
        }

        @Override
        public int getType() {
            return 0;
        }
    }

    private static class MyReply extends Reply {

        @Override
        public Utf8String getProtocol() {
            return null;
        }

        @Override
        public int getType() {
            return 0;
        }
    }

}
