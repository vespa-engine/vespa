// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.application.container.impl;

import com.google.common.collect.Lists;
import com.yahoo.container.standalone.StandaloneContainerApplication;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.application.OsgiHeader;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleListener;
import org.osgi.framework.Filter;
import org.osgi.framework.FrameworkListener;
import org.osgi.framework.ServiceFactory;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceObjects;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

/**
 * A (mock) OSGI implementation which loads classes from the system classpath
 *
 * @author Tony Vaagenes
 * @author ollivir
 */
public final class ClassLoaderOsgiFramework implements OsgiFramework {
    private BundleContextImpl bundleContextImpl = new BundleContextImpl();
    private SystemBundleImpl systemBundleImpl = new SystemBundleImpl();
    private BundleWiringImpl bundleWiringImpl = new BundleWiringImpl();

    private List<URL> bundleLocations = new ArrayList<>();
    private List<Bundle> bundleList = Lists.newArrayList(systemBundleImpl);
    private ClassLoader classLoader = null;

    private AtomicInteger nextBundleId = new AtomicInteger(1);

    @Override
    public List<Bundle> installBundle(String bundleLocation) {
        if (bundleLocation != null && bundleLocation.isEmpty() == false) {
            try {
                URL url = new URL(bundleLocation);
                bundleLocations.add(url);
                bundleList.add(new JarBundleImpl(url));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        return bundles();
    }

    private ClassLoader getClassLoader() {
        if (bundleLocations.isEmpty()) {
            return getClass().getClassLoader();
        } else {
            if (classLoader == null) {
                classLoader = new URLClassLoader(bundleLocations.toArray(new URL[0]), getClass().getClassLoader());
            }
            return classLoader;
        }
    }

    @Override
    public void startBundles(List<Bundle> bundles, boolean privileged) {
    }

    @Override
    public void refreshPackages() {
    }

    @Override
    public BundleContext bundleContext() {
        return bundleContextImpl;
    }

    @Override
    public List<Bundle> bundles() {
        return bundleList;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    private abstract class BundleImpl implements Bundle {
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
            return ClassLoaderOsgiFramework.this.getClass().getName();
        }

        @Override
        public String getLocation() {
            return getSymbolicName();
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
            return getClassLoader().getResource(name);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            return getClassLoader().loadClass(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            return getClassLoader().getResources(name);
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
            throw new UnsupportedOperationException();
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

        @Override
        @SuppressWarnings("unchecked")
        public <T> T adapt(Class<T> clazz) {
            if (clazz.equals(BundleRevision.class)) {
                return (T) new BundleRevisionImpl();
            } else if (clazz.equals(BundleWiring.class)) {
                return (T) new BundleWiringImpl();
            } else {
                return null;
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
    }

    private class BundleRevisionImpl implements BundleRevision {
        @Override
        public String getSymbolicName() {
            return this.getClass().getName();
        }

        @Override
        public List<BundleRequirement> getDeclaredRequirements(String p1) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Version getVersion() {
            return Version.emptyVersion;
        }

        @Override
        public BundleWiring getWiring() {
            return bundleWiringImpl;
        }

        @Override
        public List<BundleCapability> getDeclaredCapabilities(String p1) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getTypes() {
            return 0;
        }

        @Override
        public Bundle getBundle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Capability> getCapabilities(String p1) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Requirement> getRequirements(String p1) {
            throw new UnsupportedOperationException();
        }
    }

    private class BundleWiringImpl implements BundleWiring {
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
            throw new UnsupportedOperationException();
        }

        @Override
        public ClassLoader getClassLoader() {
            return ClassLoaderOsgiFramework.this.getClassLoader();
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

    private class SystemBundleImpl extends BundleImpl {
        @Override
        public long getBundleId() {
            return 0L;
        }

        @Override
        public Version getVersion() {
            return Version.emptyVersion;
        }

        @Override
        public Dictionary<String, String> getHeaders() {
            Hashtable<String, String> ret = new Hashtable<>();
            ret.put(OsgiHeader.APPLICATION, StandaloneContainerApplication.class.getName());
            return ret;
        }
    }

    private class JarBundleImpl extends BundleImpl {
        private final long bundleId;
        private final Dictionary<String, String> headers;

        JarBundleImpl(URL location) throws IOException {
            this.bundleId = (long) nextBundleId.getAndIncrement();
            this.headers = retrieveHeaders(location);
        }

        @Override
        public long getBundleId() {
            return bundleId;
        }

        @Override
        public Dictionary<String, String> getHeaders() {
            return headers;
        }

        @Override
        public String getSymbolicName() {
            return headers.get("Bundle-SymbolicName");
        }

        @Override
        public Version getVersion() {
            return Version.parseVersion(headers.get("Bundle-Version"));
        }

        private Dictionary<String, String> retrieveHeaders(URL location) throws IOException {
            try (JarFile jarFile = new JarFile(location.getFile())) {
                Attributes attributes = jarFile.getManifest().getMainAttributes();
                Hashtable<String, String> ret = new Hashtable<>();
                attributes.forEach((k, v) -> ret.put(k.toString(), v.toString()));
                return ret;
            }
        }
    }

    private class BundleContextImpl implements BundleContext {
        @Override
        public String getProperty(String key) {
            return null;
        }

        @Override
        public Bundle getBundle() {
            return systemBundleImpl;
        }

        @Override
        public Bundle installBundle(String location, InputStream input) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle installBundle(String location) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Bundle getBundle(long id) {
            return systemBundleImpl;
        }

        @Override
        public Bundle[] getBundles() {
            return new Bundle[] { systemBundleImpl };
        }

        @Override
        public Bundle getBundle(String location) {
            return systemBundleImpl;
        }

        @Override
        public void addServiceListener(ServiceListener listener, String filter) {
        }

        @Override
        public void addServiceListener(ServiceListener listener) {
        }

        @Override
        public void removeServiceListener(ServiceListener listener) {
        }

        @Override
        public void addBundleListener(BundleListener listener) {
        }

        @Override
        public void removeBundleListener(BundleListener listener) {
        }

        @Override
        public void addFrameworkListener(FrameworkListener listener) {
        }

        @Override
        public void removeFrameworkListener(FrameworkListener listener) {
        }

        @Override
        public ServiceRegistration<?> registerService(String[] classes, Object service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceRegistration<?> registerService(String clazz, Object service, Dictionary<String, ?> properties) {
            return null;
        }

        @Override
        public <S> ServiceRegistration<S> registerService(Class<S> clazz, S service, Dictionary<String, ?> properties) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?>[] getServiceReferences(String clazz, String filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?>[] getAllServiceReferences(String clazz, String filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ServiceReference<?> getServiceReference(String clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> ServiceReference<S> getServiceReference(Class<S> clazz) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> Collection<ServiceReference<S>> getServiceReferences(Class<S> clazz, String filter) {
            return new ArrayList<>();
        }

        @Override
        public <S> S getService(ServiceReference<S> reference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean ungetService(ServiceReference<?> reference) {
            throw new UnsupportedOperationException();
        }

        @Override
        public File getDataFile(String filename) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Filter createFilter(String filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> ServiceRegistration<S> registerService(Class<S> aClass, ServiceFactory<S> serviceFactory,
                Dictionary<String, ?> dictionary) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <S> ServiceObjects<S> getServiceObjects(ServiceReference<S> serviceReference) {
            throw new UnsupportedOperationException();
        }
    }
}
