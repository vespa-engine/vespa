// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package demo;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.yahoo.component.ComponentId;
import com.yahoo.config.ConfigInstance;
import com.yahoo.container.di.componentgraph.core.ComponentGraph;
import com.yahoo.container.di.componentgraph.core.ComponentNode;
import com.yahoo.container.di.componentgraph.core.Node;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.Before;

import java.util.HashMap;
import java.util.Map;

/**
 * @author tonytv
 * @author gjoranv
 */
public class Base {
    private ComponentGraph componentGraph;
    private Injector injector;
    private Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs =
            new HashMap<>();

    @Before
    public void createGraph() {
        injector = Guice.createInjector();
        componentGraph = new ComponentGraph(0);
    }

    public void register(Class<?> componentClass) {
        componentGraph.add(mockComponentNode(componentClass));
    }

    public ComponentId toId(Class<?> componentClass) {
        return ComponentId.fromString(componentClass.getName());
    }

    @SuppressWarnings("unchecked")
    private Node mockComponentNode(Class<?> componentClass) {
        return new ComponentNode(toId(componentClass), toId(componentClass).toString(), (Class<Object>)componentClass, null);
    }

    public <T> T getInstance(Class<T> componentClass) {
        return componentGraph.getInstance(componentClass);
    }

    public void complete() {
        componentGraph.complete(injector);
        componentGraph.setAvailableConfigs(configs);
    }

    public void setInjector(Injector injector) {
        this.injector = injector;
    }

    @SuppressWarnings("unchecked")
    public void addConfig(ConfigInstance configInstance, ComponentId id) {
        configs.put(new ConfigKey<>((Class<ConfigInstance>)configInstance.getClass(), id.toString()),
                configInstance);
    }
}
