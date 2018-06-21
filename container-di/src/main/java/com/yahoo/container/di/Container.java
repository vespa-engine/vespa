// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.ConfigurationRuntimeException;
import com.yahoo.config.subscription.ConfigInterruptedException;
import com.yahoo.container.BundlesConfig;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.di.ConfigRetriever.BootstrapConfigs;
import com.yahoo.container.di.ConfigRetriever.ConfigSnapshot;
import com.yahoo.container.di.componentgraph.core.ComponentGraph;
import com.yahoo.container.di.componentgraph.core.ComponentNode;
import com.yahoo.container.di.componentgraph.core.JerseyNode;
import com.yahoo.container.di.componentgraph.core.Node;
import com.yahoo.container.di.config.RestApiContext;
import com.yahoo.container.di.config.SubscriberFactory;
import com.yahoo.vespa.config.ConfigKey;

import java.time.Duration;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.yahoo.log.LogLevel.DEBUG;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 * @author ollivir
 */
public class Container {
    private final SubscriberFactory subscriberFactory;
    private ConfigKey<BundlesConfig> bundlesConfigKey;
    private ConfigKey<ComponentsConfig> componentsConfigKey;
    private final ComponentDeconstructor componentDeconstructor;
    private final Osgi osgi;

    private ConfigRetriever configurer;
    private long previousConfigGeneration = -1L;
    private long leastGeneration = -1L;

    public Container(SubscriberFactory subscriberFactory, String configId, ComponentDeconstructor componentDeconstructor, Osgi osgi) {
        this.subscriberFactory = subscriberFactory;
        this.bundlesConfigKey = new ConfigKey<>(BundlesConfig.class, configId);
        this.componentsConfigKey = new ConfigKey<>(ComponentsConfig.class, configId);
        this.componentDeconstructor = componentDeconstructor;
        this.osgi = osgi;

        Set<ConfigKey<? extends ConfigInstance>> keySet = new HashSet<>();
        keySet.add(bundlesConfigKey);
        keySet.add(componentsConfigKey);
        this.configurer = new ConfigRetriever(keySet, subscriberFactory::getSubscriber);
    }

    public Container(SubscriberFactory subscriberFactory, String configId, ComponentDeconstructor componentDeconstructor) {
        this(subscriberFactory, configId, componentDeconstructor, new Osgi() {
        });
    }

    private void deconstructObsoleteComponents(ComponentGraph oldGraph, ComponentGraph newGraph) {
        IdentityHashMap<Object, Object> oldComponents = new IdentityHashMap<>();
        oldGraph.allComponentsAndProviders().forEach(c -> oldComponents.put(c, null));
        newGraph.allComponentsAndProviders().forEach(oldComponents::remove);
        oldComponents.keySet().forEach(componentDeconstructor::deconstruct);
    }

    public ComponentGraph getNewComponentGraph(ComponentGraph oldGraph, Injector fallbackInjector, boolean restartOnRedeploy) {

        try {
            ComponentGraph newGraph = getConfigAndCreateGraph(oldGraph, fallbackInjector, restartOnRedeploy);
            newGraph.reuseNodes(oldGraph);
            constructComponents(newGraph);
            deconstructObsoleteComponents(oldGraph, newGraph);
            return newGraph;
        } catch (Throwable t) {
            // TODO: Wrap ComponentConstructorException in an Error when generation==0 (+ unit test that Error is thrown)
            invalidateGeneration(oldGraph.generation(), t);
            throw t;
        }
    }

    public ComponentGraph getNewComponentGraph(ComponentGraph oldGraph) {
        return getNewComponentGraph(oldGraph, Guice.createInjector(), false);
    }

    public ComponentGraph getNewComponentGraph() {
        return getNewComponentGraph(new ComponentGraph(), Guice.createInjector(), false);
    }

    private static String newGraphErrorMessage(long generation, Throwable cause, Duration maxWaitToExit) {
        String failedFirstMessage = "Failed to set up first component graph";
        String failedNewMessage = "Failed to set up new component graph";
        String constructMessage = " due to error when constructing one of the components";
        String exitMessage = ". Exiting within " + maxWaitToExit.toString();
        String retainMessage = ". Retaining previous component generation.";

        if (generation == 0) {
            if (cause instanceof ComponentNode.ComponentConstructorException) {
                return failedFirstMessage + constructMessage + exitMessage;
            } else {
                return failedFirstMessage + exitMessage;
            }
        } else {
            if (cause instanceof ComponentNode.ComponentConstructorException) {
                return failedNewMessage + constructMessage + retainMessage;
            } else {
                return failedNewMessage + retainMessage;
            }
        }
    }

    private void invalidateGeneration(long generation, Throwable cause) {
        Duration maxWaitToExit = Duration.ofSeconds(60);
        leastGeneration = Math.max(configurer.getComponentsGeneration(), configurer.getBootstrapGeneration()) + 1;
        if (!(cause instanceof InterruptedException) && !(cause instanceof ConfigInterruptedException)) {
            log.log(Level.WARNING, newGraphErrorMessage(generation, cause, maxWaitToExit), cause);
        }
    }

