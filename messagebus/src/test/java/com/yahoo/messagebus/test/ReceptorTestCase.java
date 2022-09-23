// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.test;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.Reply;
import com.yahoo.text.Utf8String;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Simon Thoresen Hult
 */
public class ReceptorTestCase {

    @Test
    void requireThatAccessorsWork() {
        Receptor receptor = new Receptor();
        assertNull(receptor.getMessage(0));
        Message msg = new MyMessage();
        receptor.handleMessage(msg);
        assertSame(msg, receptor.getMessage(0));

        Reply reply = new MyReply();
        receptor.handleReply(reply);
        assertSame(reply, receptor.getReply(0));
    }

    @Test
    void requireThatMessagesAndRepliesAreTrackedIndividually() {
        Receptor receptor = new Receptor();
        receptor.handleMessage(new MyMessage());
        receptor.handleReply(new MyReply());
        assertNotNull(receptor.getMessage(0));
        assertNotNull(receptor.getReply(0));

        receptor.handleMessage(new MyMessage());
        receptor.handleReply(new MyReply());
        assertNotNull(receptor.getReply(0));
        assertNotNull(receptor.getMessage(0));
    }

    @Test
    void requireThatMessagesCanBeWaitedFor() {
        final Receptor receptor = new Receptor();
        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    receptor.handleMessage(new MyMessage());
                } catch (InterruptedException e) {

                }
            }
        };
        thread.start();
        assertNotNull(receptor.getMessage(60));
    }

    @Test
    void requireThatMessageWaitCanBeInterrupted() throws InterruptedException {
        final Receptor receptor = new Receptor();
        final CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread() {

            @Override
            public void run() {
                receptor.getMessage(60);
                latch.countDown();
            }
        };
        thread.start();
        thread.interrupt();
        assertTrue(latch.await(30, TimeUnit.SECONDS));
    }

    @Test
    void requireThatRepliesCanBeWaitedFor() {
        final Receptor receptor = new Receptor();
        Thread thread = new Thread() {

            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                    receptor.handleReply(new MyReply());
                } catch (InterruptedException e) {

                }
            }
        };
        thread.start();
        assertNotNull(receptor.getReply(60));
    }

    @Test
    void requireThatReplyWaitCanBeInterrupted() throws InterruptedException {
        final Receptor receptor = new Receptor();
        final CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread() {

            @Override
            public void run() {
                receptor.getReply(60);
                latch.countDown();
            }
        };
        thread.start();
        thread.interrupt();
        assertTrue(latch.await(30, TimeUnit.SECONDS));
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
