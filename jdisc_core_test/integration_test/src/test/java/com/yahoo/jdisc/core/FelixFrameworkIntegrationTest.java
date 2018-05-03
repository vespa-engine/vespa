// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.application.BundleInstallationException;
import com.yahoo.jdisc.test.TestDriver;
import org.apache.felix.framework.util.FelixConstants;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author <a href="mailto:simon@yahoo-inc.com">Simon Thoresen</a>
 */
public class FelixFrameworkIntegrationTest {

    @Test
    public void requireThatBundlesCanBeInstalled() throws BundleException {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();

        Bundle bundle = felix.installBundle("cert-a.jar").get(0);
        assertNotNull(bundle);

        Iterator<Bundle> it = felix.bundles().iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertEquals(FelixConstants.SYSTEM_BUNDLE_SYMBOLICNAME, it.next().getSymbolicName());
        assertTrue(it.hasNext());
        assertSame(bundle, it.next());
        assertFalse(it.hasNext());

        felix.stop();
    }

    @Test
    public void requireThatInstallBundleInstructionWorks() throws Exception {
        assertBundle("cert-ca.jar", "com.yahoo.jdisc.bundle.c.CertificateC",
                     "com.yahoo.vespa.jdisc_core.cert-ca",
                     "com.yahoo.vespa.jdisc_core.cert-a");
    }

    @Test
    public void requireThatInstallBundleInstructionResolvesTransitiveDepedencies() throws Exception {
        assertBundle("cert-dc.jar", "com.yahoo.jdisc.bundle.d.CertificateD",
                     "com.yahoo.vespa.jdisc_core.cert-dc",
                     "com.yahoo.vespa.jdisc_core.cert-ca",
                     "com.yahoo.vespa.jdisc_core.cert-a");
    }

    @Test
    public void requireThatInstallBundleInstructionResolvesAllTransitiveDepedencies() throws Exception {
        assertBundle("cert-eab.jar", "com.yahoo.jdisc.bundle.e.CertificateE",
                     "com.yahoo.vespa.jdisc_core.cert-eab",
                     "com.yahoo.vespa.jdisc_core.cert-a",
                     "com.yahoo.vespa.jdisc_core.cert-b");
    }

    @Test
    public void requireThatInstallBundleInstructionInstallsEachBundleOnlyOnce() throws Exception {
        assertBundle("cert-fac.jar", "com.yahoo.jdisc.bundle.f.CertificateF",
                     "com.yahoo.vespa.jdisc_core.cert-fac",
                     "com.yahoo.vespa.jdisc_core.cert-a",
                     "com.yahoo.vespa.jdisc_core.cert-ca");
    }

    @Test
    public void requireThatInstallBundleInstructionTracksLocationInCanonicalForm() throws Exception {
        assertBundle("cert-nac.jar", "com.yahoo.jdisc.bundle.n.CertificateN",
                     "com.yahoo.vespa.jdisc_core.cert-nac",
                     "com.yahoo.vespa.jdisc_core.cert-a",
                     "com.yahoo.vespa.jdisc_core.cert-ca");
    }

    @Test
    public void requireThatInstallBundleInstructionDetectsAutoDependency() throws Exception {
        assertBundle("cert-gg.jar", "com.yahoo.jdisc.bundle.g.CertificateG",
                     "com.yahoo.vespa.jdisc_core.cert-gg");
    }

    @Test
    public void requireThatInstallBundleInstructionDetectsCycles() throws Exception {
        assertBundle("cert-hi.jar", "com.yahoo.jdisc.bundle.h.CertificateH",
                     "com.yahoo.vespa.jdisc_core.cert-hi",
                     "com.yahoo.vespa.jdisc_core.cert-ih");
    }

    @Test
    public void requireThatSystemPackagesAreExported() throws Exception {
        assertBundle("cert-k-pkgs.jar", "com.yahoo.jdisc.bundle.k.CertificateK",
                     "com.yahoo.vespa.jdisc_core.cert-k-pkgs");
    }

