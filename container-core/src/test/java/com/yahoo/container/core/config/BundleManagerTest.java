package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import org.junit.Before;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class BundleManagerTest {

    private static final FileReference BUNDLE_1_REF = new FileReference("bundle-1");
    private static final Bundle BUNDLE_1 = new TestBundle(BUNDLE_1_REF.value());
    private static final FileReference BUNDLE_2_REF = new FileReference("bundle-2");
    private static final Bundle BUNDLE_2 = new TestBundle(BUNDLE_2_REF.value());

    private BundleManager bundleLoader;
    private TestOsgi osgi;

    @Before
    public void setup() {
        osgi = new TestOsgi(testBundles());
        var bundleInstaller = new TestBundleInstaller();
        bundleLoader = new BundleManager(osgi);
        bundleLoader.useCustomBundleInstaller(bundleInstaller);
    }

    @Test
    public void bundles_are_installed_and_started() {
        bundleLoader.use(List.of(BUNDLE_1_REF));
        assertEquals(1, osgi.getInstalledBundles().size());

        // The bundle is installed and started
        TestBundle installedBundle = (TestBundle)osgi.getInstalledBundles().get(0);
        assertEquals(BUNDLE_1.getSymbolicName(), installedBundle.getSymbolicName());
        assertTrue(installedBundle.started);

        // The file reference is active
        assertEquals(1, bundleLoader.getActiveFileReferences().size());
        assertEquals(BUNDLE_1_REF, bundleLoader.getActiveFileReferences().get(0));
    }

    @Test
    public void new_bundle_can_be_installed_in_reconfig() {
        bundleLoader.use(List.of(BUNDLE_1_REF));
        Set<Bundle> obsoleteBundles = bundleLoader.use(List.of(BUNDLE_1_REF, BUNDLE_2_REF));

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
    public void unused_bundle_is_marked_obsolete_after_reconfig() {
        bundleLoader.use(List.of(BUNDLE_1_REF));
        Set<Bundle> obsoleteBundles = bundleLoader.use(List.of(BUNDLE_2_REF));

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


    private static Map<String, Bundle> testBundles() {
        return Map.of(BUNDLE_1_REF.value(), BUNDLE_1,
                      BUNDLE_2_REF.value(), BUNDLE_2);
    }

}
