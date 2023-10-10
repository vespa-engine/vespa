// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.LinkedList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Simon Thoresen Hult
 */
public class BundleInstallerIntegrationTest {

    @Test
    public void requireThatInstallWorks() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        List<Bundle> prev = new LinkedList<>(driver.osgiFramework().bundles());

        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("cert-a.jar").get(0);
        assertNotNull(bundle);
        assertEquals("com.yahoo.vespa.jdisc_core.cert-a", bundle.getSymbolicName());

        boolean found = false;
        for (Bundle entry : driver.osgiFramework().bundles()) {
            assertNotNull(entry);
            if (prev.remove(entry)) {
                continue;
            }
            assertFalse(found);
            assertSame(bundle, entry);
            found = true;
        }
        assertTrue(prev.isEmpty());
        assertTrue(found);
        driver.close();
    }

    @Test
    public void requireThatInstallAllWorks() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        List<Bundle> bundles = installer.installAndStart(
                "cert-a.jar",
                "cert-b.jar");
        assertNotNull(bundles);
        assertEquals(2, bundles.size());
        driver.close();
    }

    @Test
    public void requireThatUninstallWorks() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        List<Bundle> prev = new LinkedList<>(driver.osgiFramework().bundles());

        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("cert-a.jar").get(0);
        assertNotNull(bundle);
        installer.stopAndUninstall(bundle);

        for (Bundle entry : driver.osgiFramework().bundles()) {
            assertTrue(prev.remove(entry));
        }
        assertTrue(prev.isEmpty());
        driver.close();
    }

    @Test
    public void requireThatUninstallAllWorks() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        List<Bundle> prev = new LinkedList<>(driver.osgiFramework().bundles());

        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        List<Bundle> bundles = installer.installAndStart(
                "cert-a.jar",
                "cert-b.jar");
        assertNotNull(bundles);
        installer.stopAndUninstall(bundles);

        List<Bundle> next = new LinkedList<>(driver.osgiFramework().bundles());
        next.removeAll(prev);
        assertTrue(next.isEmpty());
        driver.close();
    }

    @Test
    public void requireThatUninstallUninstalledThrowsException() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("cert-a.jar").get(0);
        assertNotNull(bundle);
        installer.stopAndUninstall(bundle);
        try {
            installer.stopAndUninstall(bundle);
            fail();
        } catch (BundleException e) {
            assertEquals("OSGi bundle com.yahoo.vespa.jdisc_core.cert-a not started.",
                         e.getMessage());
        }
        driver.close();
    }

    @Test
    public void requireThatInstallInstalledThrowsException() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        Bundle bundle = installer.installAndStart("cert-a.jar").get(0);
        assertNotNull(bundle);
        try {
            installer.installAndStart("cert-a.jar");
            fail();
        } catch (BundleException e) {
            assertEquals("OSGi bundle com.yahoo.vespa.jdisc_core.cert-a already started.",
                         e.getMessage());
        }
        driver.close();
    }

    @Test
    public void requireThatApplicationInstructionThrowsException() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        try {
            installer.installAndStart("app-a.jar");
            fail();
        } catch (BundleException e) {
            assertEquals("OSGi header 'X-JDisc-Application' not allowed for non-application bundle " +
                         "com.yahoo.vespa.jdisc_core.app-a.", e.getMessage());
        }
        driver.close();
    }

    @Test
    public void requireThatPrivilegedActivatorInstructionButNoRootPrivilegesKindOfWorksOnABestEffortBasis() throws Exception {
        TestDriver driver = TestDriver.newSimpleApplicationInstance();
        BundleInstaller installer = new BundleInstaller(driver.osgiFramework());
        installer.installAndStart("cert-j-priv.jar");
        driver.close();
    }

    @Test
    public void requireThatInstallExceptionContainsInstalledBundles() throws BundleException {
        OsgiFramework osgi = TestDriver.newOsgiFramework();
        osgi.start();
        BundleInstaller installer = new BundleInstaller(osgi);
        assertEquals(1, osgi.bundles().size());
        try {
            installer.installAndStart("cert-a.jar", "cert-tp.jar");
            fail();
        } catch (BundleException e) {
            assertTrue(e instanceof BundleInstallationException);
            List<Bundle> bundles = ((BundleInstallationException)e).installedBundles();
            assertEquals(2, bundles.size());
            assertEquals("com.yahoo.vespa.jdisc_core.cert-a", bundles.get(0).getSymbolicName());
            assertEquals("com.yahoo.vespa.jdisc_core.cert-tp", bundles.get(1).getSymbolicName());
        }
        assertEquals(3, osgi.bundles().size()); // did not clean up the installed bundles inside the catch-block
        osgi.stop();
    }

    @Test
    public void requireThatStartExceptionContainsInstalledBundles() throws BundleException {
        OsgiFramework osgi = TestDriver.newOsgiFramework();
        osgi.start();
        osgi.bundleContext().registerService(RuntimeException.class, new RuntimeException(), null);
        BundleInstaller installer = new BundleInstaller(osgi);
        assertEquals(1, osgi.bundles().size());
        try {
            installer.installAndStart("cert-a.jar", "cert-us.jar");
            fail();
        } catch (BundleException e) {
            assertTrue(e instanceof BundleInstallationException);
            List<Bundle> bundles = ((BundleInstallationException)e).installedBundles();
            assertEquals(3, bundles.size());
            assertEquals("com.yahoo.vespa.jdisc_core.cert-a", bundles.get(0).getSymbolicName());
            assertEquals("com.yahoo.vespa.jdisc_core.cert-us", bundles.get(1).getSymbolicName());
            assertEquals("com.yahoo.vespa.jdisc_core.cert-s-act", bundles.get(2).getSymbolicName());
        }
        assertEquals(4, osgi.bundles().size()); // did not clean up the installed bundles inside the catch-block
        osgi.stop();
    }
}
