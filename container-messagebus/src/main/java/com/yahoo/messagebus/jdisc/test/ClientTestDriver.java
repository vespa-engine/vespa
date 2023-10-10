// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc.test;

import com.yahoo.jdisc.References;
import com.yahoo.jdisc.Request;
import com.yahoo.jdisc.ResourceReference;
import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Protocol;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.jdisc.MbusClient;
import com.yahoo.messagebus.jdisc.MbusRequest;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.shared.SharedMessageBus;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.messagebus.test.SimpleProtocol;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
public class ClientTestDriver {

    private final RemoteServer server;
    private final MbusClient client;
    private final SharedSourceSession session;
    private final TestDriver driver;

    private ClientTestDriver(RemoteServer server, Protocol protocol) {
        this.server = server;

        MessageBusParams mbusParams = new MessageBusParams().addProtocol(protocol);
        RPCNetworkParams netParams = new RPCNetworkParams().setSlobroksConfig(server.slobroksConfig());
        SharedMessageBus mbus = SharedMessageBus.newInstance(mbusParams, netParams);
        session = mbus.newSourceSession(new SourceSessionParams());
        client = new MbusClient(session);
        client.start();
        mbus.release();

        driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.clientBindings().bind("mbus://*/*", client);
        driver.activateContainer(builder);
    }

    public SourceSession sourceSession() {
        return session.session();
    }

    public Request newServerRequest() {
        return new Request(driver, URI.create("mbus://localhost/"));
    }

    public Request newClientRequest(Message msg) {
        msg.setRoute(Route.parse(server.connectionSpec()));
        if (msg.getTrace().getLevel() == 0) {
            msg.getTrace().setLevel(9);
        }
        final Request parent = newServerRequest();
        try (final ResourceReference ref = References.fromResource(parent)) {
            return new MbusRequest(parent, URI.create("mbus://remotehost/"), msg);
        }
    }

    public boolean sendRequest(Request request, ResponseHandler responseHandler) {
        request.connect(responseHandler).close(null);
        return true;
    }

    public boolean sendMessage(Message msg, ResponseHandler responseHandler) {
        final Request request = newClientRequest(msg);
        try (final ResourceReference ref = References.fromResource(request)) {
            return sendRequest(request, responseHandler);
        }
    }

    public Message awaitMessage() {
        Message msg = null;
        try {
            msg = server.awaitMessage(60, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (msg != null) {
            msg.getTrace().trace(0, "Message received by RemoteServer.");
        }
        return msg;
    }

    public void sendReply(Reply reply) {
        reply.getTrace().trace(0, "Sending reply from RemoteServer.");
        server.sendReply(reply);
    }

    public boolean awaitMessageAndSendReply(Reply reply) {
        Message msg = awaitMessage();
        if (msg == null) {
            return false;
        }
        reply.swapState(msg);
        sendReply(reply);
        return true;
    }

    public boolean close() {
        session.release();
        client.release();
        server.close();
        return driver.close();
    }

    public MbusClient client() {
        return client;
    }

    public RemoteServer server() {
        return server;
    }

    public static ClientTestDriver newInstance() {
        return new ClientTestDriver(RemoteServer.newInstanceWithInternSlobrok(), new SimpleProtocol());
    }

    public static ClientTestDriver newInstanceWithProtocol(Protocol protocol) {
        return new ClientTestDriver(RemoteServer.newInstanceWithInternSlobrok(), protocol);
    }

}
