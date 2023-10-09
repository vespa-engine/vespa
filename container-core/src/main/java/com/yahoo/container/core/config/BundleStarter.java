// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleRevision;

import java.util.Collection;

/**
 * Utility to start a collection of bundles.
 *
 * @author gjoranv
 */
public class BundleStarter {

    private BundleStarter() { }

    /**
     * Resolves and starts (calls the Bundles BundleActivator) all bundles. Bundle resolution must take place
     * after all bundles are installed to ensure that the framework can resolve dependencies between bundles.
     */
    static void startBundles(Collection<Bundle> bundles) {
        for (var bundle : bundles) {
            try {
                if ( ! isFragment(bundle))
                    bundle.start();  // NOP for already ACTIVE bundles
            } catch(Exception e) {
                throw new RuntimeException("Could not start bundle '" + bundle.getSymbolicName() + "'", e);
            }
        }
    }

    private static boolean isFragment(Bundle bundle) {
        BundleRevision bundleRevision = bundle.adapt(BundleRevision.class);
        if (bundleRevision == null)
            throw new NullPointerException("Null bundle revision means that bundle has probably been uninstalled: " +
                                                   bundle.getSymbolicName() + ":" + bundle.getVersion());
        return (bundleRevision.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0;
    }

}
