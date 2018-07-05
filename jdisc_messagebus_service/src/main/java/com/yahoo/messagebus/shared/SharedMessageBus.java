// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.messagebus.shared;

import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.jdisc.AbstractResource;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.*;
import com.yahoo.messagebus.network.Network;
import com.yahoo.messagebus.network.rpc.RPCNetwork;
import com.yahoo.messagebus.network.rpc.RPCNetworkParams;
import com.yahoo.cloud.config.SlobroksConfig;

import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 */
public class SharedMessageBus extends AbstractResource {

    private static final Logger log = Logger.getLogger(SharedMessageBus.class.getName());
    private final MessageBus mbus;

    public SharedMessageBus(MessageBus mbus) {
        mbus.getClass(); // throws NullPointerException
        this.mbus = mbus;
    }

    public MessageBus messageBus() {
        return mbus;
    }

    @Override
    protected void destroy() {
        log.log(LogLevel.DEBUG, "Destroying shared message bus.");
        mbus.destroy();
    }

    public SharedSourceSession newSourceSession(SourceSessionParams params) {
        return new SharedSourceSession(this, params);
    }

    public SharedIntermediateSession newIntermediateSession(IntermediateSessionParams params) {
        return new SharedIntermediateSession(this, params);
    }

    public SharedDestinationSession newDestinationSession(DestinationSessionParams params) {
        return new SharedDestinationSession(this, params);
    }

    public static SharedMessageBus newInstance(MessageBusParams mbusParams, RPCNetworkParams netParams) {
        return new SharedMessageBus(new MessageBus(newNetwork(netParams), mbusParams));
    }

    private static Network newNetwork(RPCNetworkParams params) {
        SlobroksConfig cfg = params.getSlobroksConfig();
        if (cfg == null) {
            cfg = ConfigGetter.getConfig(SlobroksConfig.class, params.getSlobrokConfigId());
        }
        if (cfg.slobrok().isEmpty()) {
            return new NullNetwork(); // for LocalApplication
        }
        return new RPCNetwork(params);
    }
}
