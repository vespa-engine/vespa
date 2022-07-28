// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.osgi.Osgi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class PlatformBundleLoaderTest {

    private static final String BUNDLE_1_REF = "bundle-1";
    private static final Bundle BUNDLE_1 = new TestBundle(BUNDLE_1_REF);
    private static final String BUNDLE_2_REF = "bundle-2";
    private static final Bundle BUNDLE_2 = new TestBundle(BUNDLE_2_REF);

    private PlatformBundleLoader bundleLoader;
    private TestOsgi osgi;

    @BeforeEach
    public void setup() {
        osgi = new TestOsgi(testBundles());
        bundleLoader = new PlatformBundleLoader(osgi, new TestBundleInstaller());
    }

    @Test
    void bundles_are_installed_and_started() {
        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        assertEquals(1, osgi.getInstalledBundles().size());

        // The bundle is installed and started
        TestBundle installedBundle = (TestBundle) osgi.getInstalledBundles().get(0);
        assertEquals(BUNDLE_1.getSymbolicName(), installedBundle.getSymbolicName());
        assertTrue(installedBundle.started);
    }

    @Test
    void bundles_cannot_be_added_by_later_calls() {
        bundleLoader.useBundles(List.of(BUNDLE_1_REF));
        bundleLoader.useBundles(List.of(BUNDLE_2_REF));  // Should be a NOP

        assertEquals(1, osgi.getInstalledBundles().size());
        assertEquals(BUNDLE_1.getSymbolicName(), osgi.getInstalledBundles().get(0).getSymbolicName());
    }

    private static Map<String, Bundle> testBundles() {
        return Map.of(BUNDLE_1_REF, BUNDLE_1,
                      BUNDLE_2_REF, BUNDLE_2);
    }

    static class TestBundleInstaller extends DiskBundleInstaller {
        @Override
        public List<Bundle> installBundles(String bundlePath, Osgi osgi) {
            return osgi.install(bundlePath);
        }
    }
}
