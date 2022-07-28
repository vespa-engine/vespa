// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import org.junit.jupiter.api.Test;
import org.osgi.framework.BundleException;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author Ulf Lilleengen
 */
public class DisableOsgiFrameworkTest {

    @Test
    void require_that_installBundle_throws_exception() throws BundleException {
        assertThrows(RuntimeException.class, () -> {
            new DisableOsgiFramework().installBundle("foo");
        });
    }

    @Test
    void require_that_startBundles_throws_exception() throws BundleException {
        assertThrows(RuntimeException.class, () -> {
            new DisableOsgiFramework().startBundles(null, true);
        });
    }

    @Test
    void require_that_bundleContext_throws_exception() throws BundleException {
        assertThrows(RuntimeException.class, () -> {
            new DisableOsgiFramework().bundleContext();
        });
    }

    @Test
    void require_that_refreshPackages_throws_exception() {
        assertThrows(RuntimeException.class, () -> {
            new DisableOsgiFramework().refreshPackages();
        });
    }

    @Test
    void require_that_bundles_throws_exception() {
        assertThrows(RuntimeException.class, () -> {
            new DisableOsgiFramework().bundles();
        });
    }

    @Test
    void require_that_start_throws_exception() throws BundleException {
        assertThrows(RuntimeException.class, () -> {
            new DisableOsgiFramework().start();
        });
    }

    @Test
    void require_that_stop_throws_exception() throws BundleException {
        assertThrows(RuntimeException.class, () -> {
            new DisableOsgiFramework().stop();
        });
    }

}
