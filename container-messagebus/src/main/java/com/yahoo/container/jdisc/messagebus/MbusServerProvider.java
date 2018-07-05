// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.messagebus;

import com.yahoo.component.ComponentId;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.log.LogLevel;
import com.yahoo.messagebus.IntermediateSessionParams;
import com.yahoo.messagebus.jdisc.MbusServer;
import com.yahoo.messagebus.shared.SharedIntermediateSession;

import java.util.logging.Logger;

/**
 * TODO: Javadoc
 *
 * @author Tony Vaagenes
 */
public class MbusServerProvider implements Provider<MbusServer> {
    private static final Logger log = Logger.getLogger(MbusServerProvider.class.getName());

    private final MbusServer server;
    private final ReferencedResource<SharedIntermediateSession> sessionRef;

    public MbusServerProvider(ComponentId id, SessionCache sessionCache, CurrentContainer currentContainer) {
        ComponentId chainId = id.withoutNamespace(); //TODO: this should be a config value instead.
        sessionRef = sessionCache.retainIntermediate(createIntermediateSessionParams(true, chainId.stringValue()));
        server = new MbusServer(currentContainer, sessionRef.getResource());
    }

    static IntermediateSessionParams createIntermediateSessionParams(boolean broadcastName, String name) {
        IntermediateSessionParams intermediateParams = new IntermediateSessionParams();
        intermediateParams.setBroadcastName(broadcastName);
        intermediateParams.setName(name);
        return intermediateParams;
    }

    public SharedIntermediateSession getSession() {
        return sessionRef.getResource();
    }

    @Override
    public MbusServer get() {
        return server;
    }

    @Override
    public void deconstruct() {
        log.log(LogLevel.INFO, "Deconstructing mbus server: " + server);
        server.close();
        server.release();
        sessionRef.getReference().close();
    }
}
