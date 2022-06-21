// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.messagebus;

import com.yahoo.component.ComponentId;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.messagebus.IntermediateSessionParams;
import com.yahoo.messagebus.jdisc.MbusServer;
import com.yahoo.messagebus.shared.SharedIntermediateSession;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
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
        log.log(Level.INFO, "Deconstructing mbus server: " + server);
        long start = System.currentTimeMillis();
        server.close();
        server.release();
        sessionRef.getReference().close();
        log.log(Level.INFO, String.format("Mbus server deconstruction completed in %.3f seconds",
                (System.currentTimeMillis()-start)/1000D));
    }

}
