// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.concurrent.DaemonThreadFactory;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.*;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.RPCMessageBus;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.local.LocalNetwork;
import com.yahoo.messagebus.network.local.LocalWire;
import com.yahoo.messagebus.routing.RoutingTable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * This class implements the {@link DocumentAccess} interface using message bus for communication.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar Rosenvinge</a>
 * @author bratseth
 */
public class MessageBusDocumentAccess extends DocumentAccess {

    // either
    private final RPCMessageBus bus;
    // ... or
    private final MessageBus messageBus;
    private final Network network;
    // ... TODO: Do that cleanly
    
    private final MessageBusParams params;
    // TODO: make pool size configurable? ScheduledExecutorService is not dynamic
    private final ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(
            Runtime.getRuntime().availableProcessors(), ThreadFactoryFactory.getDaemonThreadFactory("mbus.access.scheduler"));

    /**
     * Creates a new document access using default values for all parameters.
     */
    public MessageBusDocumentAccess() {
        this(new MessageBusParams());
    }

    /**
     * Creates a new document access using the supplied parameters.
     *
     * @param params All parameters for construction.
     */
    public MessageBusDocumentAccess(MessageBusParams params) {
        super(params);
        this.params = params;
        try {
            com.yahoo.messagebus.MessageBusParams mbusParams = new com.yahoo.messagebus.MessageBusParams(params.getMessageBusParams());
            mbusParams.addProtocol(new DocumentProtocol(getDocumentTypeManager(), params.getProtocolConfigId(), params.getLoadTypes()));
            if (System.getProperty("vespa.local", "false").equals("true")) { // TODO: Hackety hack ... see Application
                bus = null;
                network = new LocalNetwork(new LocalWire());
                messageBus = new MessageBus(network, mbusParams);
            }
            else {
                bus = new RPCMessageBus(mbusParams,
                                        params.getRPCNetworkParams(),
                                        params.getRoutingConfigId());
                network = null;
                messageBus = null;
            }
        }
        catch (Exception e) {
            throw new DocumentAccessException(e);
        }
    }
    
    private MessageBus messageBus() {
        return bus != null ? bus.getMessageBus() : messageBus;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (bus != null)
        bus.destroy();
        scheduledExecutorService.shutdownNow();
    }

    @Override
    public MessageBusSyncSession createSyncSession(SyncParameters parameters) {
        return new MessageBusSyncSession(parameters, messageBus(), this.params);
    }

    @Override
    public MessageBusAsyncSession createAsyncSession(AsyncParameters parameters) {
        return new MessageBusAsyncSession(parameters, messageBus(), this.params);
    }

    @Override
    public MessageBusVisitorSession createVisitorSession(VisitorParameters params) throws ParseException, IllegalArgumentException {
        MessageBusVisitorSession.AsyncTaskExecutor executor = new MessageBusVisitorSession.ThreadAsyncTaskExecutor(scheduledExecutorService);
        MessageBusVisitorSession.MessageBusSenderFactory senderFactory = new MessageBusVisitorSession.MessageBusSenderFactory(bus.getMessageBus());
        MessageBusVisitorSession.MessageBusReceiverFactory receiverFactory = new MessageBusVisitorSession.MessageBusReceiverFactory(bus.getMessageBus());
        RoutingTable table = bus.getMessageBus().getRoutingTable(DocumentProtocol.NAME);

        MessageBusVisitorSession session = new MessageBusVisitorSession(params, executor, senderFactory, receiverFactory, table);
        session.start();
        return session;
    }

    @Override
    public MessageBusVisitorDestinationSession createVisitorDestinationSession(VisitorDestinationParameters params) {
        return new MessageBusVisitorDestinationSession(params, bus.getMessageBus());
    }

    @Override
    public SubscriptionSession createSubscription(SubscriptionParameters parameters) {
        throw new UnsupportedOperationException("Subscriptions not supported.");
    }

    @Override
    public SubscriptionSession openSubscription(SubscriptionParameters parameters) {
        throw new UnsupportedOperationException("Subscriptions not supported.");
    }

    /**
     * Returns the internal message bus object so that clients can use it directly.
     *
     * @return The internal message bus.
     */
    public MessageBus getMessageBus() {
        return messageBus();
    }

    /**
     * Returns the network layer of the internal message bus object so that clients can use it directly. This may seem
     * abit arbitrary, but the fact is that the RPCNetwork actually implements the IMirror API as well as exposing the
     * SystemState object.
     *
     * @return The network layer.
     */
    public Network getNetwork() {
        return bus != null ? bus.getRPCNetwork() : network;
    }

    /**
     * Returns the parameter object that controls the underlying message bus. Changes to these parameters do not affect
     * previously created sessions.
     *
     * @return The parameter object.
     */
    public MessageBusParams getParams() {
        return params;
    }

}
