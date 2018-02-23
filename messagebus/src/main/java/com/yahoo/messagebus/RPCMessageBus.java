// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus;

import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.network.Identity;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * The RPCMessageBus class wraps a MessageBus with an RPCNetwork and handles reconfiguration. Please note that according
 * to the object shutdown order, you must shut down all sessions before shutting down this object.
 *
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class RPCMessageBus extends NetworkMessageBus {

    private static final Logger log = Logger.getLogger(RPCMessageBus.class.getName());
    private final ConfigAgent configAgent;

    /**
     * Constructs a new instance of this class.
     *
     * @param mbusParams   A complete set of message bus parameters.
     * @param rpcParams    A complete set of network parameters.
     * @param routingCfgId The config id for message bus routing specs.
     */
    public RPCMessageBus(MessageBusParams mbusParams, RPCNetworkParams rpcParams, String routingCfgId) {
        this(mbusParams, new RPCNetwork(rpcParams), routingCfgId);
    }

    private RPCMessageBus(MessageBusParams mbusParams, RPCNetwork network, String routingCfgId) {
        this(new MessageBus(network, mbusParams), network, routingCfgId);
    }

    private RPCMessageBus(MessageBus messageBus, RPCNetwork network, String routingCfgId) {
        super(network, messageBus);
        configAgent = new ConfigAgent(routingCfgId != null ? routingCfgId : "client", messageBus);
        configAgent.subscribe();
    }

    /**
     * This constructor requires an array of protocols that it is to support, as well as the host application's config
     * identifier. That identifier is necessary so that all created sessions can be uniquely identified on the network.
     *
     * @param protocols    An array of known protocols.
     * @param rpcParams    A complete set of network parameters.
     * @param routingCfgId The config id for message bus routing specs.
     */
    public RPCMessageBus(List<Protocol> protocols, RPCNetworkParams rpcParams, String routingCfgId) {
        this(new MessageBusParams().addProtocols(protocols), rpcParams, routingCfgId);
    }

    /**
     * This constructor requires a single protocol that it is to support, as well as the host application's config
     * identifier.
     *
     * @param protocol An instance of the known protocol.
     * @param configId The host application's config id. This will be used to resolve the service name prefix used when
     *                 registering with the slobrok. Using null here is allowed, but will not allow intermediate- or
     *                 destination sessions to be routed to.
     */
    public RPCMessageBus(Protocol protocol, String configId) {
        this(Arrays.asList(protocol), new RPCNetworkParams().setIdentity(new Identity(configId)), null);
    }

    /**
     * Sets the destroyed flag to true. The very first time this method is called, it cleans up all its dependencies.
     * Even if you retain a reference to this object, all of its content is allowed to be garbage collected.
     *
     * @return true if content existed and was destroyed.
     */
    @Override
    public boolean destroy() {
        boolean destroyed = super.destroy();
        if (destroyed)
            configAgent.shutdown();
        return destroyed;
    }

    /** Returns the network of this as a RPCNetwork */
    public RPCNetwork getRPCNetwork() { return (RPCNetwork)getNetwork(); }

}
