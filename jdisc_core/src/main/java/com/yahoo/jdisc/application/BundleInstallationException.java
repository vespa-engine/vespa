// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.Collection;
import java.util.List;

/**
 * <p>This exception is thrown by {@link OsgiFramework#installBundle(String)} if installation failed. Because </p>
 *
 * <p>Please see commentary on {@link OsgiFramework#installBundle(String)} and {@link
 * OsgiFramework#startBundles(java.util.List, boolean)} for a description of exception-safety issues to consider when
 * installing bundles that use the {@link OsgiHeader#PREINSTALL_BUNDLE} manifest instruction.</p>
 *
 * @author Simon Thoresen Hult
 */
public final class BundleInstallationException extends BundleException {

    private final List<Bundle> installedBundles;

    public BundleInstallationException(Collection<Bundle> installedBundles, Throwable cause) {
        super(cause.getMessage(), cause);
        this.installedBundles = List.copyOf(installedBundles);
    }

    public List<Bundle> installedBundles() {
        return installedBundles;
    }
}
