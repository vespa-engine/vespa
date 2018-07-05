// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.apache.commons.daemon.DaemonContext;
import org.apache.commons.daemon.DaemonController;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class BootstrapDaemonTestCase {

    @Test
    public void requireThatPrivilegedLifecycleWorks() throws Exception {
        MyLoader loader = new MyLoader();
        BootstrapDaemon daemon = new BootstrapDaemon(loader, true);
        daemon.init(new MyContext("foo"));
        assertTrue(loader.hasState(true, false, false, false));
        assertTrue(loader.privileged);
        daemon.start();
        assertTrue(loader.hasState(true, true, false, false));
        daemon.stop();
        assertTrue(loader.hasState(true, true, true, false));
        daemon.destroy();
        assertTrue(loader.hasState(true, true, true, true));
    }

    @Test
    public void requireThatNonPrivilegedLifecycleWorks() throws Exception {
        MyLoader loader = new MyLoader();
        BootstrapDaemon daemon = new BootstrapDaemon(loader, false);
        daemon.init(new MyContext("foo"));
        assertTrue(loader.hasState(false, false, false, false));
        daemon.start();
        assertTrue(loader.hasState(true, true, false, false));
        assertFalse(loader.privileged);
        daemon.stop();
        assertTrue(loader.hasState(true, true, true, false));
        daemon.destroy();
        assertTrue(loader.hasState(true, true, true, true));
    }

    @Test
    public void requireThatBundleLocationIsRequired() throws Exception {
        MyLoader loader = new MyLoader();
        BootstrapDaemon daemon = new BootstrapDaemon(loader, true);
        try {
            daemon.init(new MyContext((String[])null));
            fail();
        } catch (IllegalArgumentException e) {
            assertNull(loader.bundleLocation);
        }
        try {
            daemon.init(new MyContext());
            fail();
        } catch (IllegalArgumentException e) {
            assertNull(loader.bundleLocation);
        }
        try {
            daemon.init(new MyContext((String)null));
            fail();
        } catch (IllegalArgumentException e) {
            assertNull(loader.bundleLocation);
        }
        try {
            daemon.init(new MyContext("foo", "bar"));
            fail();
        } catch (IllegalArgumentException e) {
            assertNull(loader.bundleLocation);
        }

        daemon.init(new MyContext("foo"));
        daemon.start();

        assertNotNull(loader.bundleLocation);
        assertEquals("foo", loader.bundleLocation);

        daemon.stop();
        daemon.destroy();
    }

    @Test
    public void requireThatEnvironmentIsRequired() {
        try {
            new BootstrapDaemon();
            fail();
        } catch (IllegalStateException e) {

        }
    }

    private static class MyLoader implements BootstrapLoader {

        String bundleLocation = null;
        boolean privileged = false;
        boolean initCalled = false;
        boolean startCalled = false;
        boolean stopCalled = false;
        boolean destroyCalled = false;

        boolean hasState(boolean initCalled, boolean startCalled, boolean stopCalled, boolean destroyCalled) {
            return this.initCalled == initCalled && this.startCalled == startCalled &&
                   this.stopCalled == stopCalled && this.destroyCalled == destroyCalled;
        }

        @Override
        public void init(String bundleLocation, boolean privileged) throws Exception {
            this.bundleLocation = bundleLocation;
            this.privileged = privileged;
            initCalled = true;
        }

        @Override
        public void start() throws Exception {
            startCalled = true;
        }

        @Override
        public void stop() throws Exception {
            stopCalled = true;
        }

        @Override
        public void destroy() {
            destroyCalled = true;
        }
    }

    private static class MyContext implements DaemonContext {

        final String[] args;

        MyContext(String... args) {
            this.args = args;
        }

        @Override
        public DaemonController getController() {
            return null;
        }

        @Override
        public String[] getArguments() {
            return args;
        }
    }
}
