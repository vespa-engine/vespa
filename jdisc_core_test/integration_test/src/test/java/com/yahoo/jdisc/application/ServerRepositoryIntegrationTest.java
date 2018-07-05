// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class ServerRepositoryIntegrationTest {

    @Test
    public void requireThatInstallFromBundleWorks() throws Exception {
        MyModule module = new MyModule();
        TestDriver driver = TestDriver.newSimpleApplicationInstance(module);
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("my-server-provider.jar").get(0);
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverProviders().install(bundle, "com.yahoo.jdisc.bundle.MyServerProvider");
        assertTrue(module.init.await(60, TimeUnit.SECONDS));

        Iterator<ServerProvider> it = builder.serverProviders().iterator();
        assertTrue(it.hasNext());
        ServerProvider server = it.next();
        assertNotNull(server);
        server.start();
        assertTrue(module.start.await(60, TimeUnit.SECONDS));
        server.close();
        assertTrue(module.close.await(60, TimeUnit.SECONDS));
        server.release();
        assertTrue(module.destroy.await(60, TimeUnit.SECONDS));
        assertFalse(it.hasNext());
        driver.close();
    }

    @Test
    public void requireThatInstallAllFromBundleWorks() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance(new MyModule());
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("my-server-provider.jar").get(0);
        ContainerBuilder builder = driver.newContainerBuilder();
        builder.serverProviders().installAll(bundle, Arrays.asList("com.yahoo.jdisc.bundle.MyServerProvider",
                                                                   "com.yahoo.jdisc.bundle.MyServerProvider"));
        Iterator<ServerProvider> it = builder.serverProviders().iterator();
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        assertTrue(it.hasNext());
        assertNotNull(it.next());
        assertFalse(it.hasNext());
        driver.close();
    }

    @Test
    public void requireThatInstallUnknownClassThrows() throws Exception {
        MyModule module = new MyModule();
        TestDriver driver = TestDriver.newSimpleApplicationInstance(module);
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("my-server-provider.jar").get(0);
        ContainerBuilder builder = driver.newContainerBuilder();
        try {
            builder.serverProviders().install(bundle, "class.not.Found");
            fail();
        } catch (ClassNotFoundException e) {

        }
        driver.close();
    }

    private static class MyModule extends AbstractModule {

        final CountDownLatch init = new CountDownLatch(1);
        final CountDownLatch start = new CountDownLatch(1);
        final CountDownLatch close = new CountDownLatch(1);
        final CountDownLatch destroy = new CountDownLatch(1);

        @Override
        protected void configure() {
            bind(CountDownLatch.class).annotatedWith(Names.named("Init")).toInstance(init);
            bind(CountDownLatch.class).annotatedWith(Names.named("Start")).toInstance(start);
            bind(CountDownLatch.class).annotatedWith(Names.named("Close")).toInstance(close);
            bind(CountDownLatch.class).annotatedWith(Names.named("Destroy")).toInstance(destroy);
        }
    }
}
