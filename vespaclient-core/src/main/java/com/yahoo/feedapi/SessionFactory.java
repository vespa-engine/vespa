// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.feedapi;

import com.yahoo.messagebus.ReplyHandler;

/**
 * Wraps the creation of messagebus source sessions to allow
 * for unit testing of the components without involving messagebus itself.
 */
public interface SessionFactory {

    /**
     * Creates a messagebus session for sending regular messages.
     *
     *
     * @param handler A replyhandler to callback when receiving replies from messagebus
     * @return The session to use for sending messages.
     */
    SendSession createSendSession(ReplyHandler handler);
}
