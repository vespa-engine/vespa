// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc.test;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.Reply;
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
    private final MessageBus mbus;
    private final MessageQueue queue = new MessageQueue();
    private final DestinationSession session;

    private RemoteServer(Protocol protocol, String identity) {
        this.slobrok = newSlobrok();
        mbus = new MessageBus(new RPCNetwork(new RPCNetworkParams()
                                                     .setSlobroksConfig(slobroksConfig())
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

    public SlobroksConfig slobroksConfig() {
        return TestUtils.configFor(slobrok);
    }

    public void close() {
        session.destroy();
        mbus.destroy();
        slobrok.stop();
    }

    public static RemoteServer newInstanceWithInternSlobrok() {
        return new RemoteServer(new SimpleProtocol(), "remote");
    }

    public static RemoteServer newInstance(String identity, Protocol protocol) {
        return new RemoteServer(protocol, identity);
    }

    private static Slobrok newSlobrok() {
        try {
            return new Slobrok();
        } catch (ListenFailedException e) {
            throw new IllegalStateException(e);
        }
    }

}
