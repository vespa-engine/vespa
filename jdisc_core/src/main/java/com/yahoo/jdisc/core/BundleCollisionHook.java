package com.yahoo.jdisc.core;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.hooks.bundle.EventHook;
import org.osgi.framework.hooks.bundle.FindHook;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;

/**
 * A bundle {@link CollisionHook} that contains a set of bundles that are allowed to collide with
 * bundles that are about to be installed. In order to clean up when bundles are uninstalled, this
 * is also a bundle {@link EventHook}.
 *
 * Thread safe
 *
 * @author gjoranv
 */
public class BundleCollisionHook implements CollisionHook, EventHook, FindHook {
    private static Logger log = Logger.getLogger(BundleCollisionHook.class.getName());

    private ServiceRegistration<?> registration;
    private Map<Bundle, BsnVersion> allowedDuplicates = new HashMap<>(5);

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
     * Adds a collection of bundles to the allowed duplicates.
     */
    synchronized void allowDuplicateBundles(Collection<Bundle> bundles) {
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
     * that are duplicates of the allowed duplicates. Otherwise this method filters out the allowed duplicates,
     * so they are not visible to other bundles.
     *
     * NOTE:  This hook method is added for a consistent view of the installed bundles, but is not actively
     *        used by jdisc. The OSGi framework does not use FindHooks when calculating bundle wiring.
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
        log.info("Hiding bundles from bundle '" + context.getBundle() + "': " + bundlesToHide);
        bundles.removeAll(bundlesToHide);
    }

    private boolean isDuplicateOfAllowedDuplicates(Bundle bundle) {
        return ! allowedDuplicates.containsKey(bundle) && allowedDuplicates.containsValue(new BsnVersion(bundle));
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
