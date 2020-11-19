package com.yahoo.container.core.config;

import com.yahoo.osgi.Osgi;
import org.osgi.framework.Bundle;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Used to install the bundles that are added as platform bundles by the config-model.
 *
 * All platform bundles reside on disk, and they are never uninstalled.
 * Platform bundles are allowed to pre-install other bundles on disk via the
 * X-JDisc-Preinstall-Bundle manifest header.
 *
 * Attempts to install additional bundles, after the first call, should be a NOP.
 *
 * @author gjoranv
 */
public class PlatformBundleLoader {

    private static final Logger log = Logger.getLogger(PlatformBundleLoader.class.getName());

    private final Osgi osgi;
    private final DiskBundleInstaller installer;

    private Set<Bundle> installedBundles;
    private boolean hasLoadedBundles = false;

    public PlatformBundleLoader(Osgi osgi) {
        this(osgi, new DiskBundleInstaller());
    }

    PlatformBundleLoader(Osgi osgi, DiskBundleInstaller installer) {
        this.osgi = osgi;
        this.installer = installer;
    }

    public void useBundles(List<String> bundlePaths) {
        if (hasLoadedBundles) {
            log.fine(() -> "Platform bundles have already been installed." +
                    "\nInstalled bundles: " + installedBundles +
                    "\nGiven files: " + bundlePaths);
            return;
        }
        installedBundles = install(bundlePaths);
        BundleStarter.startBundles(installedBundles);
        hasLoadedBundles = true;
    }

    private Set<Bundle> install(List<String> bundlesToInstall) {
        var allInstalled = new LinkedHashSet<Bundle>();
        for (String bundlePath : bundlesToInstall) {
            try {
                allInstalled.addAll(installBundleFromDisk(bundlePath));
            }
            catch(Exception e) {
                throw new RuntimeException("Could not install bundle '" + bundlePath + "'", e);
            }
        }
        return allInstalled;
    }

    private List<Bundle> installBundleFromDisk(String bundlePath) {
        log.info("Installing bundle from disk: " + bundlePath);
        List<Bundle> bundles = installer.installBundles(bundlePath, osgi);
        log.fine("Installed " + bundles.size() + " bundles for file " + bundlePath);
        return bundles;
    }

}
