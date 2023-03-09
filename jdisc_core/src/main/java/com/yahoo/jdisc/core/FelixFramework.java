// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import com.google.inject.Inject;
import com.yahoo.jdisc.application.BundleInstallationException;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.application.OsgiHeader;
import org.apache.felix.framework.Felix;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.wiring.FrameworkWiring;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Simon Thoresen Hult
 * @author gjoranv
 */
public class FelixFramework implements OsgiFramework {

    private static final Logger log = Logger.getLogger(FelixFramework.class.getName());
    private final OsgiLogService logService = new OsgiLogService();
    private final ConsoleLogManager logListener;
    private final Felix felix;

    private final BundleCollisionHook collisionHook;

    @Inject
    public FelixFramework(FelixParams params) {
        deleteDirContents(new File(params.getCachePath()));
        felix = new Felix(params.toConfig());
        logListener = params.isLoggerEnabled() ? new ConsoleLogManager() : null;
        collisionHook = new BundleCollisionHook();
    }

    @Override
    public void start() throws BundleException {
        log.finer("Starting Felix.");
        felix.start();

        BundleContext ctx = felix.getBundleContext();
        collisionHook.start(ctx);
        logService.start(ctx);
        if (logListener != null) {
            logListener.install(ctx);
        }
    }

    @Override
    public void stop() throws BundleException {
        log.fine("Stopping felix.");
        BundleContext ctx = felix.getBundleContext();
        if (ctx != null) {
            if (logListener != null) {
                logListener.uninstall();
            }
            logService.stop();
            collisionHook.stop();
        }
        felix.stop();
        try {
            felix.waitForStop(0);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<Bundle> installBundle(String bundleLocation) throws BundleException {
        List<Bundle> bundles = new LinkedList<>();
        try {
            installBundle(bundleLocation, new HashSet<>(), bundles);
        } catch (Exception e) {
            throw new BundleInstallationException(bundles, e);
        }
        return bundles;
    }

    @Override
    public void startBundles(List<Bundle> bundles, boolean privileged) throws BundleException {
        for (Bundle bundle : bundles) {
            if (!privileged && OsgiHeader.isSet(bundle, OsgiHeader.PRIVILEGED_ACTIVATOR)) {
                log.log(Level.INFO, "OSGi bundle '" + bundle.getSymbolicName() + "' " +
                        "states that it requires privileged " +
                        "initialization, but privileges are not available. YMMV.");
            }
            if (bundle.getHeaders().get(Constants.FRAGMENT_HOST) != null) {
                continue; // fragments can not be started
            }
            bundle.start();
        }
        log.fine(startedBundlesMessage(bundles));
    }

    private String startedBundlesMessage(List<Bundle> bundles) {
        StringBuilder sb = new StringBuilder("Started bundles: {");
        for (Bundle b : bundles)
            sb.append("[" + b.getBundleId() + "]" + b.getSymbolicName() + ":" + b.getVersion() + ", ");
        sb.setLength(sb.length() - 2);
        sb.append("}");
        return sb.toString();
    }

    /**
     * NOTE: This method is no longer used by the Jdisc container framework, but kept for completeness.
     */
    @Override
    public void refreshPackages() {
        FrameworkWiring wiring = felix.adapt(FrameworkWiring.class);
        final CountDownLatch latch = new CountDownLatch(1);
        wiring.refreshBundles(null,
                              event -> {
                                  switch (event.getType()) {
                                      case FrameworkEvent.PACKAGES_REFRESHED:
                                          latch.countDown();
                                          break;
                                      case FrameworkEvent.ERROR:
                                          log.log(Level.SEVERE, "ERROR FrameworkEvent received.", event.getThrowable());
                                          break;
                                  }
                              });
        try {
            long TIMEOUT_SECONDS = 60L;
            if (!latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warning("No PACKAGES_REFRESHED FrameworkEvent received within " + TIMEOUT_SECONDS +
                                    " seconds of calling FrameworkWiring.refreshBundles()");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public BundleContext bundleContext() {
        return felix.getBundleContext();
    }

    @Override
    public List<Bundle> bundles() {
        return Arrays.asList(felix.getBundleContext().getBundles());
    }

    @Override
    public List<Bundle> getBundles(Bundle requestingBundle) {
        log.fine(() -> "All bundles: " + bundles());
        log.fine(() -> "Getting visible bundles for bundle " + requestingBundle);
        List<Bundle> visibleBundles = Arrays.asList(requestingBundle.getBundleContext().getBundles());
        log.fine(() -> "Visible bundles: " + visibleBundles);
        return visibleBundles;
    }

    public void allowDuplicateBundles(Collection<Bundle> bundles) {
        collisionHook.allowDuplicateBundles(bundles);
    }

    @Override
    public boolean isFelixFramework() {
        return true;
    }

    private void installBundle(String bundleLocation, Set<String> mask, List<Bundle> out) throws BundleException {
        bundleLocation = BundleLocationResolver.resolve(bundleLocation);
        if (mask.contains(bundleLocation)) {
            log.finer("OSGi bundle from '" + bundleLocation + "' already installed.");
            return;
        }
        log.finer("Installing OSGi bundle from '" + bundleLocation + "'.");
        mask.add(bundleLocation);

        Bundle bundle = felix.getBundleContext().installBundle(bundleLocation);
        String symbol = bundle.getSymbolicName();
        if (symbol == null) {
            bundle.uninstall();
            throw new BundleException("Missing Bundle-SymbolicName in manifest from '" + bundleLocation + " " +
                                      "(it might not be an OSGi bundle).");
        }
        out.add(bundle);
        for (String preInstall : OsgiHeader.asList(bundle, OsgiHeader.PREINSTALL_BUNDLE)) {
            log.finer("OSGi bundle '" + symbol + "' requires install from '" + preInstall + "'.");
            installBundle(preInstall, mask, out);
        }
    }

    private static void deleteDirContents(File parent) {
        File[] children = parent.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteDirContents(child);
                boolean deleted = child.delete();
                if (! deleted)
                    throw new RuntimeException("Could not delete file '" + child.getAbsolutePath() +
                                               "'. Please check file permissions!");
            }
        }
    }

}
