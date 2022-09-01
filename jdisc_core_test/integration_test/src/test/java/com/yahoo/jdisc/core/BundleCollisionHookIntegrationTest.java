// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.yahoo.jdisc.test.TestDriver;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.yahoo.jdisc.core.FelixFrameworkIntegrationTest.startBundle;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Note that the '-dup' bundles are necessary, because installing a bundle with the same location as an already
 * installed bundle is a NOP. From the OSGi spec: "Every bundle is uniquely identified by its location string."
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
    public void duplicates_whitelist_is_reset_upon_each_call() throws Exception {
        Bundle bundleL = startBundle(felix, "cert-l1.jar");
        Bundle bundleMl = startBundle(felix, "cert-ml.jar");
        felix.allowDuplicateBundles(Collections.singleton(bundleL));
        felix.allowDuplicateBundles(Collections.singleton(bundleMl));

        try {
            startBundle(felix, "cert-l1-dup.jar");
            fail("Expected exception due to duplicate bundles");
        } catch (BundleException e) {
            assertTrue(e.getMessage().contains("Bundle symbolic name and version are not unique"));
        }
    }

    @Test
    public void allowed_duplicates_are_visible_to_the_framework_and_vice_versa() throws  Exception {
        Bundle bundleA = startBundle(felix, "cert-a.jar");
        Bundle bundleB = startBundle(felix, "cert-b.jar");

        // Makes A and B visible to each other
        felix.allowDuplicateBundles(Set.of(bundleA, bundleB));

        List<Bundle> visibleBundles = felix.getBundles(bundleA);

        // The framework bundle should always be visible (the FindHook does not remove it)
        Bundle frameworkBundle = visibleBundles.get(0);
        assertTrue(isFrameworkBundle(frameworkBundle));

        // All bundles are always visible to the framework (this is according to the OSGi spec and handled by Felix)
        List<Bundle> visibleToFramework = felix.getBundles(frameworkBundle);
        assertEquals(3, visibleToFramework.size());
    }

    @Test
    public void allowed_duplicates_are_visible_to_its_own_members() throws  Exception {
        Bundle bundleA = startBundle(felix, "cert-a.jar");
        Bundle bundleB = startBundle(felix, "cert-b.jar");

        // Makes A and B visible to each other
        felix.allowDuplicateBundles(Set.of(bundleA, bundleB));

        List<Bundle> visibleBundles = felix.getBundles(bundleA);
        assertEquals(3, visibleBundles.size());
        assertSame(bundleA, visibleBundles.get(1));
        assertSame(bundleB, visibleBundles.get(2));
    }

    @Test
    public void allowed_duplicates_are_invisible_to_unrelated_bundles() throws  Exception {
        Bundle bundleL = startBundle(felix, "cert-l1.jar");
        Bundle bundleA = startBundle(felix, "cert-a.jar");

        // Makes L invisible to bundles outside the set of allowed duplicates
        felix.allowDuplicateBundles(Set.of(bundleL));

        List<Bundle> visibleBundles = felix.getBundles(bundleA);
        assertEquals(2, visibleBundles.size());
        assertTrue(isFrameworkBundle(visibleBundles.get(0)));
        assertSame(bundleA, visibleBundles.get(1));
    }

    @Test
    public void set_of_allowed_duplicates_are_invisible_to_all_bundles_of_which_they_are_duplicates() throws  Exception {
        Bundle bundleL = startBundle(felix, "cert-l1.jar");

        // Makes L invisible to bundles outside the set of allowed duplicates
        felix.allowDuplicateBundles(Set.of(bundleL));
        Bundle bundleL2 = startBundle(felix, "cert-l1-dup.jar");

        List<Bundle> visibleBundles = felix.getBundles(bundleL2);
        assertEquals(2, visibleBundles.size());
        assertSame(bundleL2, visibleBundles.get(1));
    }

    @Test
    public void allowed_duplicates_cannot_see_any_of_the_bundles_of_which_they_are_duplicates() throws  Exception {
        Bundle bundleL = startBundle(felix, "cert-l1.jar");

        // Makes L invisible to bundles outside the set of allowed duplicates
        felix.allowDuplicateBundles(Set.of(bundleL));
        Bundle invisibleToAllowedDuplicates = startBundle(felix, "cert-l1-dup.jar");

        List<Bundle> visibleToAllowedDuplicates = felix.getBundles(bundleL);
        assertEquals(2, visibleToAllowedDuplicates.size());
        assertSame(bundleL, visibleToAllowedDuplicates.get(1));
    }

    private boolean isFrameworkBundle(Bundle bundle) {
        return (bundle == felix.bundleContext().getBundle() && (bundle instanceof Framework));
    }

}
