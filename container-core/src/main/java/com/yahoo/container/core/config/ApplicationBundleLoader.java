// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Manages the set of installed and active/inactive bundles.
 *
 * @author gjoranv
 * @author Tony Vaagenes
 */
public class ApplicationBundleLoader {

    private static final Logger log = Logger.getLogger(ApplicationBundleLoader.class.getName());

    // The active bundles for the current application generation.
    private final Map<FileReference, Bundle> reference2Bundle = new LinkedHashMap<>();

    // The bundles that are obsolete from the previous generation, but kept in case the generation is reverted.
    private Map<FileReference, Bundle> obsoleteBundles = Map.of();

    // The bundles that exclusively belong to the current application generation.
    private Map<FileReference, Bundle> bundlesFromNewGeneration = Map.of();

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

        obsoleteBundles = removeObsoleteBundles(newFileReferences);
        Set<Bundle> bundlesToUninstall = new LinkedHashSet<>(obsoleteBundles.values());
        log.info("Bundles to schedule for uninstall: " + bundlesToUninstall);

        osgi.allowDuplicateBundles(bundlesToUninstall);

        bundlesFromNewGeneration = installBundles(newFileReferences);
        BundleStarter.startBundles(reference2Bundle.values());
        log.info(installedBundlesMessage());

        return bundlesToUninstall;
    }

    /**
     * Restores state from the previous application generation and returns the set of bundles that
     * exclusively belongs to the latest (failed) application generation. Uninstalling must
     * be done by the Deconstructor as they may still be needed by components from the failed gen.
     */
    public synchronized Collection<Bundle> revertToPreviousGeneration() {
        reference2Bundle.putAll(obsoleteBundles);
        bundlesFromNewGeneration.forEach(reference2Bundle::remove);
        Collection<Bundle> ret = bundlesFromNewGeneration.values();

        // No duplicate bundles should be allowed until the next call to useBundles.
        osgi.allowDuplicateBundles(Set.of());

        // Clear restore info in case this method is called multiple times, for some reason.
        bundlesFromNewGeneration = Map.of();
        obsoleteBundles = Map.of();

        return ret;
    }

    /**
     * Calculates the set of bundles that are not needed by the new application generation and
     * removes them from the map of active bundles.
     *
     * Returns the map of bundles that are not needed by the new application generation.
     */
    private Map<FileReference, Bundle> removeObsoleteBundles(List<FileReference> newReferences) {
        Map<FileReference, Bundle> obsoleteReferences = new LinkedHashMap<>(reference2Bundle);
        newReferences.forEach(obsoleteReferences::remove);

        obsoleteReferences.forEach(reference2Bundle::remove);
        return obsoleteReferences;
    }

    /**
     * Returns the set of new bundles that were installed.
     */
    private Map<FileReference, Bundle> installBundles(List<FileReference> references) {
        Set<FileReference> bundlesToInstall = new HashSet<>(references);

        // This is just an optimization, as installing a bundle with the same location id returns the already installed bundle.
        bundlesToInstall.removeAll(reference2Bundle.keySet());

        if (bundlesToInstall.isEmpty()) return Map.of();

        if (bundleInstaller.hasFileDistribution()) {
            return installWithFileDistribution(bundlesToInstall, bundleInstaller);
        } else {
            log.warning("Can't retrieve bundles since file distribution is disabled.");
            return Map.of();
        }
    }

    private Map<FileReference, Bundle> installWithFileDistribution(Set<FileReference> bundlesToInstall,
                                                    FileAcquirerBundleInstaller bundleInstaller) {
        var newBundles = new LinkedHashMap<FileReference, Bundle>();

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
                newBundles.put(reference, bundles.get(0));
            }
            catch(Exception e) {
                throw new RuntimeException("Could not install bundle with reference '" + reference + "'", e);
            }
        }
        return newBundles;
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
