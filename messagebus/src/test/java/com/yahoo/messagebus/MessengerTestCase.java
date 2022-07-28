// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class MessengerTestCase {

    @Test
    void requireThatSyncWithSelfDoesNotCauseDeadLock() throws InterruptedException {
        final Messenger msn = new Messenger();
        msn.start();

        final CountDownLatch latch = new CountDownLatch(1);
        msn.enqueue(new Messenger.Task() {

            @Override
            public void run() {
                msn.sync();
            }

            @Override
            public void destroy() {
                latch.countDown();
            }
        });
        assertTrue(latch.await(60, TimeUnit.SECONDS));
    }

    @Test
    void requireThatTaskIsExecuted() throws InterruptedException {
        Messenger msn = new Messenger();
        msn.start();
        assertTrue(tryMessenger(msn));
    }

    @Test
    void requireThatRunExceptionIsCaught() throws InterruptedException {
        Messenger msn = new Messenger();
        msn.start();
        msn.enqueue(new Messenger.Task() {
            @Override
            public void run() {
                throw new RuntimeException();
            }

            @Override
            public void destroy() {

            }
        });
        assertTrue(tryMessenger(msn));
    }

    @Test
    void requireThatDestroyExceptionIsCaught() throws InterruptedException {
        Messenger msn = new Messenger();
        msn.start();
        msn.enqueue(new Messenger.Task() {
            @Override
            public void run() {

            }

            @Override
            public void destroy() {
                throw new RuntimeException();
            }
        });
        assertTrue(tryMessenger(msn));
    }

    @Test
    void requireThatRunAndDestroyExceptionsAreCaught() throws InterruptedException {
        Messenger msn = new Messenger();
        msn.start();
        msn.enqueue(new Messenger.Task() {
            @Override
            public void run() {
                throw new RuntimeException();
            }

            @Override
            public void destroy() {
                throw new RuntimeException();
            }
        });
        assertTrue(tryMessenger(msn));
    }

    private static boolean tryMessenger(Messenger msn) {
        MyTask task = new MyTask();
        msn.enqueue(task);
        try {
            return task.runLatch.await(60, TimeUnit.SECONDS) &&
                   task.destroyLatch.await(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    private static class MyTask implements Messenger.Task {

        final CountDownLatch runLatch = new CountDownLatch(1);
        final CountDownLatch destroyLatch = new CountDownLatch(1);

        @Override
        public void run() {
            runLatch.countDown();
        }

        @Override
        public void destroy() {
            destroyLatch.countDown();
        }
    }
}
