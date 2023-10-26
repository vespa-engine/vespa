// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc.test;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.jrt.slobrok.server.Slobrok;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.Result;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.local.LocalNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.test.SimpleProtocol;

import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class RemoteClient {

    private final Slobrok slobrok;
    private final MessageBus mbus;
    private final ReplyQueue queue = new ReplyQueue();
    private final SourceSession session;

    private RemoteClient(Protocol protocol, boolean network) {
        this.slobrok = newSlobrok();
        mbus = network
               ? new MessageBus(new RPCNetwork(new RPCNetworkParams().setSlobroksConfig(slobroksConfig())),
                                new MessageBusParams().addProtocol(protocol))
               : new MessageBus(new LocalNetwork(), new MessageBusParams().addProtocol(protocol));
        session = mbus.createSourceSession(new SourceSessionParams().setThrottlePolicy(null).setReplyHandler(queue));
    }

    public Result sendMessage(Message msg) {
        return session.send(msg);
    }

    public Reply awaitReply(int timeout, TimeUnit unit) throws InterruptedException {
        return queue.awaitReply(timeout, unit);
    }

    public SlobroksConfig slobroksConfig() {
        return TestUtils.configFor(slobrok);
    }

    public void close() {
        session.destroy();
        mbus.destroy();
        slobrok.stop();
    }

    public static RemoteClient newInstanceWithInternSlobrok(boolean network) {
        return new RemoteClient(new SimpleProtocol(), network);
    }

    public static RemoteClient newInstanceWithProtocolAndInternSlobrok(Protocol protocol, boolean network) {
        return new RemoteClient(protocol, network);
    }

    private static Slobrok newSlobrok() {
        Slobrok slobrok;
        try {
            slobrok = new Slobrok();
        } catch (ListenFailedException e) {
            throw new IllegalStateException(e);
        }
        return slobrok;
    }
}
