// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class BundleActivatorIntegrationTest {

    @Test
    public void requireThatBundleActivatorHasAccessToCurrentContainer() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        OsgiFramework osgi = driver.osgiFramework();
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("my-bundle-activator.jar").get(0);
        Class<?> serviceClass = bundle.loadClass("com.yahoo.jdisc.bundle.MyService");
        assertNotNull(serviceClass);
        BundleContext ctx = osgi.bundleContext();
        ServiceReference<?> serviceRef = ctx.getServiceReference(serviceClass.getName());
        assertNotNull(serviceRef);
        Object service = ctx.getService(serviceRef);
        assertNotNull(service);
        assertTrue(serviceClass.isInstance(service));
        assertTrue(driver.close());
    }

    @Test
    public void requireThatApplicationBundleActivatorHasAccessToCurrentContainer() throws Exception {
        TestDriver driver = TestDriver.newApplicationBundleInstance("app-g-act.jar", false);
        OsgiFramework osgi = driver.osgiFramework();
        Class<?> serviceClass = osgi.bundles().get(1).loadClass("com.yahoo.jdisc.bundle.MyService");
        assertNotNull(serviceClass);
        BundleContext ctx = osgi.bundleContext();
        ServiceReference<?> serviceRef = ctx.getServiceReference(serviceClass.getName());
        assertNotNull(serviceRef);
        Object service = ctx.getService(serviceRef);
        assertNotNull(service);
        assertTrue(serviceClass.isInstance(service));
        assertTrue(driver.close());
    }
}
