// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.bundle;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.Version;
import org.osgi.framework.wiring.BundleCapability;
import org.osgi.framework.wiring.BundleRequirement;
import org.osgi.framework.wiring.BundleRevision;
import org.osgi.framework.wiring.BundleWire;
import org.osgi.framework.wiring.BundleWiring;
import org.osgi.resource.Capability;
import org.osgi.resource.Requirement;
import org.osgi.resource.Wire;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

/**
 * @author gjoranv
 * @author ollivir
 */
public class MockBundle implements Bundle, BundleWiring {
    public static final String SymbolicName = "mock-bundle";
    public static final Version BundleVersion = new Version(1, 0, 0);

    private static final Class<BundleWiring> bundleWiringClass = BundleWiring.class;

    @Override
    public int getState() {
        return Bundle.ACTIVE;
    }

    @Override
    public void start(int options) {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop(int options) {
    }

    @Override
    public void stop() {
    }

    @Override
    public void update(InputStream input) {
    }

    @Override
    public void update() {
    }

    @Override
    public void uninstall() {
    }

    @Override
    public Dictionary<String, String> getHeaders(String locale) {
        return getHeaders();
    }

    @Override
    public String getSymbolicName() {
        return SymbolicName;
    }

    @Override
    public Version getVersion() {
        return BundleVersion;
    }

    @Override
    public String getLocation() {
        return getSymbolicName();
    }

    @Override
    public long getBundleId() {
        return 0L;
    }

    @Override
    public Dictionary<String, String> getHeaders() {
        return new Hashtable<>();
    }

    @Override
    public ServiceReference<?>[] getRegisteredServices() {
        return new ServiceReference<?>[0];
    }

    @Override
    public ServiceReference<?>[] getServicesInUse() {
        return getRegisteredServices();
    }

    @Override
    public boolean hasPermission(Object permission) {
        return true;
    }

    @Override
    public URL getResource(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Class<?> loadClass(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<URL> getResources(String name) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<String> getEntryPaths(String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public URL getEntry(String path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Enumeration<URL> findEntries(String path, String filePattern, boolean recurse) {
        return Collections.emptyEnumeration();
    }


    @Override
    public long getLastModified() {
        return 1L;
    }

    @Override
    public BundleContext getBundleContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Map<X509Certificate, List<X509Certificate>> getSignerCertificates(int signersType) {
        return Collections.emptyMap();
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T adapt(Class<T> type) {
        if (type.equals(bundleWiringClass)) {
            return (T) this;
        } else {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public File getDataFile(String filename) {
        return null;
    }

    @Override
    public int compareTo(Bundle o) {
        return Long.compare(getBundleId(), o.getBundleId());
    }


    //TODO: replace with mockito
    @Override
    public List<URL> findEntries(String p1, String p2, int p3) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Wire> getRequiredResourceWires(String p1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Capability> getResourceCapabilities(String p1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isCurrent() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BundleWire> getRequiredWires(String p1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BundleCapability> getCapabilities(String p1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Wire> getProvidedResourceWires(String p1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<BundleWire> getProvidedWires(String p1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BundleRevision getRevision() {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Requirement> getResourceRequirements(String p1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isInUse() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<String> listResources(String p1, String p2, int p3) {
        return Collections.emptyList();
    }

    @Override
    public ClassLoader getClassLoader() {
        return MockBundle.class.getClassLoader();
    }

    @Override
    public List<BundleRequirement> getRequirements(String p1) {
        throw new UnsupportedOperationException();
    }

    @Override
    public BundleRevision getResource() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Bundle getBundle() {
        throw new UnsupportedOperationException();
    }
}
