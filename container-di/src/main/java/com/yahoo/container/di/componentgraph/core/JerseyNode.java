// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.container.di.Osgi;
import com.yahoo.container.di.config.JerseyBundlesConfig;
import com.yahoo.container.di.config.RestApiContext;
import com.yahoo.container.di.config.RestApiContext.BundleInfo;
import com.yahoo.container.di.osgi.BundleClasses;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

import java.net.URL;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Represents an instance of RestApiContext
 *
 * @author gjoranv
 * @author Tony Vaagenes
 * @author ollivir
 */
public class JerseyNode extends ComponentNode {
    private static final String WEB_INF_URL = "WebInfUrl";

    private final Osgi osgi;

    public JerseyNode(ComponentId componentId, String configId, Class<?> clazz, Osgi osgi) {
        super(componentId, configId, clazz, null);
        this.osgi = osgi;
    }

    @Override
    protected RestApiContext newInstance() {
        Object instance = super.newInstance();
        RestApiContext restApiContext = (RestApiContext) instance;

        List<JerseyBundlesConfig.Bundles> bundles = restApiContext.bundlesConfig.bundles();
        for (JerseyBundlesConfig.Bundles bundleConfig : bundles) {
            BundleClasses bundleClasses = osgi.getBundleClasses(ComponentSpecification.fromString(bundleConfig.spec()),
                    new HashSet<>(bundleConfig.packages()));

            restApiContext.addBundle(createBundleInfo(bundleClasses.bundle(), bundleClasses.classEntries()));
        }

        componentsToInject.forEach(component -> restApiContext.addInjectableComponent(component.instanceKey(), component.componentId(),
                component.newOrCachedInstance()));

        return restApiContext;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other)
                && (other instanceof JerseyNode && this.componentsToInject.equals(((JerseyNode) other).componentsToInject));
    }

    public static BundleInfo createBundleInfo(Bundle bundle, Collection<String> classEntries) {
        BundleInfo bundleInfo = new BundleInfo(bundle.getSymbolicName(), bundle.getVersion(), bundle.getLocation(), webInfUrl(bundle),
                bundle.adapt(BundleWiring.class).getClassLoader());

        bundleInfo.setClassEntries(classEntries);
        return bundleInfo;
    }

    public static Bundle getBundle(Osgi osgi, String bundleSpec) {
        Bundle bundle = osgi.getBundle(ComponentSpecification.fromString(bundleSpec));
        if (bundle == null) {
            throw new IllegalArgumentException("Bundle not found: " + bundleSpec);
        }
        return bundle;
    }

    private static URL webInfUrl(Bundle bundle) {
        String webInfUrlHeader = bundle.getHeaders().get(WEB_INF_URL);

        if (webInfUrlHeader == null) {
            return null;
        } else {
            return bundle.getEntry(webInfUrlHeader);
        }
    }

}
