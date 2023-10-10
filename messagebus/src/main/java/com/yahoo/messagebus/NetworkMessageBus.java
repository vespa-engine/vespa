// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.messagebus.network.Network;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The combination of a messagebus and a network over which it may send data.
 *
 * @author bratseth
 */
public class NetworkMessageBus {

    private final Network network;
    private final MessageBus messageBus;

    private final AtomicBoolean destroyed = new AtomicBoolean(false);

    public NetworkMessageBus(Network network, MessageBus messageBus) {
        this.network = network;
        this.messageBus = messageBus;
    }

    /** Returns the contained message bus object */
    public MessageBus getMessageBus() { return messageBus; }

    /** Returns the network of this as a Network */
    public Network getNetwork() { return network; }

    /**
     * Irreversibly destroys the content of this.
     *
     * @return whether this destroyed anything, or if it was already destroyed
     */
    public boolean destroy() {
        if ( destroyed.getAndSet(true)) return false;

        getMessageBus().destroy();
        return true;
    }

}
