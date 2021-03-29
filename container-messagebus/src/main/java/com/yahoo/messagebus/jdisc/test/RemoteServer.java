// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc.test;

import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.test.SimpleProtocol;

import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class RemoteServer {

    private final Slobrok slobrok;
    private final String slobrokId;
    private final MessageBus mbus;
    private final MessageQueue queue = new MessageQueue();
    private final DestinationSession session;

    private RemoteServer(Slobrok slobrok, String slobrokId, Protocol protocol, String identity) {
        this.slobrok = slobrok;
        this.slobrokId = slobrok != null ? slobrok.configId() : slobrokId;
        mbus = new MessageBus(new RPCNetwork(new RPCNetworkParams()
                                                     .setSlobrokConfigId(this.slobrokId)
                                                     .setIdentity(new Identity(identity))),
                              new MessageBusParams().addProtocol(protocol));
        session = mbus.createDestinationSession(new DestinationSessionParams().setMessageHandler(queue));
    }

    public String connectionSpec() {
        return session.getConnectionSpec();
    }

    public Message awaitMessage(int timeout, TimeUnit unit) throws InterruptedException {
        return queue.awaitMessage(timeout, unit);
    }

    public void ackMessage(Message msg) {
        session.acknowledge(msg);
    }

    public void sendReply(Reply reply) {
        session.reply(reply);
    }

    public String slobrokId() {
        return slobrokId;
    }

    public void close() {
        session.destroy();
        mbus.destroy();
        if (slobrok != null) {
            slobrok.stop();
        }
    }

    public static RemoteServer newInstanceWithInternSlobrok() {
        return new RemoteServer(newSlobrok(), null, new SimpleProtocol(), "remote");
    }

    public static RemoteServer newInstanceWithExternSlobrok(String slobrokId) {
        return new RemoteServer(null, slobrokId, new SimpleProtocol(), "remote");
    }

    public static RemoteServer newInstance(String slobrokId, String identity, Protocol protocol) {
        return new RemoteServer(null, slobrokId, protocol, identity);
    }

    public static RemoteServer newInstanceWithProtocol(Protocol protocol) {
        return new RemoteServer(newSlobrok(), null, protocol, "remote");
    }

    private static Slobrok newSlobrok() {
        try {
            return new Slobrok();
        } catch (ListenFailedException e) {
            throw new IllegalStateException(e);
        }
    }

}
