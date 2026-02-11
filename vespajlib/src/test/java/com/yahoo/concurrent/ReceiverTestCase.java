// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import static org.junit.Assert.*;

import org.junit.Test;

import com.yahoo.collections.Tuple2;

/**
 * Check for com.yahoo.concurrent.Receiver.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ReceiverTestCase {

    private static class Worker implements Runnable {
        private static final String HELLO_WORLD = "Hello, World!";
        private final Receiver<String> receiver;
        private final long timeToWait;

        Worker(Receiver<String> receiver, long timeToWait) {
            this.receiver = receiver;
            this.timeToWait = timeToWait;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(timeToWait);
            } catch (InterruptedException e) {
                fail("Test was interrupted.");
            }
            receiver.put(HELLO_WORLD);
        }
    }

    @Test
    public void testPut() throws InterruptedException {
        Receiver<String> receiver = new Receiver<>();
        Worker runnable = new Worker(receiver, 0);
        Thread worker = new Thread(runnable);
        worker.start();
        Tuple2<Receiver.MessageState, String> answer = receiver.get(1000L * 1000L * 1000L);
        assertEquals(Receiver.MessageState.VALID, answer.first);
        assertEquals(answer.second, Worker.HELLO_WORLD);
    }

    @Test
    public void testTimeOut() throws InterruptedException {
        Receiver<String> receiver = new Receiver<>();
        Worker runnable = new Worker(receiver, 1000L * 1000L * 1000L);
        Thread worker = new Thread(runnable);
        worker.start();
        Tuple2<Receiver.MessageState, String> answer = receiver.get(500L);
        assertEquals(Receiver.MessageState.TIMEOUT, answer.first);
    }

}
