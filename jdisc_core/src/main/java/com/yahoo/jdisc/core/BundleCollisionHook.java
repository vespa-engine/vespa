// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.core;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.hooks.bundle.EventHook;
import org.osgi.framework.hooks.bundle.FindHook;
import org.osgi.framework.launch.Framework;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * A bundle {@link CollisionHook} that contains a set of bundles that are allowed to collide with bundles
 * that are about to be installed. This class also implements a {@link FindHook} to provide a consistent
 * view of bundles such that the two sets of duplicate bundles are invisible to each other.
 * In order to clean up when bundles are uninstalled, this is also a bundle {@link EventHook}.
 *
 * Thread safe
 *
 * @author gjoranv
 */
public class BundleCollisionHook implements CollisionHook, EventHook, FindHook {
    private static final Logger log = Logger.getLogger(BundleCollisionHook.class.getName());

    private ServiceRegistration<?> registration;
    private final Map<Bundle, BsnVersion> allowedDuplicates = new HashMap<>(5);

    public void start(BundleContext context) {
        if (registration != null) {
            throw new IllegalStateException();
        }
        String[] serviceClasses = {CollisionHook.class.getName(), EventHook.class.getName(), FindHook.class.getName()};
        registration = context.registerService(serviceClasses, this, null);
    }

    public void stop() {
        registration.unregister();
        registration = null;
    }

    /**
     * Sets a collection of bundles to allow duplicates for.
     */
    synchronized void allowDuplicateBundles(Collection<Bundle> bundles) {
        allowedDuplicates.clear();
        for (var bundle : bundles) {
            allowedDuplicates.put(bundle, new BsnVersion(bundle));
        }
    }

    /**
     * Cleans up the allowed duplicates when a bundle is uninstalled.
     */
    @Override
    public void event(BundleEvent event, Collection<BundleContext> contexts) {
        if (event.getType() != BundleEvent.UNINSTALLED) return;

        synchronized (this) {
            allowedDuplicates.remove(event.getBundle());
        }
    }

    /**
     * Removes duplicates of the allowed duplicate bundles from the given collision candidates.
     */
    @Override
    public synchronized void filterCollisions(int operationType, Bundle target, Collection<Bundle> collisionCandidates) {
        Set<Bundle> whitelistedCandidates = new HashSet<>();
        for (var bundle : collisionCandidates) {
            // This is O(n), but n should be small here, plus this is only called when bundles collide.
            if (allowedDuplicates.containsValue(new BsnVersion(bundle))) {
                whitelistedCandidates.add(bundle);
            }
        }
        collisionCandidates.removeAll(whitelistedCandidates);
    }

    /**
     * Filters out the set of bundles that should not be visible to the bundle associated with the given context.
     * If the given context represents one of the allowed duplicates, this method filters out all bundles
     * that are duplicates of the allowed duplicates. Otherwise, this method filters out the allowed duplicates,
     * so they are not visible to other bundles.
     */
    @Override
    public synchronized void find(BundleContext context, Collection<Bundle> bundles) {
        Set<Bundle> bundlesToHide = new HashSet<>();
        if (allowedDuplicates.containsKey(context.getBundle())) {
            for (var bundle : bundles) {
                // isDuplicate... is O(n), but n should be small here, plus this is only run for duplicate bundles.
                if (isDuplicateOfAllowedDuplicates(bundle)) {
                    bundlesToHide.add(bundle);
                }
            }
        } else {
            for (var bundle : bundles) {
                if (allowedDuplicates.containsKey(bundle)) {
                    bundlesToHide.add(bundle);
                }
            }
        }
        logHiddenBundles(context, bundlesToHide);
        bundles.removeAll(bundlesToHide);
    }

    private boolean isDuplicateOfAllowedDuplicates(Bundle bundle) {
        return ! allowedDuplicates.containsKey(bundle) && allowedDuplicates.containsValue(new BsnVersion(bundle));
    }

    private void logHiddenBundles(BundleContext requestingContext, Set<Bundle> hiddenBundles) {
        if (hiddenBundles.isEmpty()) {
            log.fine(() -> "No bundles to hide from bundle " + requestingContext.getBundle());
        } else {
            if (requestingContext.getBundle() instanceof Framework) {
                log.fine(() -> "Requesting bundle is the Framework, so hidden bundles will be visible: " + hiddenBundles);
            } else {
                log.fine(() -> "Hiding bundles from bundle '" + requestingContext.getBundle() + "': " + hiddenBundles);
            }
        }
    }


    static class BsnVersion {

        private final String symbolicName;
        private final Version version;

        BsnVersion(Bundle bundle) {
            this.symbolicName = bundle.getSymbolicName();
            this.version = bundle.getVersion();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            BsnVersion that = (BsnVersion) o;
            return Objects.equals(symbolicName, that.symbolicName) &&
                    version.equals(that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(symbolicName, version);
        }

    }

}
