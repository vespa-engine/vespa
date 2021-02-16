// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.core.config;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.concurrent.ThreadFactoryFactory;
import com.yahoo.config.FileReference;
import com.yahoo.container.di.ComponentDeconstructor;
import com.yahoo.container.di.Container;
import com.yahoo.container.di.componentgraph.core.ComponentGraph;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.container.di.osgi.BundleClasses;
import com.yahoo.container.di.osgi.OsgiUtil;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.filedistribution.fileacquirer.FileAcquirer;
import com.yahoo.jdisc.application.OsgiFramework;
import com.yahoo.jdisc.handler.RequestHandler;
import com.yahoo.jdisc.service.ClientProvider;
import com.yahoo.jdisc.service.ServerProvider;
import com.yahoo.osgi.OsgiImpl;
import com.yahoo.osgi.OsgiWrapper;
import com.yahoo.statistics.Statistics;
import org.osgi.framework.Bundle;
import org.osgi.framework.wiring.BundleWiring;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.collections.CollectionUtil.first;

/**
 * For internal use only.
 *
 * @author Tony Vaagenes
 * @author gjoranv
 */
//TODO: rename
public class HandlersConfigurerDi {

    private static final Logger log = Logger.getLogger(HandlersConfigurerDi.class.getName());

    private static final Executor fallbackExecutor = Executors.newCachedThreadPool(
            ThreadFactoryFactory.getThreadFactory("HandlersConfigurerDI"));

    private final com.yahoo.container.Container vespaContainer;
    private final Container container;

    private volatile ComponentGraph currentGraph = new ComponentGraph(0);

    public HandlersConfigurerDi(SubscriberFactory subscriberFactory,
                                com.yahoo.container.Container vespaContainer,
                                String configId,
                                ComponentDeconstructor deconstructor,
                                Injector discInjector,
                                OsgiFramework osgiFramework) {

        this(subscriberFactory, vespaContainer, configId, deconstructor, discInjector,
             new ContainerAndDiOsgi(osgiFramework, vespaContainer.getFileAcquirer()));
    }

    // Only public for testing
    public HandlersConfigurerDi(SubscriberFactory subscriberFactory,
                                com.yahoo.container.Container vespaContainer,
                                String configId,
                                ComponentDeconstructor deconstructor,
                                Injector discInjector,
                                OsgiWrapper osgiWrapper) {

        this.vespaContainer = vespaContainer;
        container = new Container(subscriberFactory, configId, deconstructor, osgiWrapper);
        getNewComponentGraph(discInjector, true);
    }

    private static class ContainerAndDiOsgi extends OsgiImpl implements OsgiWrapper {

        private final OsgiFramework osgiFramework;
        private final ApplicationBundleLoader applicationBundleLoader;
        private final PlatformBundleLoader platformBundleLoader;

        public ContainerAndDiOsgi(OsgiFramework osgiFramework, FileAcquirer fileAcquirer) {
            super(osgiFramework);
            this.osgiFramework = osgiFramework;

            applicationBundleLoader = new ApplicationBundleLoader(this, new FileAcquirerBundleInstaller(fileAcquirer));
            platformBundleLoader = new PlatformBundleLoader(this);
        }


        // TODO Vespa 8: Remove, only used for Jersey
        @Override
        public BundleClasses getBundleClasses(ComponentSpecification bundleSpec, Set<String> packagesToScan) {
            //Temporary hack: Using class name since ClassLoaderOsgiFramework is not available at compile time in this bundle.
            if (osgiFramework.getClass().getName().equals("com.yahoo.application.container.impl.ClassLoaderOsgiFramework")) {
                Bundle syntheticClassPathBundle = first(osgiFramework.bundles());
                ClassLoader classLoader = syntheticClassPathBundle.adapt(BundleWiring.class).getClassLoader();

                return new BundleClasses(
                        syntheticClassPathBundle,
                        OsgiUtil.getClassEntriesForBundleUsingProjectClassPathMappings(classLoader, bundleSpec, packagesToScan));
            } else {
                Bundle bundle = getBundle(bundleSpec);
                if (bundle == null)
                    throw new RuntimeException("No bundle matching '" + bundleSpec + "'");

                return new BundleClasses(bundle, OsgiUtil.getClassEntriesInBundleClassPath(bundle, packagesToScan));
            }
        }

        @Override
        public void installPlatformBundles(Collection<String> bundlePaths) {
            log.fine("Installing platform bundles.");
            platformBundleLoader.useBundles(new ArrayList<>(bundlePaths));
        }

        @Override
        public Set<Bundle> useApplicationBundles(Collection<FileReference> bundles) {
            log.info("Installing bundles from the latest application");
            return applicationBundleLoader.useBundles(new ArrayList<>(bundles));
        }
    }

    /**
     * Wait for new config to arrive and produce the new graph
     */
    public void getNewComponentGraph(Injector discInjector, boolean isInitializing) {
        currentGraph = container.getNewComponentGraph(currentGraph,
                                                      createFallbackInjector(vespaContainer, discInjector),
                                                      isInitializing);
    }

    private Injector createFallbackInjector(com.yahoo.container.Container vespaContainer, Injector discInjector) {
        return discInjector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
                // Provide a singleton instance for all component fallbacks,
                // otherwise fallback injection may lead to a cascade of components requiring reconstruction.
                bind(com.yahoo.container.Container.class).toInstance(vespaContainer);
                bind(com.yahoo.statistics.Statistics.class).toInstance(Statistics.nullImplementation);
                bind(AccessLog.class).toInstance(AccessLog.NONE_INSTANCE);
                bind(Executor.class).toInstance(fallbackExecutor);
                if (vespaContainer.getFileAcquirer() != null)
                    bind(com.yahoo.filedistribution.fileacquirer.FileAcquirer.class).toInstance(vespaContainer.getFileAcquirer());
            }
        });
    }

    public void reloadConfig(long generation) {
        container.reloadConfig(generation);
    }

    public <T> T getComponent(Class<T> componentClass) {
        return currentGraph.getInstance(componentClass);
    }

    public void shutdown(ComponentDeconstructor deconstructor) {
        container.shutdown(currentGraph, deconstructor);
    }

    /** Returns the currently active application configuration generation */
    public long generation() { return currentGraph.generation(); }

    public static class RegistriesHack {

        @Inject
        public RegistriesHack(com.yahoo.container.Container vespaContainer,
                              ComponentRegistry<AbstractComponent> allComponents,
                              ComponentRegistry<RequestHandler> requestHandlerRegistry,
                              ComponentRegistry<ClientProvider> clientProviderRegistry,
                              ComponentRegistry<ServerProvider> serverProviderRegistry) {
            log.log(Level.FINE, "RegistriesHack.init " + System.identityHashCode(this));

            vespaContainer.setComponentRegistry(allComponents);
            vespaContainer.setRequestHandlerRegistry(requestHandlerRegistry);
            vespaContainer.setClientProviderRegistry(clientProviderRegistry);
            vespaContainer.setServerProviderRegistry(serverProviderRegistry);
        }

    }

}
