// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc.osgi;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.framework.hooks.resolver.ResolverHookFactory;

import java.util.Hashtable;

/**
 * @author tonytv
 */
public class ContainerBundleActivator implements BundleActivator {

    private ServiceRegistration<ResolverHookFactory> resolverHookFactoryServiceRegistration;

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        resolverHookFactoryServiceRegistration = bundleContext.registerService(
                ResolverHookFactory.class,
                new JacksonJaxrsResolverHook.Factory(),
                new Hashtable<>());
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        resolverHookFactoryServiceRegistration.unregister();
    }

}
