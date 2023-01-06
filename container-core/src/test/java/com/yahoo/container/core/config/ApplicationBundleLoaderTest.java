// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.container.di.Osgi.GenerationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

import java.util.Collection;
import java.util.List;

import static com.yahoo.container.core.config.BundleTestUtil.BUNDLE_1;
import static com.yahoo.container.core.config.BundleTestUtil.BUNDLE_1_REF;
import static com.yahoo.container.core.config.BundleTestUtil.BUNDLE_2;
import static com.yahoo.container.core.config.BundleTestUtil.BUNDLE_2_REF;
import static com.yahoo.container.core.config.BundleTestUtil.testBundles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class ApplicationBundleLoaderTest {

    private ApplicationBundleLoader bundleLoader;
    private TestOsgi osgi;

    @BeforeEach
    public void setup() {
        osgi = new TestOsgi(testBundles());
        bundleLoader = osgi.bundleLoader();
    }

    @Test
    void bundles_are_installed_and_started() {
        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        assertEquals(1, osgi.getInstalledBundles().size());

        // The bundle is installed and started
        TestBundle installedBundle = (TestBundle) osgi.getInstalledBundles().get(0);
        assertEquals(BUNDLE_1.getSymbolicName(), installedBundle.getSymbolicName());
        assertTrue(installedBundle.started);

        // The file reference is active
        assertEquals(1, bundleLoader.getActiveFileReferences().size());
        assertEquals(BUNDLE_1_REF, bundleLoader.getActiveFileReferences().get(0));
    }

    @Test
    void generation_must_be_marked_complete_before_using_new_bundles() {
        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        assertThrows(IllegalStateException.class,
                     () -> bundleLoader.useBundles(List.of(BUNDLE_1_REF)));
    }

    @Test
    void no_bundles_are_marked_obsolete_upon_reconfig_with_unchanged_bundles() {
        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        bundleLoader.completeGeneration(GenerationStatus.SUCCESS);

        bundleLoader.useBundles(List.of(BUNDLE_1_REF, BUNDLE_2_REF));
        Collection<Bundle> obsoleteBundles = bundleLoader.completeGeneration(GenerationStatus.SUCCESS);

        // No bundles are obsolete
        assertTrue(obsoleteBundles.isEmpty());
    }

    @Test
    void new_bundle_can_be_installed_in_reconfig() {
        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        bundleLoader.completeGeneration(GenerationStatus.SUCCESS);

        bundleLoader.useBundles(List.of(BUNDLE_1_REF, BUNDLE_2_REF));
        Collection<Bundle> obsoleteBundles = bundleLoader.completeGeneration(GenerationStatus.SUCCESS);

        // No bundles are obsolete
        assertTrue(obsoleteBundles.isEmpty());

        // Both bundles are installed
        assertEquals(2, osgi.getInstalledBundles().size());
        assertEquals(BUNDLE_1.getSymbolicName(), osgi.getInstalledBundles().get(0).getSymbolicName());
        assertEquals(BUNDLE_2.getSymbolicName(), osgi.getInstalledBundles().get(1).getSymbolicName());

        // Both bundles are current
        assertEquals(2, osgi.getCurrentBundles().size());
        assertEquals(BUNDLE_1.getSymbolicName(), osgi.getCurrentBundles().get(0).getSymbolicName());
        assertEquals(BUNDLE_2.getSymbolicName(), osgi.getCurrentBundles().get(1).getSymbolicName());

        // Both file references are active
        assertEquals(2, bundleLoader.getActiveFileReferences().size());
        assertEquals(BUNDLE_1_REF, bundleLoader.getActiveFileReferences().get(0));
        assertEquals(BUNDLE_2_REF, bundleLoader.getActiveFileReferences().get(1));
    }

    @Test
    void unused_bundle_is_marked_obsolete_after_reconfig() {
        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        bundleLoader.completeGeneration(GenerationStatus.SUCCESS);

        bundleLoader.useBundles(List.of(BUNDLE_2_REF));
        Collection<Bundle> obsoleteBundles = bundleLoader.completeGeneration(GenerationStatus.SUCCESS);

        // The returned set of obsolete bundles contains bundle-1
        assertEquals(1, obsoleteBundles.size());
        assertEquals(BUNDLE_1.getSymbolicName(), obsoleteBundles.iterator().next().getSymbolicName());

        // Both bundles are installed
        assertEquals(2, osgi.getInstalledBundles().size());
        assertEquals(BUNDLE_1.getSymbolicName(), osgi.getInstalledBundles().get(0).getSymbolicName());
        assertEquals(BUNDLE_2.getSymbolicName(), osgi.getInstalledBundles().get(1).getSymbolicName());

        // Only bundle-2 is current
        assertEquals(1, osgi.getCurrentBundles().size());
        assertEquals(BUNDLE_2.getSymbolicName(), osgi.getCurrentBundles().get(0).getSymbolicName());

        // Only the bundle-2 file reference is active
        assertEquals(1, bundleLoader.getActiveFileReferences().size());
        assertEquals(BUNDLE_2_REF, bundleLoader.getActiveFileReferences().get(0));
    }


    @Test
    void previous_generation_can_be_restored_after_a_failed_reconfig() {
        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        bundleLoader.completeGeneration(GenerationStatus.SUCCESS);

        bundleLoader.useBundles(List.of(BUNDLE_2_REF));

        // Report the generation as a failure.
        Collection<Bundle> bundlesToUninstall = bundleLoader.completeGeneration(GenerationStatus.FAILURE);

        assertEquals(1, bundlesToUninstall.size());
        assertEquals(BUNDLE_2.getSymbolicName(), bundlesToUninstall.iterator().next().getSymbolicName());

        // The bundle from the failed generation is not seen as current. It will still be installed,
        // as uninstalling is handled by the Deconstructor, not included in this test setup.
        assertEquals(1, osgi.getCurrentBundles().size());
        assertEquals(BUNDLE_1.getSymbolicName(), osgi.getCurrentBundles().get(0).getSymbolicName());

        // Only the bundle-1 file reference is active, bundle-2 is removed.
        assertEquals(1, bundleLoader.getActiveFileReferences().size());
        assertEquals(BUNDLE_1_REF, bundleLoader.getActiveFileReferences().get(0));
    }

    @Test
    void bundles_from_committed_config_generation_are_not_uninstalled_upon_future_failed_reconfig() {
        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        bundleLoader.completeGeneration(GenerationStatus.SUCCESS);

        // Revert to the previous generation, as will be done upon a failed reconfig.
        Collection<Bundle> bundlesToUninstall = bundleLoader.completeGeneration(GenerationStatus.FAILURE);

        assertEquals(0, bundlesToUninstall.size());
        assertEquals(1, osgi.getCurrentBundles().size());

        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        Collection<Bundle> obsoleteBundles = bundleLoader.completeGeneration(GenerationStatus.SUCCESS);
        assertTrue(obsoleteBundles.isEmpty());
        assertEquals(1, osgi.getCurrentBundles().size());
    }

}
