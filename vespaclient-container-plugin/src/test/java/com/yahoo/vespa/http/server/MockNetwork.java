// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.server;

import java.util.List;

import com.yahoo.jrt.slobrok.api.IMirror;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.NetworkOwner;
import com.yahoo.messagebus.routing.RoutingNode;

/**
 * NOP-network.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
class MockNetwork implements Network {

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
        return true;
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
