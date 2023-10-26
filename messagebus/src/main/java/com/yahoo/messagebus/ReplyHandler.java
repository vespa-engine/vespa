// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

/**
 * All classes that wants to handle replies that move through the messagebus need to implement this interface. As
 * opposed to the {@link MessageHandler} which handles messages as they travel from the sender to the receiver, this
 * interface is intended for handling replies as they return from the receiver to the sender.
 *
 * @author Simon Thoresen Hult
 */
public interface ReplyHandler {

    /**
     * This function is called when a reply arrives.
     *
     * @param reply The reply that arrived.
     */
    void handleReply(Reply reply);

}
