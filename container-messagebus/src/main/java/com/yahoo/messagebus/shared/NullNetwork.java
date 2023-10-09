// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.NetworkOwner;
import com.yahoo.messagebus.routing.RoutingNode;

import java.util.List;

/**
 * <p>Used by SharedMessageBus as a network when the container runs in LocalApplication with no network services.</p>
 *
 * @author Vegard Havdal
 */
public class NullNetwork implements Network {

    @Override
    public boolean waitUntilReady(double seconds) {
        return true;
    }

    @Override
    public void attach(NetworkOwner owner) {

    }

    @Override
    public void registerSession(String session) {

    }

    @Override
    public void unregisterSession(String session) {

    }

    @Override
    public boolean allocServiceAddress(RoutingNode recipient) {
        return false;
    }

    @Override
    public void freeServiceAddress(RoutingNode recipient) {

    }

    @Override
    public void send(Message msg, List<RoutingNode> recipients) {

    }

    @Override
    public void sync() {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public String getConnectionSpec() {
        return null;
    }

    @Override
    public IMirror getMirror() {
        return null;
    }
}
