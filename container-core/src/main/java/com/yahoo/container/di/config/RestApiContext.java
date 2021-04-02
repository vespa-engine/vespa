// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.config;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Inject;
import com.google.inject.Key;
import com.yahoo.component.ComponentId;
import org.osgi.framework.Version;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Only for internal JDisc use.
 *
 * @author gjoranv
 */
public class RestApiContext {

    private final List<BundleInfo> bundles = new ArrayList<>();
    private final List<Injectable> injectableComponents = new ArrayList<>();

    public final JerseyBundlesConfig bundlesConfig;
    public final JerseyInjectionConfig injectionConfig;

    @Inject
    public RestApiContext(JerseyBundlesConfig bundlesConfig, JerseyInjectionConfig injectionConfig) {
        this.bundlesConfig = bundlesConfig;
        this.injectionConfig = injectionConfig;
    }

    public List<BundleInfo> getBundles() {
        return Collections.unmodifiableList(bundles);
    }

    public void addBundle(BundleInfo bundle) {
        bundles.add(bundle);
    }

    public List<Injectable> getInjectableComponents() {
        return Collections.unmodifiableList(injectableComponents);
    }

    public void addInjectableComponent(Key<?> key, ComponentId id, Object component) {
        injectableComponents.add(new Injectable(key, id, component));
    }

    public static class Injectable {
        public final Key<?> key;
        public final ComponentId id;
        public final Object instance;

        public Injectable(Key<?> key, ComponentId id, Object instance) {
            this.key = key;
            this.id = id;
            this.instance = instance;
        }
        @Override
        public String toString() {
            return id.toString();
        }
    }

    public static class BundleInfo {
        public final String symbolicName;
        public final Version version;
        public final String fileLocation;
        public final URL webInfUrl;
        public final ClassLoader classLoader;

        private Set<String> classEntries;

        public BundleInfo(String symbolicName, Version version, String fileLocation, URL webInfUrl, ClassLoader classLoader) {
            this.symbolicName = symbolicName;
            this.version = version;
            this.fileLocation = fileLocation;
            this.webInfUrl = webInfUrl;
            this.classLoader = classLoader;
        }

        @Override
        public String toString() {
            return symbolicName + ":" + version;
        }

        public void setClassEntries(Collection<String> entries) {
            this.classEntries = ImmutableSet.copyOf(entries);
        }

        public Set<String> getClassEntries() {
            return classEntries;
        }
    }
}
