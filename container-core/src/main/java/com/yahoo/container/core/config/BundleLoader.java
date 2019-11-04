// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.yahoo.collections.PredicateSplit;
import com.yahoo.config.FileReference;
import com.yahoo.container.Container;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleRevision;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static com.yahoo.collections.PredicateSplit.partition;
import static com.yahoo.container.core.BundleLoaderProperties.DISK_BUNDLE_PREFIX;

/**
 * Manages the set of installed 3rd-party component bundles.
 *
 * @author Tony Vaagenes
 */
public class BundleLoader {

    private final List<Bundle> initialBundles;

    private final Map<FileReference, List<Bundle>> reference2Bundles = new LinkedHashMap<>();

    private final Logger log = Logger.getLogger(BundleLoader.class.getName());
    private final Osgi osgi;

    public BundleLoader(Osgi osgi) {
        this.osgi = osgi;
        initialBundles = Arrays.asList(osgi.getBundles());
    }

    private List<Bundle> obtainBundles(FileReference reference, FileAcquirer fileAcquirer) throws InterruptedException {
        File file = fileAcquirer.waitFor(reference, 7, TimeUnit.DAYS);
        return osgi.install(file.getAbsolutePath());
    }

    /** Returns the number of bundles installed by this call. */
    private int install(List<FileReference> references) {
        Set<FileReference> bundlesToInstall = new HashSet<>(references);
        bundlesToInstall.removeAll(reference2Bundles.keySet());

        PredicateSplit<FileReference> bundlesToInstall_isDisk = partition(bundlesToInstall, BundleLoader::isDiskBundle);
        installBundlesFromDisk(bundlesToInstall_isDisk.trueValues);
        installBundlesFromFileDistribution(bundlesToInstall_isDisk.falseValues);

        startBundles();
        return bundlesToInstall.size();
    }

    private static boolean isDiskBundle(FileReference fileReference) {
        return fileReference.value().startsWith(DISK_BUNDLE_PREFIX);
    }

    private void installBundlesFromDisk(List<FileReference> bundlesToInstall) {
        for (FileReference reference : bundlesToInstall) {
            try {
                installBundleFromDisk(reference);
            }
            catch(Exception e) {
                throw new RuntimeException("Could not install bundle '" + reference + "'", e);
            }
        }
    }

    private void installBundlesFromFileDistribution(List<FileReference> bundlesToInstall) {
        if (!bundlesToInstall.isEmpty()) {
            FileAcquirer fileAcquirer = Container.get().getFileAcquirer();
            boolean hasFileDistribution = (fileAcquirer != null);
            if (hasFileDistribution) {
                installWithFileDistribution(bundlesToInstall, fileAcquirer);
            } else {
                log.warning("Can't retrieve bundles since file distribution is disabled.");
            }
        }
    }

    private void installBundleFromDisk(FileReference reference) {
        assert(reference.value().startsWith(DISK_BUNDLE_PREFIX));
        String referenceFileName = reference.value().substring(DISK_BUNDLE_PREFIX.length());
        log.info("Installing bundle from disk with reference '" + reference.value() + "'");

        File file = new File(referenceFileName);
        if ( ! file.exists()) {
            throw new IllegalArgumentException("Reference '" + reference.value() + "' not found on disk.");
        }

        List<Bundle> bundles = osgi.install(file.getAbsolutePath());
        reference2Bundles.put(reference, bundles);
    }

    private void installWithFileDistribution(List<FileReference> bundlesToInstall, FileAcquirer fileAcquirer) {
        for (FileReference reference : bundlesToInstall) {
            try {
                log.info("Installing bundle with reference '" + reference.value() + "'");
                List<Bundle> bundles = obtainBundles(reference, fileAcquirer);
                reference2Bundles.put(reference, bundles);
            }
            catch(Exception e) {
                throw new RuntimeException("Could not install bundle '" + reference + "'", e);
            }
        }
    }

    // All bundles must have been started first to ensure correct package resolution.
    private void startBundles() {
        for (List<Bundle> bundles : reference2Bundles.values()) {
            for (Bundle bundle : bundles) {
                try {
                    if ( ! isFragment(bundle))
                        bundle.start();
                } catch(Exception e) {
                    throw new RuntimeException("Could not start bundle '" + bundle.getSymbolicName() + "'", e);
                }
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

    /** Returns the number of uninstalled bundles */
    private int retainOnly(List<FileReference> newReferences) {
        Set<Bundle> bundlesToRemove = new HashSet<>(Arrays.asList(osgi.getBundles()));

        for (FileReference fileReferenceToKeep: newReferences) {
            if (reference2Bundles.containsKey(fileReferenceToKeep))
                bundlesToRemove.removeAll(reference2Bundles.get(fileReferenceToKeep));
        }

        bundlesToRemove.removeAll(initialBundles);
        for (Bundle bundle : bundlesToRemove) {
            log.info("Removing bundle '" + bundle.toString() + "'");
            osgi.uninstall(bundle);
        }

        Set<FileReference> fileReferencesToRemove = new HashSet<>(reference2Bundles.keySet());
        fileReferencesToRemove.removeAll(newReferences);

        for (FileReference fileReferenceToRemove : fileReferencesToRemove) {
            reference2Bundles.remove(fileReferenceToRemove);
        }
        return bundlesToRemove.size();
    }

    public synchronized int use(List<FileReference> bundles) {
        int removedBundles = retainOnly(bundles);
        int installedBundles = install(bundles);
        startBundles();

        log.info(removedBundles + " bundles were removed, and " + installedBundles + " bundles were installed.");
        log.info(installedBundlesMessage());
        return removedBundles + installedBundles;
    }

    private String installedBundlesMessage() {
        StringBuilder sb = new StringBuilder("Installed bundles: {" );
        for (Bundle b : osgi.getBundles())
            sb.append("[" + b.getBundleId() + "]" + b.getSymbolicName() + ":" + b.getVersion() + ", ");
        sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }

}
