package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Installs all platform bundles, using the {@link DiskBundleInstaller}.
 * All platform bundles reside on disk, and they are never uninstalled.
 *
 * @author gjoranv
 */
public class PlatformBundleLoader {
    private static final Logger log = Logger.getLogger(PlatformBundleLoader.class.getName());

    private final Osgi osgi;
    private final DiskBundleInstaller installer;

    public PlatformBundleLoader(Osgi osgi) {
        this(osgi, new DiskBundleInstaller());
    }

    PlatformBundleLoader(Osgi osgi, DiskBundleInstaller installer) {
        this.osgi = osgi;
        this.installer = installer;
    }

    public void useBundles(List<FileReference> fileReferences) {
        Set<Bundle> installedBundles = install(fileReferences);
        BundleStarter.startBundles(installedBundles);
    }

    private Set<Bundle> install(List<FileReference> bundlesToInstall) {
        var installedBundles = new LinkedHashSet<Bundle>();
        for (FileReference reference : bundlesToInstall) {
            try {
                installedBundles.addAll(installBundleFromDisk(reference));
            }
            catch(Exception e) {
                throw new RuntimeException("Could not install bundle '" + reference + "'", e);
            }
        }
        return installedBundles;
    }

    private List<Bundle> installBundleFromDisk(FileReference reference) {
        log.info("Installing bundle from disk with reference '" + reference.value() + "'");
        List<Bundle> bundles = installer.installBundles(reference, osgi);
        log.fine("Installed " + bundles.size() + " bundles for file reference " + reference);
        return bundles;
    }

}