    public ComponentGraph getConfigAndCreateGraph(ComponentGraph graph, Injector fallbackInjector, boolean restartOnRedeploy) {

        ConfigSnapshot snapshot;

        while (true) {
            snapshot = configurer.getConfigs(graph.configKeys(), leastGeneration, restartOnRedeploy);

            log.log(DEBUG, String.format("createNewGraph:\n" + "graph.configKeys = %s\n" + "graph.generation = %s\n" + "snapshot = %s\n",
                    graph.configKeys(), graph.generation(), snapshot));

            if (snapshot instanceof BootstrapConfigs) {
                // TODO: remove require when proven unnecessary
                if (getBootstrapGeneration() <= previousConfigGeneration) {
                    throw new IllegalStateException(String.format(
                            "Got bootstrap configs out of sequence for old config generation %d.\n" + "Previous config generation is %d",
                            getBootstrapGeneration(), previousConfigGeneration));
                }
                log.log(DEBUG,
                        String.format(
                                "Got new bootstrap generation\n" + "bootstrap generation = %d\n" + "components generation: %d\n"
                                        + "previous generation: %d\n",
                                getBootstrapGeneration(), getComponentsGeneration(), previousConfigGeneration));
                installBundles(snapshot.configs());
                graph = createComponentsGraph(snapshot.configs(), getBootstrapGeneration(), fallbackInjector);
                // Continues loop

            } else if (snapshot instanceof ConfigRetriever.ComponentsConfigs) {
                break;
            }
        }
        log.log(DEBUG,
                String.format(
                        "Got components configs,\n" + "bootstrap generation = %d\n" + "components generation: %d\n"
                                + "previous generation: %d",
                        getBootstrapGeneration(), getComponentsGeneration(), previousConfigGeneration));
        return createAndConfigureComponentsGraph(snapshot.configs(), fallbackInjector);
    }

    private long getBootstrapGeneration() {
        return configurer.getBootstrapGeneration();
    }

    private long getComponentsGeneration() {
        return configurer.getComponentsGeneration();
    }

    private ComponentGraph createAndConfigureComponentsGraph(Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> componentsConfigs,
            Injector fallbackInjector) {
        ComponentGraph componentGraph = createComponentsGraph(componentsConfigs, getComponentsGeneration(), fallbackInjector);
        componentGraph.setAvailableConfigs(componentsConfigs);
        return componentGraph;
    }

    private void injectNodes(ComponentsConfig config, ComponentGraph graph) {
        for (ComponentsConfig.Components component : config.components()) {
            Node componentNode = ComponentGraph.getNode(graph, component.id());

            for (ComponentsConfig.Components.Inject inject : component.inject()) {
                //TODO: Support inject.name()
                componentNode.inject(ComponentGraph.getNode(graph, inject.id()));
            }
        }
    }

    public void installBundles(Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configsIncludingBootstrapConfigs) {
        BundlesConfig bundlesConfig = getConfig(bundlesConfigKey, configsIncludingBootstrapConfigs);
        osgi.useBundles(bundlesConfig.bundle());
    }

    private ComponentGraph createComponentsGraph(Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configsIncludingBootstrapConfigs,
            long generation, Injector fallbackInjector) {

        previousConfigGeneration = generation;

        ComponentGraph graph = new ComponentGraph(generation);

        ComponentsConfig componentsConfig = getConfig(componentsConfigKey, configsIncludingBootstrapConfigs);
        if (componentsConfig == null) {
            throw new ConfigurationRuntimeException("The set of all configs does not include a valid 'components' config. Config set: "
                    + configsIncludingBootstrapConfigs.keySet());
        }
        addNodes(componentsConfig, graph);
        injectNodes(componentsConfig, graph);

        graph.complete(fallbackInjector);
        return graph;
    }

    private void addNodes(ComponentsConfig componentsConfig, ComponentGraph graph) {

        for (ComponentsConfig.Components config : componentsConfig.components()) {
            BundleInstantiationSpecification specification = bundleInstatiationSpecification(config);
            Class<?> componentClass = osgi.resolveClass(specification);
            Node componentNode;

            if (RestApiContext.class.isAssignableFrom(componentClass)) {
                Class<? extends RestApiContext> nodeClass = componentClass.asSubclass(RestApiContext.class);
                componentNode = new JerseyNode(specification.id, config.configId(), nodeClass, osgi);
            } else {
                componentNode = new ComponentNode(specification.id, config.configId(), componentClass, null);
            }
            graph.add(componentNode);
        }
    }

    private void constructComponents(ComponentGraph graph) {
        graph.nodes().forEach(Node::newOrCachedInstance);
    }

    public void shutdown(ComponentGraph graph, ComponentDeconstructor deconstructor) {
        shutdownConfigurer();
        if (graph != null) {
            deconstructAllComponents(graph, deconstructor);
        }
    }

    public void shutdownConfigurer() {
        configurer.shutdown();
    }

    // Reload config manually, when subscribing to non-configserver sources
    public void reloadConfig(long generation) {
        subscriberFactory.reloadActiveSubscribers(generation);
    }

    private void deconstructAllComponents(ComponentGraph graph, ComponentDeconstructor deconstructor) {
        graph.allComponentsAndProviders().forEach(deconstructor::deconstruct);
    }

    private static final Logger log = Logger.getLogger(Container.class.getName());

    public static <T extends ConfigInstance> T getConfig(ConfigKey<T> key,
            Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs) {
        ConfigInstance inst = configs.get(key);

        if (inst == null || key.getConfigClass() == null) {
            throw new RuntimeException("Missing config " + key);
        }

        return key.getConfigClass().cast(inst);
    }

    public static BundleInstantiationSpecification bundleInstatiationSpecification(ComponentsConfig.Components config) {
        return BundleInstantiationSpecification.getFromStrings(config.id(), config.classId(), config.bundle());
    }
}
