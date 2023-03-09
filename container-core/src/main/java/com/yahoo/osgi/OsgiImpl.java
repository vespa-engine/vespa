// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.Version;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.application.OsgiFramework;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;

import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

/**
 * @author Tony Vaagenes
 * @author bratseth
 * @author gjoranv
 */
public class OsgiImpl implements Osgi {

    private static final Logger log = Logger.getLogger(OsgiImpl.class.getName());

    private final OsgiFramework jdiscOsgi;

    // The initial bundles are never scheduled for uninstall
    private final List<Bundle> initialBundles;

    private final Bundle systemBundle;

    // An initial bundle that is not the framework, and can hence be used to look up current bundles
    private final Bundle alwaysCurrentBundle;

    public OsgiImpl(OsgiFramework jdiscOsgi) {
        this.jdiscOsgi = jdiscOsgi;

        this.initialBundles = jdiscOsgi.bundles();
        if (initialBundles.isEmpty())
            throw new IllegalStateException("No initial bundles!");

        systemBundle = getSystemBundle(initialBundles, jdiscOsgi);
        alwaysCurrentBundle = firstNonFrameworkBundle(initialBundles);
        if (alwaysCurrentBundle == null)
            throw new IllegalStateException("The initial bundles only contained the framework bundle!");
        log.fine("Using " + alwaysCurrentBundle + " to lookup current bundles.");
    }

    @Override
    public Bundle[] getBundles() {
        List<Bundle> bundles = jdiscOsgi.bundles();
        return bundles.toArray(new Bundle[bundles.size()]);
    }

    @Override
    public List<Bundle> getCurrentBundles() {
        return jdiscOsgi.getBundles(alwaysCurrentBundle);
    }

    public Class<?> resolveClass(BundleInstantiationSpecification spec) {
        Bundle bundle = getBundle(spec.bundle);
        if (bundle != null) {
            return resolveFromBundle(spec, bundle);
        } else {
            if (jdiscOsgi.isFelixFramework() && ! spec.bundle.equals(spec.classId)) {
                // Bundle was explicitly specified, but not found.
                throw new IllegalArgumentException("Could not find bundle " + spec.bundle + " to create a component with class '"
                                                     + spec.classId.getName() + ". " + bundleResolutionErrorMessage(spec.bundle));
            }
            return resolveFromThisBundleOrSystemBundle(spec);
        }
    }

    /**
     * Tries to resolve the given class from this class' bundle.
     * If unsuccessful, resolves the class from the system bundle (jdisc_core).
     */
    @SuppressWarnings("unchecked")
    private Class<Object> resolveFromThisBundleOrSystemBundle(BundleInstantiationSpecification spec) {
        log.fine(() -> "Resolving class from container-disc: " + spec.classId.getName());
        try {
            return (Class<Object>) Class.forName(spec.classId.getName());
        } catch (ClassNotFoundException e) {
            if (hasFelixFramework()) {
                log.fine(() -> "Resolving class from the system bundle (jdisc core): " + spec.classId.getName());
                try {
                    return (Class<Object>) systemBundle.loadClass(spec.classId.getName());
                } catch (ClassNotFoundException e2) {
                    // empty
                }
            }
        }
        throw new IllegalArgumentException(
                "Could not create a component with class '" + spec.classId.getName() +
                        "'. Tried to load class directly, since no bundle was found for spec: " + spec.bundle +
                        ". " + bundleResolutionErrorMessage(spec.bundle));
    }

    protected String bundleResolutionErrorMessage(ComponentSpecification bundleSpec) {
        return "If a bundle with the same name is installed, there is a either a version mismatch " +
                "or the installed bundle's version contains a qualifier string.";
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> resolveFromBundle(BundleInstantiationSpecification spec, Bundle bundle) {
        try {
            ensureBundleActive(bundle);
            return (Class<Object>) bundle.loadClass(spec.classId.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Could not load class '" + spec.classId.getName() +
                                               "' from bundle " + bundle, e);
        }
    }

    private static void ensureBundleActive(Bundle bundle) throws IllegalStateException {
        int state = bundle.getState();
        Throwable cause = null;
        if (state != Bundle.ACTIVE) {
            try {
                // Get the reason why the bundle isn't active.
                // Do not change this method to not fail if start is successful without carefully analyzing
                // why there are non-active bundles.
                bundle.start();
            } catch (BundleException e) {
                cause = e;
            }
            throw new IllegalStateException("Bundle " + bundle + " is not active. State=" + state + ".", cause);
        }
    }

    /**
     * Returns the bundle of a given name having the highest matching version
     *
     * @param id the id of the component to return. May not include a version, or include
     *        an underspecified version, in which case the highest (matching) version which
     *        does not contain a qualifier is returned
     * @return the bundle match having the highest version, or null if there was no matches
     */
    public Bundle getBundle(ComponentSpecification id) {
        log.fine(() -> "Getting bundle for component " + id + ". Set of current bundles: " + getCurrentBundles());
        Bundle highestMatch = null;
        for (Bundle bundle : getCurrentBundles()) {
            assert bundle.getSymbolicName() != null : "ensureHasBundleSymbolicName not called during installation";

            if ( ! bundle.getSymbolicName().equals(id.getName())) continue;
            if ( ! id.getVersionSpecification().matches(versionOf(bundle))) continue;

            if (highestMatch == null || versionOf(highestMatch).compareTo(versionOf(bundle)) < 0)
                highestMatch = bundle;
        }
        return highestMatch;
    }

    /** Returns the version of a bundle, as specified by Bundle-Version in the manifest */
    private static Version versionOf(Bundle bundle) {
        Object bundleVersion = bundle.getHeaders().get("Bundle-Version");
        if (bundleVersion == null) return Version.emptyVersion;
        return new Version(bundleVersion.toString());
    }

    @Override
    public List<Bundle> install(String absolutePath) {
        try {
            return jdiscOsgi.installBundle(normalizeLocation(absolutePath));
        } catch (BundleException e) {
            throw new RuntimeException(e);
        }
    }

    private static String normalizeLocation(String location) {
        if (location.indexOf(':') < 0)
            location = "file:" + location;
        return location;
    }

    @Override
    public void allowDuplicateBundles(Collection<Bundle> bundles) {
        jdiscOsgi.allowDuplicateBundles(bundles);
    }

    @Override
    public boolean hasFelixFramework() {
        return jdiscOsgi.isFelixFramework();
    }

    private static Bundle getSystemBundle(List<Bundle> bundles, OsgiFramework jdiscOsgi) {
        for (Bundle b : bundles) {
            if (b instanceof Framework)
                return b;
        }
        if (jdiscOsgi.isFelixFramework()) throw new IllegalStateException("No system bundle in " + bundles);
        return null;
    }

    private static Bundle firstNonFrameworkBundle(List<Bundle> bundles) {
        for (Bundle b : bundles) {
            if (! (b instanceof Framework))
                return b;
        }
        return null;
    }

}
