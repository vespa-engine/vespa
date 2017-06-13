// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;


import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.yahoo.container.QrConfig;
import com.yahoo.container.Server;
import com.yahoo.container.jdisc.messagebus.SessionCache;
import com.yahoo.jrt.ListenFailedException;
import com.yahoo.log.LogLevel;

import java.util.logging.Logger;


/**
 * The application which sets up the jDisc container
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class ContainerDiscApplication {

    private static final Logger log = Logger.getLogger(ContainerDiscApplication.class.getName());

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

    public static void hackToInitializeServer(QrConfig config) {
        try {
            Server.get().initialize(config);
        } catch (Exception e) {
            log.log(LogLevel.ERROR, "Caught exception when initializing server. Exiting.", e);
            Runtime.getRuntime().halt(1);
        }
    }

}
