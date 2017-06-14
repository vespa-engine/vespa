// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.yahoo.jdisc.test.TestDriver;
import org.apache.commons.daemon.Daemon;
import org.apache.commons.daemon.DaemonContext;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class Main implements Daemon {

    public static void main(String[] args) {
        startContainer(System.getProperty("config.id"));
    }

    public static TestDriver startContainer(final String configId) {
        System.setProperty("config.id", configId);

        @SuppressWarnings("unused")
        TestDriver driver = TestDriver.newInjectedApplicationInstance(ConfiguredApplication.class,
                new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(String.class).annotatedWith(Names.named("configId")).toInstance(configId);

                    }
                });

        return driver;
    }

    @Override
    public void init(DaemonContext context) throws Exception {
        //nada
    }

    @Override
    public void start() throws Exception {
        main(new String[0]);
    }

    @Override
    public void stop() throws Exception {
        //TODO: Implement this method:

    }

    @Override
    public void destroy() {
        //nada
    }

}
