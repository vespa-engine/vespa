// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc.test;

import com.google.inject.Module;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.Error;
import com.yahoo.messagebus.jdisc.MbusServer;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.shared.ServerSession;
import com.yahoo.messagebus.shared.SharedMessageBus;
import com.yahoo.messagebus.test.SimpleProtocol;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class ServerTestDriver {

    private final RemoteClient client;
    private final MbusServer server;
    private final TestDriver driver;

    private ServerTestDriver(RemoteClient client, boolean activateContainer, RequestHandler requestHandler,
                             Protocol protocol, Module... guiceModules)
    {
        this.client = client;
        driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi(guiceModules);
        if (activateContainer) {
            ContainerBuilder builder = driver.newContainerBuilder();
            if (requestHandler != null) {
                builder.serverBindings().bind("mbus://*/*", requestHandler);
            }
            driver.activateContainer(builder);
        }

        MessageBusParams mbusParams = new MessageBusParams().addProtocol(protocol);
        RPCNetworkParams netParams = new RPCNetworkParams().setSlobrokConfigId(client.slobrokId());
        SharedMessageBus mbus = SharedMessageBus.newInstance(mbusParams, netParams);
        ServerSession session = mbus.newDestinationSession(new DestinationSessionParams());
        server = new MbusServer(driver, session);
        server.start();
        session.release();
        mbus.release();
    }

    public boolean sendMessage(Message msg) {
        msg.setRoute(Route.parse(server.connectionSpec()));
        msg.getTrace().setLevel(9);
        return client.sendMessage(msg).isAccepted();
    }

    public Reply awaitReply() {
        Reply reply = null;
        try {
            reply = client.awaitReply(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (reply != null) {
            System.out.println(reply.getTrace());
        }
        return reply;
    }

    public Reply awaitSuccess() {
        Reply reply = awaitReply();
        if (reply == null || reply.hasErrors()) {
            return null;
        }
        return reply;
    }

    public Reply awaitErrors(Integer... errCodes) {
        Reply reply = awaitReply();
        if (reply == null) {
            return null;
        }
        List<Integer> lst = new LinkedList<>(Arrays.asList(errCodes));
        for (int i = 0, len = reply.getNumErrors(); i < len; ++i) {
            Error err = reply.getError(i);
            System.out.println(err);
            int idx = lst.indexOf(err.getCode());
            if (idx < 0) {
                return null;
            }
            lst.remove(idx);
        }
        if (!lst.isEmpty()) {
            return null;
        }
        return reply;
    }

    public boolean close() {
        server.close();
        server.release();
        client.close();
        return driver.close();
    }

    public TestDriver parent() {
        return driver;
    }

    public RemoteClient client() {
        return client;
    }

    public MbusServer server() {
        return server;
    }

    public static ServerTestDriver newInstance(RequestHandler requestHandler, boolean network, Module... guiceModules) {
        return new ServerTestDriver(RemoteClient.newInstanceWithInternSlobrok(network), true, requestHandler,
                                    new SimpleProtocol(), guiceModules);
    }

    public static ServerTestDriver newInstanceWithProtocol(Protocol protocol, RequestHandler requestHandler,
                                                           boolean network, Module... guiceModules)
    {
        return new ServerTestDriver(RemoteClient.newInstanceWithInternSlobrok(network), true, requestHandler, protocol,
                                    guiceModules);
    }

    public static ServerTestDriver newInstanceWithExternSlobrok(String slobrokId, RequestHandler requestHandler,
                                                                boolean network, Module... guiceModules)
    {
        return new ServerTestDriver(RemoteClient.newInstanceWithExternSlobrok(slobrokId, network),
                                    true, requestHandler, new SimpleProtocol(), guiceModules);
    }

    public static ServerTestDriver newInactiveInstance(boolean network, Module... guiceModules) {
        return new ServerTestDriver(RemoteClient.newInstanceWithInternSlobrok(network), false, null,
                                    new SimpleProtocol(), guiceModules);
    }

    public static ServerTestDriver newInactiveInstanceWithProtocol(Protocol protocol, boolean network, Module... guiceModules) {
        return new ServerTestDriver(RemoteClient.newInstanceWithProtocolAndInternSlobrok(protocol, network), false, null,
                                    protocol, guiceModules);
    }

    public static ServerTestDriver newUnboundInstance(boolean network, Module... guiceModules) {
        return new ServerTestDriver(RemoteClient.newInstanceWithInternSlobrok(network), true, null,
                                    new SimpleProtocol(), guiceModules);
    }

}
