// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.yahoo.jdisc.test.TestDriver;
import org.apache.felix.framework.util.FelixConstants;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class ApplicationLoaderIntegrationTest {

    @Test
    public void requireThatLifecycleWorks() throws Exception {
        MyModule module = new MyModule();
        ApplicationLoader loader = new ApplicationLoader(TestDriver.newOsgiFramework(),
                                                         Arrays.asList(module));
        loader.init("app-a.jar", false);

        assertFalse(module.init.await(100, TimeUnit.MILLISECONDS));
        assertFalse(module.start.await(100, TimeUnit.MILLISECONDS));
        loader.start();
        assertTrue(module.init.await(60, TimeUnit.SECONDS));
        assertTrue(module.start.await(60, TimeUnit.SECONDS));

        Iterator<Bundle> it = loader.osgiFramework().bundles().iterator();
        assertTrue(it.hasNext());
        Bundle bundle = it.next();
        assertNotNull(bundle);
        assertEquals(FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME,
                     bundle.getSymbolicName());
        assertTrue(it.hasNext());
        assertNotNull(bundle = it.next());
        assertEquals("com.yahoo.vespa.jdisc_core.app-a",
                     bundle.getSymbolicName());
        assertFalse(it.hasNext());

        assertFalse(module.stop.await(100, TimeUnit.MILLISECONDS));
        assertFalse(module.destroy.await(100, TimeUnit.MILLISECONDS));
        loader.stop();
        assertTrue(module.stop.await(60, TimeUnit.SECONDS));
        assertTrue(module.destroy.await(60, TimeUnit.SECONDS));

        loader.destroy();
    }

    @Test
    public void requireThatNoApplicationInstructionThrowsException() throws Exception {
        try {
            TestDriver.newApplicationBundleInstance("cert-a.jar", false);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatMultipleApplicationInstructionsThrowException() throws Exception {
        try {
            TestDriver.newApplicationBundleInstance("app-f-more.jar", false);
            fail();
        } catch (IllegalArgumentException e) {

        }
    }

    @Test
    public void requireThatUnprivilegedBundleCanBeLoadedUnprivileged() throws Exception {
        MyModule module = new MyModule();
        TestDriver driver = TestDriver.newApplicationBundleInstance("app-a.jar", false, module);
        assertTrue(module.init.await(60, TimeUnit.SECONDS));
        assertEquals("com.yahoo.jdisc.bundle.ApplicationA", driver.application().getClass().getName());
        driver.close();
    }

    @Test
    public void requireThatUnprivilegedBundleCanBeLoadedPrivileged() throws Exception {
        MyModule module = new MyModule();
        TestDriver driver = TestDriver.newApplicationBundleInstance("app-a.jar", true, module);
        assertTrue(module.init.await(60, TimeUnit.SECONDS));
        assertEquals("com.yahoo.jdisc.bundle.ApplicationA", driver.application().getClass().getName());
        driver.close();
    }

    @Test
    public void requireThatPrivilegedBundleCanBeLoadedUnprivilegedOnABestEffortBasis() throws Exception {
        MyModule module = new MyModule();
        TestDriver driver = TestDriver.newApplicationBundleInstance("app-b-priv.jar",
                                                                    false, module);
        assertTrue(module.init.await(60, TimeUnit.SECONDS));
        assertEquals("com.yahoo.jdisc.bundle.ApplicationB", driver.application().getClass().getName());
        driver.close();
    }

    @Test
    public void requireThatPrivilegedBundleCanBeLoadedPrivileged() throws Exception {
        MyModule module = new MyModule();
        TestDriver driver = TestDriver.newApplicationBundleInstance("app-b-priv.jar",
                                                                    true, module);
        assertTrue(module.init.await(60, TimeUnit.SECONDS));
        assertEquals("com.yahoo.jdisc.bundle.ApplicationB", driver.application().getClass().getName());
        driver.close();
    }

    @Test
    public void requireThatInstallBundleInstructionWorks() throws Exception {
        TestDriver driver = TestDriver.newApplicationBundleInstance("app-ca.jar", true);
        assertEquals("com.yahoo.jdisc.bundle.ApplicationC", driver.application().getClass().getName());
        driver.close();
    }

    @Test
    public void requireThatInstallBundleInstructionDoesNotIgnorePrivilegedActivatorOfDependencies() throws Exception {
        TestDriver driver = TestDriver.newApplicationBundleInstance("app-dj.jar", true);
        driver.close();
    }

    @Test
    public void requireThatInstallBundleInstructionPropagatesPrivileges() throws Exception {
        TestDriver driver = TestDriver.newApplicationBundleInstance("app-ej-priv.jar", true);
        assertEquals("com.yahoo.jdisc.bundle.ApplicationE", driver.application().getClass().getName());
        driver.close();
    }

    private static class MyModule extends AbstractModule {

        final CountDownLatch init = new CountDownLatch(1);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch stop = new CountDownLatch(1);
        final CountDownLatch destroy = new CountDownLatch(1);

        @Override
        protected void configure() {
            bind(CountDownLatch.class).annotatedWith(Names.named("Init")).toInstance(init);
            bind(CountDownLatch.class).annotatedWith(Names.named("Start")).toInstance(start);
            bind(CountDownLatch.class).annotatedWith(Names.named("Stop")).toInstance(stop);
            bind(CountDownLatch.class).annotatedWith(Names.named("Destroy")).toInstance(destroy);
        }
    }
}
