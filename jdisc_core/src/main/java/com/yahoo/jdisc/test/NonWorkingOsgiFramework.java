// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.test;

import com.yahoo.jdisc.application.OsgiFramework;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Simon Thoresen Hult
 */
public class NonWorkingOsgiFramework implements OsgiFramework {

    @Override
    public List<Bundle> installBundle(String bundleLocation) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void startBundles(List<Bundle> bundles, boolean privileged) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void refreshPackages() {
        throw new UnsupportedOperationException();
    }

    @Override
    public BundleContext bundleContext() {
        return null;
    }

    @Override
    public List<Bundle> bundles() {
        return Collections.emptyList();
    }

    @Override
    public List<Bundle> getBundles(Bundle requestingBundle) {
        return Collections.emptyList();
    }

    @Override
    public void allowDuplicateBundles(Collection<Bundle> bundles) { }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }
}
