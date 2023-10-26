// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
     * @param message      the message to evaluate
     * @param pendingCount the current number of pending messages
     * @return true to send the message
     */
    boolean canSend(Message message, int pendingCount);

    /**
     * This method is called once for every message that was accepted by {@link #canSend(Message, int)} and sent.
     *
     * @param message the message being sent
     */
    void processMessage(Message message);

    /**
     * This method is called once for every reply that is received.
     *
     * @param reply the reply received
     */
    void processReply(Reply reply);

}
