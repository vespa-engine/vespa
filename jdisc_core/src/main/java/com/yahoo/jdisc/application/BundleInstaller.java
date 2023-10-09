// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.Inject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import static java.util.Collections.singletonList;

/**
 * <p>This is a utility class to help with installing, starting, stopping and uninstalling OSGi Bundles. You can choose
 * to inject an instance of this class, or it can be created explicitly by reference to a {@link OsgiFramework}.</p>
 *
 * <p>Please see commentary on {@link OsgiFramework#installBundle(String)} for a description of exception-safety issues
 * to consider when installing bundles that use the {@link OsgiHeader#PREINSTALL_BUNDLE} manifest instruction.</p>
 *
 * @author Simon Thoresen Hult
 */
public final class BundleInstaller {

    private final OsgiFramework osgiFramework;

    @Inject
    public BundleInstaller(OsgiFramework osgiFramework) {
        this.osgiFramework = osgiFramework;
    }

    public List<Bundle> installAndStart(String... locations) throws BundleException {
        return installAndStart(Arrays.asList(locations));
    }

    public List<Bundle> installAndStart(Iterable<String> locations) throws BundleException {
        List<Bundle> bundles = new LinkedList<>();
        try {
            for (String location : locations) {
                bundles.addAll(osgiFramework.installBundle(location));
            }
        } catch (BundleInstallationException e) {
            bundles.addAll(e.installedBundles());
            throw new BundleInstallationException(bundles, e);
        } catch (Exception e) {
            throw new BundleInstallationException(bundles, e);
        }
        try {
            for (Bundle bundle : bundles) {
                start(bundle);
            }
        } catch (Exception e) {
            throw new BundleInstallationException(bundles, e);
        }
        return bundles;
    }

    public void stopAndUninstall(Bundle... bundles) throws BundleException {
        stopAndUninstall(Arrays.asList(bundles));
    }

    public void stopAndUninstall(Iterable<Bundle> bundles) throws BundleException {
        for (Bundle bundle : bundles) {
            stop(bundle);
        }
        for (Bundle bundle : bundles) {
            bundle.uninstall();
        }
    }

    private void start(Bundle bundle) throws BundleException {
        if (bundle.getState() == Bundle.ACTIVE) {
            throw new BundleException("OSGi bundle " + bundle.getSymbolicName() + " already started.");
        }
        if (!OsgiHeader.asList(bundle, OsgiHeader.APPLICATION).isEmpty()) {
            throw new BundleException("OSGi header '" + OsgiHeader.APPLICATION + "' not allowed for " +
                                      "non-application bundle " + bundle.getSymbolicName() + ".");
        }
        osgiFramework.startBundles(singletonList(bundle), false);
    }

    private void stop(Bundle bundle) throws BundleException {
        if (bundle.getState() != Bundle.ACTIVE) {
            throw new BundleException("OSGi bundle " + bundle.getSymbolicName() + " not started.");
        }
        bundle.stop();
    }
}
