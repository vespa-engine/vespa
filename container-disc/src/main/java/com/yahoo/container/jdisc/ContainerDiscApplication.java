// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.jrt.ListenFailedException;

/**
 * TODO: The contents of this class can probably be moved into ConfiguredApplication
 */
public class ContainerDiscApplication {

    private SessionCache sessionCache;

    @Inject
    public ContainerDiscApplication(String configId) throws ListenFailedException {
        sessionCache = new SessionCache(configId);
    }

    AbstractModule getMbusBindings() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(SessionCache.class).toInstance(sessionCache);
            }
        };
    }

}
