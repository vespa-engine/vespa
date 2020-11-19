// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.messagebus;

import com.google.inject.Inject;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.jdisc.config.SessionConfig;
import com.yahoo.jdisc.ReferencedResource;
import com.yahoo.messagebus.AllPassThrottlePolicy;
import com.yahoo.messagebus.IntermediateSessionParams;
import com.yahoo.messagebus.SourceSessionParams;
import com.yahoo.messagebus.jdisc.MbusClient;
import com.yahoo.messagebus.shared.SharedIntermediateSession;
import com.yahoo.messagebus.shared.SharedSourceSession;

/**
 * @author Tony Vaagenes
 * @author Einar M R Rosenvinge
 */
public class MbusClientProvider implements Provider<MbusClient> {

    private final MbusClient client;

    private static MbusClient createSourceClient(SessionCache sessionCache, boolean setAllPassThrottlePolicy) {
        final SourceSessionParams sourceSessionParams = new SourceSessionParams();
        if (setAllPassThrottlePolicy) {
            sourceSessionParams.setThrottlePolicy(new AllPassThrottlePolicy());
        }
        try (ReferencedResource<SharedSourceSession> ref = sessionCache.retainSource(sourceSessionParams)) {
            return new MbusClient(ref.getResource());
        }
    }

    @Inject
    public MbusClientProvider(SessionCache sessionCache, SessionConfig sessionConfig) {
        switch (sessionConfig.type()) {
            case INTERMEDIATE:
                final IntermediateSessionParams intermediateSessionParams =
                    MbusServerProvider.createIntermediateSessionParams(true, sessionConfig.name());
                try (final ReferencedResource<SharedIntermediateSession> ref =
                     sessionCache.retainIntermediate(intermediateSessionParams)) {
                        client = new MbusClient(ref.getResource());
                }
                break;
            case SOURCE:
                client = createSourceClient(sessionCache, false);
                break;
            case INTERNAL:
                client = createSourceClient(sessionCache, true);
                break;
            default:
                throw new IllegalArgumentException("Unknown session type: " + sessionConfig.type());
        }
    }

    @Override
    public MbusClient get() {
        return client;
    }

    @Override
    public void deconstruct() {
        client.release();
    }

}
