// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.jdisc;

import com.yahoo.jdisc.application.ContainerBuilder;
import com.yahoo.jdisc.handler.FutureResponse;
import com.yahoo.jdisc.handler.ResponseHandler;
import com.yahoo.jdisc.test.TestDriver;
import com.yahoo.messagebus.DestinationSession;
import com.yahoo.messagebus.DestinationSessionParams;
import com.yahoo.messagebus.Message;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.MessageBusParams;
import com.yahoo.messagebus.MessageHandler;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.network.local.LocalNetwork;
import com.yahoo.messagebus.network.local.LocalWire;
import com.yahoo.messagebus.routing.Route;
import com.yahoo.messagebus.shared.SharedMessageBus;
import com.yahoo.messagebus.shared.SharedSourceSession;
import com.yahoo.messagebus.test.SimpleMessage;
import com.yahoo.messagebus.test.SimpleProtocol;
import org.junit.Ignore;
import org.junit.Test;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * @author Simon Thoresen Hult
 */
public class ClientThreadingTestCase {

    private static final int NUM_THREADS = 32;
    private static final int NUM_REQUESTS = 1000;

    @Test
    @Ignore
    public void requireThatClientIsThreadSafe() throws Exception {
        final LocalWire wire = new LocalWire();
        final Client client = new Client(wire);
        final Server server = new Server(wire);

        final List<Callable<Boolean>> lst = new LinkedList<>();
        final Route route = Route.parse(server.session.getConnectionSpec());
        for (int i = 0; i < NUM_THREADS; ++i) {
            lst.add(new RequestTask(client, route));
        }
        final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
        for (final Future<Boolean> res : executor.invokeAll(lst, 60, TimeUnit.SECONDS)) {
            assertTrue(res.get());
        }

        assertTrue(client.close());
        assertTrue(server.close());
    }

    private static final class RequestTask implements Callable<Boolean> {

        final Client client;
        final Route route;

        RequestTask(final Client client, final Route route) {
            this.client = client;
            this.route = route;
        }

        @Override
        public Boolean call() throws Exception {
            for (int i = 0; i < NUM_REQUESTS; ++i) {
                final FutureResponse responseHandler = new FutureResponse();
                client.send(new SimpleMessage("foo").setRoute(route), responseHandler);
                responseHandler.get(60, TimeUnit.SECONDS);
            }
            return true;
        }
    }

    private static class Client {

        final MbusClient delegate;
        final TestDriver driver;

        Client(final LocalWire wire) {
            driver = TestDriver.newSimpleApplicationInstanceWithoutOsgi();
            delegate = newMbusClient(wire);

            final ContainerBuilder builder = driver.newContainerBuilder();
            builder.clientBindings().bind("mbus://*/*", delegate);
            driver.activateContainer(builder);
            delegate.start();
        }
        void send(final Message msg, final ResponseHandler handler) {
            final MbusRequest request = new MbusRequest(driver, URI.create("mbus://remote/"), msg, false);
            request.connect(handler).close(null);
            request.release();
        }

        boolean close() {
            delegate.release();
            return driver.close();
        }
    }

    private static class Server implements MessageHandler {

        final MessageBus mbus;
        final DestinationSession session;

        Server(final LocalWire wire) {
            mbus = new MessageBus(
                    new LocalNetwork(wire),
                    new MessageBusParams().addProtocol(new SimpleProtocol()));
            session = mbus.createDestinationSession(
                    new DestinationSessionParams().setMessageHandler(this));
        }

        @Override
        public void handleMessage(final Message msg) {
            session.acknowledge(msg);
        }

        boolean close() {
            return session.destroy() && mbus.destroy();
        }
    }

    private static MbusClient newMbusClient(final LocalWire wire) {
        final SharedMessageBus mbus = new SharedMessageBus(new MessageBus(
                new LocalNetwork(wire),
                new MessageBusParams().addProtocol(new SimpleProtocol())));
        final SharedSourceSession session = mbus.newSourceSession(
                new SourceSessionParams());
        final MbusClient client = new MbusClient(session);
        session.release();
        mbus.release();
        return client;
    }
}
