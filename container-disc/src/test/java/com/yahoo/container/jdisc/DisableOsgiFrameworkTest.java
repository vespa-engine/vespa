// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import org.junit.Test;
import org.osgi.framework.BundleException;

/**
 * @author Ulf Lilleengen
 */
public class DisableOsgiFrameworkTest {

    @Test(expected = RuntimeException.class)
    public void require_that_installBundle_throws_exception() throws BundleException {
        new DisableOsgiFramework().installBundle("foo");
    }

    @Test(expected = RuntimeException.class)
    public void require_that_startBundles_throws_exception() throws BundleException {
        new DisableOsgiFramework().startBundles(null, true);
    }

    @Test(expected = RuntimeException.class)
    public void require_that_bundleContext_throws_exception() throws BundleException {
        new DisableOsgiFramework().bundleContext();
    }

    @Test(expected = RuntimeException.class)
    public void require_that_refreshPackages_throws_exception() {
        new DisableOsgiFramework().refreshPackages();
    }

    @Test(expected = RuntimeException.class)
    public void require_that_bundles_throws_exception() {
        new DisableOsgiFramework().bundles();
    }

    @Test(expected = RuntimeException.class)
    public void require_that_start_throws_exception() throws BundleException {
        new DisableOsgiFramework().start();
    }

    @Test(expected = RuntimeException.class)
    public void require_that_stop_throws_exception() throws BundleException {
        new DisableOsgiFramework().stop();
    }

}
