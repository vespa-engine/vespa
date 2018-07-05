// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Sequencing is implemented as a message handler that is configured in a source session in that session's chain of
 * linked message handlers. Each message that carries a sequencing id is queued in an internal list of messages for that
 * id, and messages are only sent when they are at the front of their list. When a reply arrives, the current front of
 * the list is removed and the next message, if any, is sent.
 *
 * @author Simon Thoresen Hult
 */
public class Sequencer implements MessageHandler, ReplyHandler {

    private final AtomicBoolean destroyed = new AtomicBoolean(false);
    private final MessageHandler sender;
    private final Map<Long, Queue<Message>> seqMap = new HashMap<Long, Queue<Message>>();

    /**
     * Constructs a new sequencer on top of the given async sender.
     *
     * @param sender The underlying sender.
     */
    public Sequencer(MessageHandler sender) {
        this.sender = sender;
    }

    /**
     * Sets the destroyed flag to true. The very first time this method is called, it cleans up all its dependencies.
     * Even if you retain a reference to this object, all of its content is allowed to be garbage collected.
     *
     * @return True if content existed and was destroyed.
     */
    public boolean destroy() {
        if (!destroyed.getAndSet(true)) {
            synchronized (this) {
                for (Queue<Message> queue : seqMap.values()) {
                    if (queue != null) {
                        for (Message msg : queue) {
                            msg.discard();
                        }
                    }
                }
                seqMap.clear();
            }
            return true;
        }
        return false;
    }

    /**
     * Filter a message against the current sequencing state. If this method returns true, the message has been cleared
     * for sending and its sequencing information has been added to the state. If this method returns false, it has been
     * queued for later sending due to sequencing restrictions. This method also sets the sequence id as message
     * context.
     *
     * @param msg The message to filter.
     * @return True if the message was consumed.
     */
    private boolean filter(Message msg) {
        long seqId = msg.getSequenceId();
        msg.setContext(seqId);
        synchronized (this) {
            if (seqMap.containsKey(seqId)) {
                Queue<Message> queue = seqMap.get(seqId);
                if (queue == null) {
                    queue = new LinkedList<Message>();
                    seqMap.put(seqId, queue);
                }
                if (msg.getTrace().shouldTrace(TraceLevel.COMPONENT)) {
                    msg.getTrace().trace(TraceLevel.COMPONENT,
                                         "Sequencer queued message with sequence id '" + seqId + "'.");
                }
                queue.add(msg);
                return false;
            }
            seqMap.put(seqId, null);
        }
        return true;
    }

    /**
     * Internal method for forwarding a sequenced message to the underlying sender.
     *
     * @param msg The message to forward.
     */
    private void sequencedSend(Message msg) {
        if (msg.getTrace().shouldTrace(TraceLevel.COMPONENT)) {
            msg.getTrace().trace(TraceLevel.COMPONENT,
                                 "Sequencer sending message with sequence id '" + msg.getContext() + "'.");
        }
        msg.pushHandler(this);
        sender.handleMessage(msg);
    }

    /**
     * All messages pass through this handler when being sent by the owning source session. In case the message has no
     * sequencing-id, it is simply passed through to the next handler in the chain. Sequenced messages are sent only if
     * there is no queue for their id, otherwise they are queued.
     *
     * @param msg The message to send.
     */
    @Override
    public void handleMessage(Message msg) {
        if (destroyed.get()) {
            msg.discard();
            return;
        }
        if (msg.hasSequenceId()) {
            if (filter(msg)) {
                sequencedSend(msg);
            }
        } else {
            sender.handleMessage(msg); // unsequenced
        }
    }

    /**
     * Lookup the sequencing id of an incoming reply to pop the front of the corresponding queue, and then send the next
     * message in line, if any.
     *
     * @param reply The reply received.
     */
    @Override
    public void handleReply(Reply reply) {
        if (destroyed.get()) {
            reply.discard();
            return;
        }
        long seqId = (Long)reply.getContext(); // non-sequenced messages do not enter here
        if (reply.getTrace().shouldTrace(TraceLevel.COMPONENT)) {
            reply.getTrace().trace(TraceLevel.COMPONENT,
                                   "Sequencer received reply with sequence id '" + seqId + "'.");
        }
        Message msg = null;
        synchronized (this) {
            Queue<Message> queue = seqMap.get(seqId);
            if (queue == null || queue.isEmpty()) {
                seqMap.remove(seqId);
            } else {
                msg = queue.remove();
            }
        }
        if (msg != null) {
            sequencedSend(msg);
        }
        ReplyHandler handler = reply.popHandler();
        handler.handleReply(reply);
    }
}
