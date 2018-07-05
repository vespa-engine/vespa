// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.name.Names;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author Simon Thoresen Hult
 */
public class GuiceRepositoryIntegrationTest {

    @Test
    public void requireThatInstallFromBundleWorks() throws Exception {
        MyModule module = new MyModule();
        TestDriver driver = TestDriver.newSimpleApplicationInstance(module);
        ContainerBuilder builder = driver.newContainerBuilder();
        List<Module> prev = new LinkedList<>(builder.guiceModules().collection());

        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("my-guice-module.jar").get(0);
        builder.guiceModules().install(bundle, "com.yahoo.jdisc.bundle.MyGuiceModule");
        List<Module> next = new LinkedList<>(builder.guiceModules().collection());
        next.removeAll(prev);

        assertEquals(1, next.size());
        assertTrue(module.initLatch.await(60, TimeUnit.SECONDS));
        assertNotNull(builder.guiceModules().getInstance(Injector.class));
        assertTrue(module.configLatch.await(60, TimeUnit.SECONDS));

        driver.close();
    }

    @Test
    public void requireThatInstallAllFromBundleWorks() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance(new MyModule());
        ContainerBuilder builder = driver.newContainerBuilder();
        List<Module> prev = new LinkedList<>(builder.guiceModules().collection());

        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("my-guice-module.jar").get(0);
        builder.guiceModules().installAll(bundle, Arrays.asList("com.yahoo.jdisc.bundle.MyGuiceModule",
                                                                "com.yahoo.jdisc.bundle.MyGuiceModule"));
        List<Module> next = new LinkedList<>(builder.guiceModules().collection());
        next.removeAll(prev);

        assertEquals(2, next.size());
        driver.close();
    }

    @Test
    public void requireThatInstallUnknowClassThrows() throws BundleException, ClassNotFoundException {
        TestDriver driver = TestDriver.newSimpleApplicationInstance(new MyModule());
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("my-guice-module.jar").get(0);

        ContainerBuilder builder = driver.newContainerBuilder();
        try {
            builder.guiceModules().install(bundle, "class.not.Found");
            fail();
        } catch (ClassNotFoundException e) {

        }
        driver.close();
    }

    private static class MyModule extends AbstractModule {

        final CountDownLatch initLatch = new CountDownLatch(1);
        final CountDownLatch configLatch = new CountDownLatch(1);

        @Override
        protected void configure() {
            bind(CountDownLatch.class).annotatedWith(Names.named("Init")).toInstance(initLatch);
            bind(CountDownLatch.class).annotatedWith(Names.named("Config")).toInstance(configLatch);
        }
    }
}
