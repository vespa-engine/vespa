// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class ApplicationNotReadyTestCase {

    @Test
    public void requireThatExceptionIsThrown() {
        try {
            TestDriver.newInjectedApplicationInstanceWithoutOsgi(MyApplication.class);
            fail();
        } catch (ProvisionException e) {
            Throwable t = e.getCause();
            assertNotNull(t);
            assertTrue(t instanceof ApplicationNotReadyException);
        }
    }

    private static class MyApplication implements Application {

        @Inject
        MyApplication(ContainerActivator activator) {
            activator.activateContainer(activator.newContainerBuilder());
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
