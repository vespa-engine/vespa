// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import com.yahoo.jdisc.application.Application;
import org.apache.commons.daemon.DaemonContext;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;


/**
 * @author Simon Thoresen Hult
 */
public class BootstrapDaemonIntegrationTest {

    @Test
    public void requireThatConfigFileIsInjected() throws Exception {
        BootstrapDaemon daemon = new BootstrapDaemon();

        DaemonContext ctx = Mockito.mock(DaemonContext.class);
        Mockito.doReturn(new String[] { MyApplication.class.getName() }).when(ctx).getArguments();
        daemon.init(ctx);
        daemon.start();

        assertEquals("bar", ((MyApplication)((ApplicationLoader)daemon.loader()).application()).foo);

        daemon.stop();
        daemon.destroy();
    }

    public static class MyApplication implements Application {

        final String foo;

        @Inject
        public MyApplication(@Named("foo") String foo) {
            this.foo = foo;
        }

        @Override
        public void start() {

        }

        @Override
        public void stop() {

        }

        @Override
        public void destroy() {

        }
    }
}