    @Test
    public void requireThatBundlesCanBeRefreshed() throws Exception {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        Bundle bundleL = startBundle(felix, "cert-l1.jar");
        Bundle bundleM = startBundle(felix, "cert-ml.jar");
        assertEquals(1, callClass(bundleM, "com.yahoo.jdisc.bundle.m.CertificateM"));
        bundleL.uninstall();
        startBundle(felix, "cert-l2.jar");
        felix.refreshPackages();
        assertEquals(2, callClass(bundleM, "com.yahoo.jdisc.bundle.m.CertificateM"));
        felix.stop();
    }

    @Test
    public void requireThatBundlesCanBeRefreshedWithDisjunctRemovalClosure() throws Exception {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        Bundle bundleA = startBundle(felix, "cert-a.jar");
        Bundle bundleL = startBundle(felix, "cert-l1.jar");
        Bundle bundleM = startBundle(felix, "cert-ml.jar");
        assertEquals(1, callClass(bundleM, "com.yahoo.jdisc.bundle.m.CertificateM"));
        bundleA.uninstall();
        bundleL.uninstall();
        startBundle(felix, "cert-l2.jar");
        felix.refreshPackages();
        assertEquals(2, callClass(bundleM, "com.yahoo.jdisc.bundle.m.CertificateM"));
        felix.stop();
    }

    @Test
    public void requireThatJdiscBundlePathIsConfigurable() throws Exception {
        assertBundle("cert-oa-path.jar", "com.yahoo.jdisc.bundle.o.CertificateO",
                     "com.yahoo.vespa.jdisc_core.cert-oa-path",
                     "com.yahoo.vespa.jdisc_core.cert-a");
    }

    @Test
    public void requireThatBundleSymbolicNameIsRequired() throws Exception {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        try {
            felix.installBundle("cert-p-jar.jar");
            fail();
        } catch (BundleException e) {
            assertTrue(e.getMessage().contains("it might not be an OSGi bundle"));
        }
        felix.stop();
    }

    @Test
    public void requireThatBundleInstallationExceptionContainsInstalledBundles() throws Exception {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        assertEquals(1, felix.bundles().size());
        try {
            felix.installBundle("cert-tp.jar");
            fail();
        } catch (BundleException e) {
            assertTrue(e instanceof BundleInstallationException);
            List<Bundle> bundles = ((BundleInstallationException)e).installedBundles();
            assertEquals(1, bundles.size());
            assertEquals("com.yahoo.vespa.jdisc_core.cert-tp", bundles.get(0).getSymbolicName());
        }
        assertEquals(2, felix.bundles().size()); // did not clean up the installed bundle inside the catch-block
        felix.stop();
    }

    @Test
    public void requireThatFragmentBundlesCanBeInstalled() throws Exception {
        assertBundle("cert-q-frag.jar", null,
                     "com.yahoo.vespa.jdisc_core.cert-q-frag");
    }

    @Test
    public void requireThatFragmentBundlesCanBePreinstalled() throws Exception {
        assertBundle("cert-rq.jar", null,
                     "com.yahoo.vespa.jdisc_core.cert-rq",
                     "com.yahoo.vespa.jdisc_core.cert-q-frag");
    }

    private static Bundle startBundle(FelixFramework felix, String bundleLocation) throws BundleException {
        List<Bundle> lst = felix.installBundle(bundleLocation);
        assertEquals(1, lst.size());
        felix.startBundles(lst, false);
        return lst.get(0);
    }

    @SuppressWarnings({ "unchecked" })
    private static int callClass(Bundle bundle, String className) throws Exception {
        Class<?> certClass = bundle.loadClass(className);
        assertNotNull(certClass);
        Callable<Integer> cert = (Callable<Integer>)certClass.getDeclaredConstructor().newInstance();
        assertNotNull(cert);
        return cert.call();
    }

    private static void assertBundle(String bundleLocation, String className, String... expectedBundles)
            throws Exception {
        FelixFramework felix = TestDriver.newOsgiFramework();
        felix.start();
        try {
            List<Bundle> bundles = felix.installBundle(bundleLocation);
            List<String> actual = new LinkedList<>();
            for (Bundle bundle : bundles) {
                actual.add(bundle.getSymbolicName());
            }
            assertEquals(Arrays.asList(expectedBundles), actual);
            felix.startBundles(bundles, false);
            if (className != null) {
                assertNotNull(bundles.get(0).loadClass(className).getDeclaredConstructor().newInstance());
            }
        } finally {
            felix.stop();
        }
    }
}
