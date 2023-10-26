// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.jdisc.application.OsgiFramework;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;

import java.util.Collection;
import java.util.List;

/**
 * @author Tony Vaagenes
 */
public final class DisableOsgiFramework implements OsgiFramework {

    private final RestrictedBundleContext restrictedBundleContext;

    public DisableOsgiFramework() {
        this.restrictedBundleContext = null;
    }

    public DisableOsgiFramework(RestrictedBundleContext restrictedBundleContext) {
        this.restrictedBundleContext = restrictedBundleContext;
    }

    @Override
    public List<Bundle> installBundle(String bundleLocation) throws BundleException {
        throw newException();
    }

    @Override
    public void startBundles(List<Bundle> bundles, boolean privileged) throws BundleException {
        throw newException();
    }

    @Override
    public void refreshPackages() {
        throw newException();
    }

    @Override
    public BundleContext bundleContext() {
        if (restrictedBundleContext == null) {
            throw newException();
        }
        return restrictedBundleContext;
    }

    @Override
    public List<Bundle> bundles() {
        throw newException();
    }

    @Override
    public List<Bundle> getBundles(Bundle requestingBundle) {
        throw newException();
    }

    @Override
    public void allowDuplicateBundles(Collection<Bundle> bundles) {
        throw newException();
    }

    @Override
    public void start() throws BundleException {
        throw newException();
    }

    @Override
    public void stop() throws BundleException {
        throw newException();
    }

    private RuntimeException newException() {
        return new UnsupportedOperationException("The OSGi framework is not available to components.");
    }

}
