// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.container.Container;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleRevision;

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

    /* Map of file refs of active bundles (not scheduled for uninstall) to the installed bundle.
     *
     * Used to:
     * 1. Avoid installing already installed bundles. Just an optimization, installing the same bundle location is a NOP
     * 2. Start bundles (all are started every time)
     * 3. Calculate the set of bundles to uninstall
     */
    private final Map<FileReference, Bundle> reference2Bundle = new LinkedHashMap<>();

    private final Logger log = Logger.getLogger(ApplicationBundleLoader.class.getName());
    private final Osgi osgi;

    // A custom bundle installer for non-disk bundles, to be used for testing
    private BundleInstaller customBundleInstaller = null;

    public ApplicationBundleLoader(Osgi osgi) {
        this.osgi = osgi;
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
        startBundles();
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
            FileAcquirer fileAcquirer = Container.get().getFileAcquirer();
            boolean hasFileDistribution = (fileAcquirer != null);
            if (hasFileDistribution) {
                installWithFileDistribution(bundlesToInstall, new FileAcquirerBundleInstaller(fileAcquirer));
            } else if (customBundleInstaller != null) {
                installWithFileDistribution(bundlesToInstall, customBundleInstaller);
            } else {
                log.warning("Can't retrieve bundles since file distribution is disabled.");
            }
        }
    }

    private void installWithFileDistribution(Set<FileReference> bundlesToInstall, BundleInstaller bundleInstaller) {
        for (FileReference reference : bundlesToInstall) {
            try {
                log.info("Installing bundle with reference '" + reference.value() + "'");
                List<Bundle> bundles = bundleInstaller.installBundles(reference, osgi);

                // Throw if more than one bundle was installed, which means that the X-JDisc-Preinstall-Bundle header was used.
                // However, if the OSGi framework is only a test framework, this rule does not apply.
                if (bundles.size() > 1  && osgi.hasFelixFramework()) {
                    throw new RuntimeException("Bundle '" + bundles.get(0).getSymbolicName() + "' tried to pre-install other bundles.");
                }
                reference2Bundle.put(reference, bundles.get(0));
            }
            catch(Exception e) {
                throw new RuntimeException("Could not install bundle with reference '" + reference + "'", e);
            }
        }
    }

    /**
     * Resolves and starts (calls the Bundles BundleActivator) all bundles. Bundle resolution must take place
     * after all bundles are installed to ensure that the framework can resolve dependencies between bundles.
     */
    private void startBundles() {
        for (var bundle : reference2Bundle.values()) {
            try {
                if ( ! isFragment(bundle))
                    bundle.start();  // NOP for already ACTIVE bundles
            } catch(Exception e) {
                throw new RuntimeException("Could not start bundle '" + bundle.getSymbolicName() + "'", e);
            }
        }
    }

    private boolean isFragment(Bundle bundle) {
        BundleRevision bundleRevision = bundle.adapt(BundleRevision.class);
        if (bundleRevision == null)
            throw new NullPointerException("Null bundle revision means that bundle has probably been uninstalled: " +
                                           bundle.getSymbolicName() + ":" + bundle.getVersion());
        return (bundleRevision.getTypes() & BundleRevision.TYPE_FRAGMENT) != 0;
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
    void useCustomBundleInstaller(BundleInstaller bundleInstaller) {
        customBundleInstaller = bundleInstaller;
    }

    // Only for testing
    List<FileReference> getActiveFileReferences() {
        return new ArrayList<>(reference2Bundle.keySet());
    }

}
