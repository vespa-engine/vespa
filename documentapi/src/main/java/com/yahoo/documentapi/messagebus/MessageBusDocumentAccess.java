// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus;

import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.document.select.parser.ParseException;
import com.yahoo.documentapi.AsyncParameters;
import com.yahoo.documentapi.DocumentAccess;
import com.yahoo.documentapi.DocumentAccessException;
import com.yahoo.documentapi.SubscriptionParameters;
import com.yahoo.documentapi.SubscriptionSession;
import com.yahoo.documentapi.SyncParameters;
import com.yahoo.documentapi.VisitorDestinationParameters;
import com.yahoo.documentapi.VisitorParameters;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.messagebus.MessageBus;
import com.yahoo.messagebus.NetworkMessageBus;
import com.yahoo.messagebus.RPCMessageBus;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.local.LocalNetwork;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class implements the {@link DocumentAccess} interface using message bus for communication.
 *
 * @author Einar Rosenvinge
 * @author bratseth
 */
public class MessageBusDocumentAccess extends DocumentAccess {

    private static final Logger log = Logger.getLogger(MessageBusDocumentAccess.class.getName());

    private final NetworkMessageBus bus;

    private final MessageBusParams params;
    // TODO: make pool size configurable? ScheduledExecutorService is not dynamic
    private final ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors(),
                                             ThreadFactoryFactory.getDaemonThreadFactory("mbus.access.scheduler"));

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
            mbusParams.addProtocol(new DocumentProtocol(getDocumentTypeManager(), params.getProtocolConfigId()));
            if (System.getProperty("vespa.local", "false").equals("true")) { // set by Application when running locally
                LocalNetwork network = new LocalNetwork();
                bus = new NetworkMessageBus(network, new MessageBus(network, mbusParams));
            }
            else {
                if (mbusParams.getMessageBusConfig() != null) {
                    bus = new RPCMessageBus(mbusParams, params.getRPCNetworkParams());
                }
                else {
                    log.log(Level.FINE, () -> "Setting up self-subscription to config because explicit config was missing; try to avoid this in containers");
                    bus = new RPCMessageBus(mbusParams, params.getRPCNetworkParams(), params.getRoutingConfigId());
                }
            }
        }
        catch (Exception e) {
            throw new DocumentAccessException(e);
        }
    }

    private MessageBus messageBus() {
        return bus.getMessageBus();
    }

    @Override
    public void shutdown() {
        super.shutdown();
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
        MessageBusVisitorSession session = MessageBusVisitorSession.createForMessageBus(
                messageBus(), scheduledExecutorService, params);
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

    /** Returns the internal message bus object so that clients can use it directly. */
    public MessageBus getMessageBus() { return messageBus(); }

    /**
     * Returns the network layer of the internal message bus object so that clients can use it directly. This may seem
     * abit arbitrary, but the fact is that the RPCNetwork actually implements the IMirror API as well as exposing the
     * SystemState object.
     */
    public Network getNetwork() { return bus.getNetwork(); }

    /**
     * Returns the parameter object that controls the underlying message bus. Changes to these parameters do not affect
     * previously created sessions.
     */
    public MessageBusParams getParams() { return params; }

}
