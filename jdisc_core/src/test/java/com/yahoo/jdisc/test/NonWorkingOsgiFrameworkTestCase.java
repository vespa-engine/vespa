// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.application.OsgiFramework;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;


/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingOsgiFrameworkTestCase {

    @Test
    void requireThatFrameworkCanStartAndStop() throws BundleException {
        OsgiFramework osgi = new NonWorkingOsgiFramework();
        osgi.start();
        osgi.stop();
    }

    @Test
    void requireThatFrameworkHasNoBundles() throws BundleException {
        OsgiFramework osgi = new NonWorkingOsgiFramework();
        assertTrue(osgi.bundles().isEmpty());
    }

    @Test
    void requireThatFrameworkHasNoBundleContext() {
        OsgiFramework osgi = new NonWorkingOsgiFramework();
        assertNull(osgi.bundleContext());
    }

    @Test
    void requireThatFrameworkThrowsOnInstallBundle() throws BundleException {
        OsgiFramework osgi = new NonWorkingOsgiFramework();
        try {
            osgi.installBundle("file:bundle.jar");
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    void requireThatFrameworkThrowsOnStartBundles() throws BundleException {
        OsgiFramework osgi = new NonWorkingOsgiFramework();
        try {
            osgi.startBundles(Collections.<Bundle>emptyList(), false);
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }

    @Test
    void requireThatFrameworkThrowsOnRefreshPackages() throws BundleException, InterruptedException {
        OsgiFramework osgi = new NonWorkingOsgiFramework();
        try {
            osgi.refreshPackages();
            fail();
        } catch (UnsupportedOperationException e) {
            // expected
        }
    }
}
