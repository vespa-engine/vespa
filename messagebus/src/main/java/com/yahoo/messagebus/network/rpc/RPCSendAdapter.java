// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.network.rpc;

import com.yahoo.component.Version;
import com.yahoo.messagebus.routing.RoutingNode;

/**
 * This interface defines the necessary methods to process incoming and send outgoing RPC requests. The {@link
 * RPCNetwork} maintains a list of supported RPC signatures, and dispatches requests to the corresponding adapter.
 *
 * @author Simon Thoresen Hult
 */
public interface RPCSendAdapter {

    /**
     * Attaches this adapter to the given network.
     *
     * @param net The network to attach to.
     */
    public void attach(RPCNetwork net);

    /**
     * Performs the actual sending to the given recipient.
     *
     * @param recipient     The recipient to send to.
     * @param version       The version for which the payload is serialized.
     * @param payload       The already serialized payload of the message to send.
     * @param timeRemaining The time remaining until the message expires.
     */
    public void send(RoutingNode recipient, Version version, byte[] payload, long timeRemaining);
}
