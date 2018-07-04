// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * An implementation of this interface is used by {@link SourceSession} to throttle output. Every message entering
 * {@link SourceSession#send(Message)} needs to be accepted by this interface's {@link #canSend(Message, int)} method.
 * All messages accepted are passed through the {@link #processMessage(Message)} method, and the corresponding replies
 * are passed through the {@link #processReply(Reply)} method.
 *
 * @author Simon Thoresen Hult
 */
public interface ThrottlePolicy {

    /**
     * Returns whether or not the given message can be sent according to the current state of this policy.
     *
     * @param msg          The message to evaluate.
     * @param pendingCount The current number of pending messages.
     * @return True to send the message.
     */
    public boolean canSend(Message msg, int pendingCount);

    /**
     * This method is called once for every message that was accepted by {@link #canSend(Message, int)} and sent.
     *
     * @param msg The message beint sent.
     */
    public void processMessage(Message msg);

    /**
     * This method is called once for every reply that is received.
     *
     * @param reply The reply received.
     */
    public void processReply(Reply reply);
}
