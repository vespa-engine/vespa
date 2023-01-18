// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.container.di.Osgi.GenerationStatus;
import com.yahoo.jdisc.application.BsnVersion;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.util.ArrayList;
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
 * TODO: This class and the CollisionHook currently only handles a "current" and "previous" generation.
 *       In order to correctly handle rapid reconfiguration and hence multiple generations, we need to
 *       consider the graph generation number for each bundle.
 *
 * @author gjoranv
 */
public class ApplicationBundleLoader {

    private static final Logger log = Logger.getLogger(ApplicationBundleLoader.class.getName());

    // The active bundles for the current application generation.
    private final Map<FileReference, Bundle> activeBundles = new LinkedHashMap<>();

    // The bundles that are obsolete from the previous generation, but kept in case the generation is reverted.
    private Map<FileReference, Bundle> obsoleteBundles = Map.of();

    // The bundles that exclusively belong to the current application generation.
    private Map<FileReference, Bundle> bundlesFromNewGeneration = Map.of();

    private boolean readyForNewBundles = true;

    private final Osgi osgi;
    private final FileAcquirerBundleInstaller bundleInstaller;


    public ApplicationBundleLoader(Osgi osgi, FileAcquirerBundleInstaller bundleInstaller) {
        this.osgi = osgi;
        this.bundleInstaller = bundleInstaller;
    }

    /**
     * Returns bsn:version for all active bundles.
     */
    public synchronized List<BsnVersion> activeBundlesBsnVersion() {
        return activeBundles.values().stream().map(BsnVersion::new)
                .toList();
    }

    /**
     * Installs the given set of bundles and updates state for which bundles and file references
     * that are active or should be uninstalled in case of success or failure.
     */
    public synchronized void useBundles(List<FileReference> newFileReferences) {
        if (! readyForNewBundles)
            throw new IllegalStateException("Bundles must be committed or reverted before using new bundles.");

        obsoleteBundles = removeObsoleteReferences(newFileReferences);
        osgi.allowDuplicateBundles(obsoleteBundles.values());

        bundlesFromNewGeneration = installBundles(newFileReferences);
        BundleStarter.startBundles(activeBundles.values());
        log.info(installedBundlesMessage());

        readyForNewBundles = false;
    }

    /**
     * Must be called after useBundles() to report success or failure for the latest bundle generation.
     */
    public synchronized Set<Bundle> completeGeneration(GenerationStatus status) {
        Set<Bundle> ret = Set.of();
        if (readyForNewBundles) return ret;

        readyForNewBundles = true;
        if (status == GenerationStatus.SUCCESS) {
            return commitBundles();
        } else {
            return revertToPreviousGeneration();
        }
    }

    /**
     * Commit to the latest set of bundles given to useBundles(). Returns the set of bundles that is no longer
     * used by the application, and should therefore be scheduled for uninstall.
     */
    private Set<Bundle> commitBundles() {
        var bundlesToUninstall = new LinkedHashSet<>(obsoleteBundles.values());
        log.info("Bundles to be uninstalled from previous generation: " + bundlesToUninstall);

        bundlesFromNewGeneration = Map.of();
        obsoleteBundles = Map.of();
        readyForNewBundles = true;
        return bundlesToUninstall;
    }

    /**
     * Restores state from the previous application generation and returns the set of bundles that
     * exclusively belongs to the latest (failed) application generation. Uninstalling must
     * be done by the Deconstructor as they may still be needed by components from the failed gen.
     */
    private Set<Bundle> revertToPreviousGeneration() {
        log.info("Reverting to previous generation with bundles: " + obsoleteBundles);
        log.info("Bundles from latest generation will be removed: " + bundlesFromNewGeneration);
        activeBundles.putAll(obsoleteBundles);
        bundlesFromNewGeneration.forEach(activeBundles::remove);
        var ret = new LinkedHashSet<>(bundlesFromNewGeneration.values());

        // For correct operation of the CollisionHook (more specifically its FindHook implementation), the set of
        // allowed duplicates must reflect the next set of bundles to uninstall, which is now the bundles from the
        // failed generation.
        osgi.allowDuplicateBundles(ret);

        // Clear restore info in case this method is called multiple times, for some reason.
        bundlesFromNewGeneration = Map.of();
        obsoleteBundles = Map.of();

        readyForNewBundles = true;
        return ret;
    }

    /**
     * Calculates the set of bundles that are not needed by the new application generation and
     * removes them from the map of active bundles.
     *
     * Returns the map of bundles that are not needed by the new application generation.
     */
    private Map<FileReference, Bundle> removeObsoleteReferences(List<FileReference> newReferences) {
        Map<FileReference, Bundle> obsoleteReferences = new LinkedHashMap<>(activeBundles);
        newReferences.forEach(obsoleteReferences::remove);

        obsoleteReferences.forEach(activeBundles::remove);
        return obsoleteReferences;
    }

    /**
     * Returns the set of new bundles that were installed.
     */
    private Map<FileReference, Bundle> installBundles(List<FileReference> references) {
        Set<FileReference> bundlesToInstall = new HashSet<>(references);

        // This is just an optimization, as installing a bundle with the same location id returns the already installed bundle.
        bundlesToInstall.removeAll(activeBundles.keySet());

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
                activeBundles.put(reference, bundles.get(0));
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
        return new ArrayList<>(activeBundles.keySet());
    }

}
