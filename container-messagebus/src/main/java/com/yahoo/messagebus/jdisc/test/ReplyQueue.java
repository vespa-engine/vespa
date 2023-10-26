// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc.test;

import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class ReplyQueue implements ReplyHandler {

    private final BlockingQueue<Reply> queue = new LinkedBlockingQueue<>();

    @Override
    public void handleReply(Reply reply) {
        queue.add(reply);
    }

    public Reply awaitReply(int timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }
}
