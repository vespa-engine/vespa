// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.test;

import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:havardpe@yahoo-inc.com">Haavard Pettersen</a>
 */
public class Receptor implements MessageHandler, ReplyHandler {

    private final BlockingQueue<Message> msg = new LinkedBlockingQueue<>();
    private final BlockingQueue<Reply> reply = new LinkedBlockingQueue<>();

    public void reset() {
        msg.clear();
        reply.clear();
    }

    public void handleMessage(Message msg) {
        this.msg.add(msg);
    }

    public void handleReply(Reply reply) {
        this.reply.add(reply);
    }

    public Message getMessage(int seconds) {
        try {
            return msg.poll(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public Reply getReply(int seconds) {
        try {
            return reply.poll(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}
