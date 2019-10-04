package com.yahoo.jdisc.core;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.Collections;

import static com.yahoo.jdisc.core.FelixFrameworkIntegrationTest.startBundle;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Note that the '-dup' bundles are necessary, because felix ignores duplicate bundles with the same location.
 *
 * @author gjoranv
 */
public class BundleCollisionHookIntegrationTest {

    private FelixFramework felix;

    @Before
    public void startFelix() throws  Exception {
        felix = TestDriver.newOsgiFramework();
        felix.start();
    }

    @After
    public void stopFelix() throws Exception {
        felix.stop();
    }

    @Test
    public void duplicate_bundles_cannot_be_installed_in_general() throws Exception {
        startBundle(felix, "cert-l1.jar");
        try {
            startBundle(felix, "cert-l1-dup.jar");
            fail("Expected exception due to duplicate bundles");
        } catch (BundleException e) {
            assertTrue(e.getMessage().contains("Bundle symbolic name and version are not unique"));
        }
    }

    @Test
    public void duplicate_bundles_can_be_installed_if_bsn_version_is_whitelisted() throws Exception {
        Bundle bundleL = startBundle(felix, "cert-l1.jar");
        felix.allowDuplicateBundles(Collections.singleton(bundleL));

        Bundle bundleLdup = startBundle(felix, "cert-l1-dup.jar");
        assertNotEquals(bundleL.getBundleId(), bundleLdup.getBundleId());
    }

    @Test
    public void duplicates_whitelist_is_updated_when_bundles_are_uninstalled() throws Exception {
        Bundle bundleL = startBundle(felix, "cert-l1.jar");
        felix.allowDuplicateBundles(Collections.singleton(bundleL));

        startBundle(felix, "cert-l1-dup.jar");

        // Remove the original bundle -> will also remove the allowed duplicate
        bundleL.uninstall();

        // Trigger error by trying to re-install the bundle while the duplicate remains
        try {
            startBundle(felix, "cert-l1.jar");
            fail("Expected exception due to duplicate bundles");
        } catch (BundleException e) {
            assertTrue(e.getMessage().contains("Bundle symbolic name and version are not unique"));
        }

    }

    @Test
    public void duplicates_whitelist_can_be_updated_with_additional_bundles() throws Exception {
        Bundle bundleL = startBundle(felix, "cert-l1.jar");
        Bundle bundleMl = startBundle(felix, "cert-ml.jar");
        felix.allowDuplicateBundles(Collections.singleton(bundleL));
        felix.allowDuplicateBundles(Collections.singleton(bundleMl));

        startBundle(felix, "cert-l1-dup.jar");
        startBundle(felix, "cert-ml-dup.jar");
    }

}
