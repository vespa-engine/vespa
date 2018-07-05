// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Inject;
import com.yahoo.jdisc.service.CurrentContainer;
import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class AbstractApplicationTestCase {

    @Test
    public void requireThatBundleInstallerCanBeAccessed() throws BundleException {
        TestDriver driver = TestDriver.newInjectedApplicationInstance(MyApplication.class);
        MyApplication app = (MyApplication)driver.application();
        List<Bundle> lst = app.installAndStartBundle("cert-a.jar");
        assertEquals(1, lst.size());
        assertEquals("com.yahoo.vespa.jdisc_core.cert-a", lst.get(0).getSymbolicName());
        app.stopAndUninstallBundle(lst.get(0));
        assertTrue(driver.close());
    }

    private static class MyApplication extends AbstractApplication {

        @Inject
        public MyApplication(BundleInstaller bundleInstaller, ContainerActivator activator,
                             CurrentContainer container) {
            super(bundleInstaller, activator, container);
        }

        @Override
        public void start() {

        }
    }
}
