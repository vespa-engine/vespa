// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import com.yahoo.collections.Tuple2;

/**
 * A class for sending single messages between threads with timeout. Typical use
 * would be
 *
 * <pre>
 * Receiver&lt;SomeMessage&gt; receiver = new Receiver&lt;SomeMessage&gt;();
 * SomeRunnable runnable = new SomeRunnable(receiver);
 * Thread worker = new Thread(runnable);
 * worker.start();
 * Pair&lt;Receiver.MessageState, SomeMessage&gt; answer = receiver.get(500L);
 * </pre>
 *
 * ... and in the worker thread simply
 *
 * <pre>
 * receiver.put(new SomeMessage(...))
 * </pre>
 *
 * <p>
 * Any number of threads may wait for the same message. Sending null references
 * is supported. The object is intended for delivering only single message,
 * there is no support for recycling it.
 * </p>
 *
 * @author Steinar Knutsen
 */
public class Receiver<T> {
    /**
     * MessageState is the reason for returning from get(). If a message is
     * received before timeout, the state will be VALID. If no message is
     * received before timeout, state is TIMEOUT.
     */
    public enum MessageState {
        VALID, TIMEOUT;
    };
    private final Object lock = new Object();
    private T message = null;
    private boolean received = false;

    /**
     * Make a message available for consumers.
     *
     * @param message the message to send
     * @throws IllegalStateException if a message has already been received here
     */
    public void put(T message) {
        synchronized (lock) {
            if (received) {
                throw new IllegalStateException("Multiple puts on a single Receiver instance is not legal.");
            }
            this.message = message;
            received = true;
            lock.notifyAll();
        }
    }

    /**
     * Wait for up to "timeout" milliseconds for an incoming message. This hides
     * spurious wakeup, but InterruptedException will be propagated.
     *
     * @param timeout
     *            maximum time to wait for message in milliseconds
     * @return a Pair instance containing the reason for returning and the
     *         message possible received
     * @throws InterruptedException if the waiting thread is interrupted
     */
    public Tuple2<MessageState, T> get(long timeout) throws InterruptedException {
        long barrier = System.currentTimeMillis() + timeout;
        synchronized (lock) {
            while (!received) {
                long t = System.currentTimeMillis();
                if (t >= barrier) {
                    return new Tuple2<>(MessageState.TIMEOUT, null);
                }
                lock.wait(barrier - t);
            }
            return new Tuple2<>(MessageState.VALID, message);
        }
    }

}
