// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.osgi;

import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.Version;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.jdisc.application.OsgiFramework;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import java.util.List;

/**
 * @author Tony Vaagenes
 * @author bratseth
 */
public class OsgiImpl implements Osgi {

    private final OsgiFramework jdiscOsgi;

    public OsgiImpl(OsgiFramework jdiscOsgi) {
        this.jdiscOsgi = jdiscOsgi;
    }

    @Override
    public Bundle[] getBundles() {
        List<Bundle> bundles = jdiscOsgi.bundles();
        return bundles.toArray(new Bundle[bundles.size()]);
    }


    public Class<Object> resolveClass(BundleInstantiationSpecification spec) {
        Bundle bundle = getBundle(spec.bundle);
        if (bundle != null) {
            return resolveFromBundle(spec, bundle);
        } else {
            return resolveFromClassPath(spec);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<Object> resolveFromClassPath(BundleInstantiationSpecification spec) {
        try {
            return (Class<Object>) Class.forName(spec.classId.getName());
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Could not create a component with id '" + spec.classId.getName() +
                    "'. Tried to load class directly, since no bundle was found for spec: " + spec.bundle +
                    ". If a bundle with the same name is installed, there is a either a version mismatch" +
                    " or the installed bundle's version contains a qualifier string.");
        }
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
        Bundle highestMatch = null;
        for (Bundle bundle : getBundles()) {
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
    public void uninstall(Bundle bundle) {
        try {
            bundle.uninstall();
        } catch (BundleException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void refreshPackages() {
        jdiscOsgi.refreshPackages();
    }

}
