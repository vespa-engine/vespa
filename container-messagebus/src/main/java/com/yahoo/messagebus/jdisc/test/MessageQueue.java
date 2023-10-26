// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc.test;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class MessageQueue implements MessageHandler {

    private final BlockingQueue<Message> queue = new LinkedBlockingQueue<>();

    @Override
    public void handleMessage(Message msg) {
        queue.add(msg);
    }

    public Message awaitMessage(int timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

}
