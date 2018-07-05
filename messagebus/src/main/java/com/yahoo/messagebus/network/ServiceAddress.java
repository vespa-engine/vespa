// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network;

/**
 * This interface represents an abstract network service; i.e. somewhere to send messages. An instance of this is
 * retrieved by calling {@link Network#allocServiceAddress(com.yahoo.messagebus.routing.RoutingNode)}.
 *
 * @author Simon Thoresen Hult
 */
public interface ServiceAddress {
    // empty
}


