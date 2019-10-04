package com.yahoo.jdisc.core;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleEvent;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.Version;
import org.osgi.framework.hooks.bundle.CollisionHook;
import org.osgi.framework.hooks.bundle.EventHook;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A bundle {@link CollisionHook} that contains a set of bundles that are allowed to collide with
 * bundles that are about to be installed. In order to clean up when bundles are uninstalled, this
 * is also a bundle {@link EventHook}.
 *
 * Thread safe
 *
 * @author gjoranv
 */
public class BundleCollisionHook implements CollisionHook, EventHook {

    private ServiceRegistration<?> registration;
    private Map<Bundle, BsnVersion> allowedDuplicates = new HashMap<>(5);

    public void start(BundleContext context) {
        if (registration != null) {
            throw new IllegalStateException();
        }
        registration = context.registerService(new String[]{CollisionHook.class.getName(), EventHook.class.getName()},
                                               this, null);
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
            allowedDuplicates.put(bundle, new BsnVersion(bundle.getSymbolicName(), bundle.getVersion()));
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

    @Override
    public synchronized void filterCollisions(int operationType, Bundle target, Collection<Bundle> collisionCandidates) {
        Set<Bundle> whitelistedCandidates = new HashSet<>();
        for (var bundle : collisionCandidates) {
            var bsnVersion = new BsnVersion(bundle.getSymbolicName(), bundle.getVersion());

            // This is O(n), but n should be small here, plus this is only called when bundles collide.
            if (allowedDuplicates.containsValue(bsnVersion)) {
                whitelistedCandidates.add(bundle);
            }
        }
        collisionCandidates.removeAll(whitelistedCandidates);
    }


    static class BsnVersion {

        private final String symbolicName;
        private final Version version;

        BsnVersion(String symbolicName, Version version) {
            this.symbolicName = symbolicName;
            this.version = version;
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
