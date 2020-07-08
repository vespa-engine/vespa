package com.yahoo.container.core.config;

import com.yahoo.config.FileReference;
import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * Installs all platform bundles, using the {@link DiskBundleInstaller}.
 * All platform bundles reside on disk, and they are never uninstalled.
 *
 * @author gjoranv
 */
// TODO: rename to ...Loader or ...Manager
public class PlatformBundleInstaller {
    private static final Logger log = Logger.getLogger(PlatformBundleInstaller.class.getName());

    private final Osgi osgi;
    private final DiskBundleInstaller installer;

    public PlatformBundleInstaller(Osgi osgi) {
        this.osgi = osgi;
        installer = new DiskBundleInstaller();
    }

    public void install(Collection<FileReference> bundlesToInstall) {
        for (FileReference reference : bundlesToInstall) {
            try {
                installBundleFromDisk(reference);
            }
            catch(Exception e) {
                throw new RuntimeException("Could not install bundle '" + reference + "'", e);
            }
        }
    }

    private void installBundleFromDisk(FileReference reference) {
        log.info("Installing bundle from disk with reference '" + reference.value() + "'");
        List<Bundle> bundles = installer.installBundles(reference, osgi);
        log.fine("Installed " + bundles.size() + " bundles for file reference " + reference);
    }

}
