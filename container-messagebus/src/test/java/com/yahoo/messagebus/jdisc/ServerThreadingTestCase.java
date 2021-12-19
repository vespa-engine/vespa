// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.EmptyReply;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.Reply;
import com.yahoo.messagebus.ReplyHandler;
import com.yahoo.messagebus.SourceSession;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.local.LocalNetwork;
import com.yahoo.messagebus.network.local.LocalWire;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.shared.SharedDestinationSession;
import com.yahoo.messagebus.shared.SharedMessageBus;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.Test;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ServerThreadingTestCase {

    private static final int NUM_THREADS = 32;
    private static final int NUM_REQUESTS = 1000;

    @Test
    public void requireThatServerIsThreadSafe() throws Exception {
        final LocalWire wire = new LocalWire();
        final Client client = new Client(wire);
        final Server server = new Server(wire);

        for (int i = 0; i < NUM_REQUESTS; ++i) {
            final Message msg = new SimpleMessage("foo");
            msg.setRoute(Route.parse(server.delegate.connectionSpec()));
            msg.pushHandler(client);
            assertTrue(client.session.send(msg).isAccepted());
        }
        for (int i = 0; i < NUM_REQUESTS; ++i) {
            final Reply reply = client.replies.poll(600, TimeUnit.SECONDS);
            assertTrue(reply instanceof EmptyReply);
            assertFalse(reply.hasErrors());
        }

        assertTrue(client.close());
        assertTrue(server.close());
    }

    private static class Client implements ReplyHandler {

        final BlockingDeque<Reply> replies = new LinkedBlockingDeque<>();
        final MessageBus mbus;
        final SourceSession session;

        Client(final LocalWire wire) {
            mbus = new MessageBus(
                    new LocalNetwork(wire),
                    new MessageBusParams().addProtocol(new SimpleProtocol()));
            session = mbus.createSourceSession(
                    new SourceSessionParams()
                            .setReplyHandler(this)
                            .setThrottlePolicy(null));
        }

        @Override
        public void handleReply(final Reply reply) {
            replies.addLast(reply);
        }

        boolean close() {
            return session.destroy() && mbus.destroy();
        }
    }

    private static class Server extends MbusRequestHandler {

        final Executor executor = Executors.newFixedThreadPool(NUM_THREADS);
        final MbusServer delegate;
        final TestDriver driver;

        Server(final LocalWire wire) {
            driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
            delegate = newMbusServer(driver, wire);

            final ContainerBuilder builder = driver.newContainerBuilder();
            builder.serverBindings().bind("mbus://*/*", this);
            driver.activateContainer(builder);
            delegate.start();
        }

        @Override
        public void handleMessage(final Message msg) {
            executor.execute(() -> {
                final Reply reply = new EmptyReply();
                reply.swapState(msg);
                reply.popHandler().handleReply(reply);
            });
        }

        boolean close() {
            delegate.release();
            return driver.close();
        }
    }

    private static MbusServer newMbusServer(final CurrentContainer container, final LocalWire wire) {
        final SharedMessageBus mbus = new SharedMessageBus(new MessageBus(
                new LocalNetwork(wire),
                new MessageBusParams().addProtocol(new SimpleProtocol())));
        final SharedDestinationSession session = mbus.newDestinationSession(
                new DestinationSessionParams());
        final MbusServer server = new MbusServer(container, session);
        session.release();
        mbus.release();
        return server;
    }
}
