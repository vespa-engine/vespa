// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Manages the set of installed and active/inactive bundles.
 *
 * @author gjoranv
 * @author Tony Vaagenes
 */
public class ApplicationBundleLoader {

    private static final Logger log = Logger.getLogger(ApplicationBundleLoader.class.getName());

    /**
     * Map of file refs of active bundles (not scheduled for uninstall) to the installed bundle.
     *
     * Used to:
     * 1. Avoid installing already installed bundles. Just an optimization, installing the same bundle location is a NOP
     * 2. Start bundles (all are started every time)
     * 3. Calculate the set of bundles to uninstall
     */
    private final Map<FileReference, Bundle> reference2Bundle = new LinkedHashMap<>();

    private final Osgi osgi;
    private final FileAcquirerBundleInstaller bundleInstaller;

    public ApplicationBundleLoader(Osgi osgi, FileAcquirerBundleInstaller bundleInstaller) {
        this.osgi = osgi;
        this.bundleInstaller = bundleInstaller;
    }

    /**
     * Installs the given set of bundles and returns the set of bundles that is no longer used
     * by the application, and should therefore be scheduled for uninstall.
     */
    public synchronized Set<Bundle> useBundles(List<FileReference> newFileReferences) {

        Set<FileReference> obsoleteReferences = getObsoleteFileReferences(newFileReferences);
        Set<Bundle> bundlesToUninstall = getObsoleteBundles(obsoleteReferences);
        log.info("Bundles to schedule for uninstall: " + bundlesToUninstall);

        osgi.allowDuplicateBundles(bundlesToUninstall);
        removeInactiveFileReferences(obsoleteReferences);

        installBundles(newFileReferences);
        BundleStarter.startBundles(reference2Bundle.values());
        log.info(installedBundlesMessage());

        return bundlesToUninstall;
    }

    private Set<FileReference> getObsoleteFileReferences(List<FileReference> newReferences) {
        Set<FileReference> obsoleteReferences = new HashSet<>(reference2Bundle.keySet());
        obsoleteReferences.removeAll(newReferences);
        return obsoleteReferences;
    }

    /**
     * Returns the bundles that will not be retained by the new application generation.
     */
    private Set<Bundle> getObsoleteBundles(Set<FileReference> obsoleteReferences) {
        return obsoleteReferences.stream().map(reference2Bundle::get).collect(Collectors.toSet());
    }

    private void removeInactiveFileReferences(Set<FileReference> fileReferencesToRemove) {
        fileReferencesToRemove.forEach(reference2Bundle::remove);
    }

    private void installBundles(List<FileReference> references) {
        Set<FileReference> bundlesToInstall = new HashSet<>(references);

        // This is just an optimization, as installing a bundle with the same location id returns the already installed bundle.
        bundlesToInstall.removeAll(reference2Bundle.keySet());

        if (!bundlesToInstall.isEmpty()) {
            if (bundleInstaller.hasFileDistribution()) {
                installWithFileDistribution(bundlesToInstall, bundleInstaller);
            } else {
                log.warning("Can't retrieve bundles since file distribution is disabled.");
            }
        }
    }

    private void installWithFileDistribution(Set<FileReference> bundlesToInstall,
                                             FileAcquirerBundleInstaller bundleInstaller) {
        for (FileReference reference : bundlesToInstall) {
            try {
                log.info("Installing bundle with reference '" + reference.value() + "'");
                List<Bundle> bundles = bundleInstaller.installBundles(reference, osgi);

                // If more than one bundle was installed, and the OSGi framework is the real Felix one,
                // it means that the X-JDisc-Preinstall-Bundle header was used.
                // However, test osgi frameworks may return multiple bundles when installing a single bundle.
                if (bundles.size() > 1  && osgi.hasFelixFramework()) {
                    throw new RuntimeException("Bundle '" + bundles.get(0).getSymbolicName() + "' tried to pre-install bundles from disk.");
                }
                reference2Bundle.put(reference, bundles.get(0));
            }
            catch(Exception e) {
                throw new RuntimeException("Could not install bundle with reference '" + reference + "'", e);
            }
        }
    }

    private String installedBundlesMessage() {
        StringBuilder sb = new StringBuilder("Installed bundles: {" );
        for (Bundle b : osgi.getBundles())
            sb.append("[" + b.getBundleId() + "]" + b.getSymbolicName() + ":" + b.getVersion() + ", ");
        sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }

    // Only for testing
    List<FileReference> getActiveFileReferences() {
        return new ArrayList<>(reference2Bundle.keySet());
    }

}
