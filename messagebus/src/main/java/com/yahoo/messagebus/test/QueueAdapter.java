// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.test;

import com.yahoo.messagebus.*;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 */
public class QueueAdapter implements MessageHandler, ReplyHandler {

    private final Queue<Routable> queue = new ConcurrentLinkedQueue<>();

    @Override
    public void handleMessage(Message message) {
        queue.offer(message);
    }

    @Override
    public void handleReply(Reply reply) {
        queue.offer(reply);
    }

    public Routable dequeue() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public int size() {
        return queue.size();
    }

    public boolean waitSize(int size, int seconds) {
        long timeout = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(seconds);
        while (true) {
            if (size() == size) {
                return true;
            }
            if (System.currentTimeMillis() > timeout) {
                return false;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
