// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.text.Utf8String;

/**
 * The empty reply is the only concrete implementation of a message that is offered by the MessageBus. It is used to
 * generate replies to events that occur within the messagebus, and since the messagebus by design knows nothing about
 * the messages that have been implemented by the users it requires a class such as this.
 *
 * @author Simon Thoresen Hult
 */
public final class EmptyReply extends Reply {

    private final Utf8String PROTOCOL = new Utf8String("");

    /**
     * Implements the getType() function of the root class Routable to identify this reply as the reserved type '0'.
     *
     * @return The number '0'.
     */
    public int getType() {
        return 0;
    }

    /**
     * Implements the getProtocol() function of Routable to identify this reply as the reserved type. This is done by an
     * empty string.
     *
     * @return The string "".
     */
    public Utf8String getProtocol() {
        return PROTOCOL;
    }

}
