// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.test.TestConfig;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.ComponentTakingAllSimpleComponents;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.ComponentTakingComponent;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.ComponentTakingConfig;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.ComponentTakingConfigAndComponent;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.ComponentTakingExecutor;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.ExecutorProvider;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.SimpleComponent;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.SimpleComponent2;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 * @author ollivir
 */
public class ReuseComponentsTest {
    @Test
    public void require_that_component_is_reused_when_componentNode_is_unmodified() {
        reuseAndTest(SimpleComponent.class, SimpleComponent.class);
        reuseAndTest(ExecutorProvider.class, Executor.class);
    }

    private <T> void reuseAndTest(Class<?> classToRegister, Class<T> classToLookup) {
        ComponentGraph graph = buildGraphAndSetNoConfigs(classToRegister);
        T instance = getComponent(graph, classToLookup);

        ComponentGraph newGraph = buildGraphAndSetNoConfigs(classToRegister);
        newGraph.reuseNodes(graph);
        T instance2 = getComponent(newGraph, classToLookup);

        assertThat(instance2, sameInstance(instance));
    }

    @Test(expected = IllegalStateException.class)
    public void require_that_component_is_not_reused_when_class_is_changed() {
        ComponentGraph graph = buildGraphAndSetNoConfigs(SimpleComponent.class);
        SimpleComponent instance = getComponent(graph, SimpleComponent.class);

        ComponentGraph newGraph = buildGraphAndSetNoConfigs(SimpleComponent2.class);
        newGraph.reuseNodes(graph);
        SimpleComponent2 instance2 = getComponent(newGraph, SimpleComponent2.class);

        assertThat(instance2.getId(), is(instance.getId()));
        @SuppressWarnings("unused")
        SimpleComponent throwsException = getComponent(newGraph, SimpleComponent.class);
    }

    @Test
    public void require_that_component_is_not_reused_when_config_is_changed() {
        Class<ComponentTakingConfig> componentClass = ComponentTakingConfig.class;

        ComponentGraph graph = buildGraph(componentClass);
        graph.setAvailableConfigs(Collections.singletonMap(new ConfigKey<>(TestConfig.class, "component"),
                ConfigGetter.getConfig(TestConfig.class, "raw: stringVal \"oldConfig\"")));
        ComponentTakingConfig instance = getComponent(graph, componentClass);

        ComponentGraph newGraph = buildGraph(componentClass);
        newGraph.setAvailableConfigs(Collections.singletonMap(new ConfigKey<>(TestConfig.class, "component"),
                ConfigGetter.getConfig(TestConfig.class, "raw: stringVal \"newConfig\"")));
        newGraph.reuseNodes(graph);
        ComponentTakingConfig instance2 = getComponent(newGraph, componentClass);

        assertThat(instance2, not(sameInstance(instance)));
    }

    @Test
    public void require_that_component_is_not_reused_when_injected_component_is_changed() {
        Function<String, ComponentGraph> buildGraph = config -> {
            ComponentGraph graph = new ComponentGraph();

            ComponentNode rootComponent = mockComponentNode(ComponentTakingComponent.class, "root_component");

            String configId = "componentTakingConfigId";
            ComponentNode injectedComponent = mockComponentNode(ComponentTakingConfig.class, "injected_component", configId);

            rootComponent.inject(injectedComponent);

            graph.add(rootComponent);
            graph.add(injectedComponent);

            graph.complete();
            graph.setAvailableConfigs(Collections.singletonMap(new ConfigKey<>(TestConfig.class, configId),
                    ConfigGetter.getConfig(TestConfig.class, "raw: stringVal \"" + config + "\"")));

            return graph;
        };

        ComponentGraph oldGraph = buildGraph.apply("oldGraph");
        ComponentTakingComponent oldInstance = getComponent(oldGraph, ComponentTakingComponent.class);

        ComponentGraph newGraph = buildGraph.apply("newGraph");
        newGraph.reuseNodes(oldGraph);
        ComponentTakingComponent newInstance = getComponent(newGraph, ComponentTakingComponent.class);

        assertThat(newInstance, not(sameInstance(oldInstance)));
    }

    @Test
    public void require_that_component_is_not_reused_when_injected_component_registry_has_one_component_removed() {
        Function<Boolean, ComponentGraph> buildGraph = useBothInjectedComponents -> {
            ComponentGraph graph = new ComponentGraph();
            graph.add(mockComponentNode(ComponentTakingAllSimpleComponents.class, "root_component"));

            /* Below if-else has code duplication, but explicit ordering of the two components
             * was necessary to reproduce erroneous behaviour in ComponentGraph.reuseNodes that
             * occurred before ComponentRegistryNode got its own 'equals' implementation.
             */
            if (useBothInjectedComponents) {
                graph.add(mockComponentNode(SimpleComponent.class, "injected_component2"));
                graph.add(mockComponentNode(SimpleComponent.class, "injected_component1"));
            } else {
                graph.add(mockComponentNode(SimpleComponent.class, "injected_component1"));
            }

            graph.complete();
            graph.setAvailableConfigs(Collections.emptyMap());
            return graph;
        };

        ComponentGraph oldGraph = buildGraph.apply(true);
        ComponentRegistry<SimpleComponent> oldSimpleComponentRegistry = getComponent(oldGraph, ComponentTakingAllSimpleComponents.class).simpleComponents;

        ComponentGraph newGraph = buildGraph.apply(false);
        newGraph.reuseNodes(oldGraph);
        ComponentRegistry<SimpleComponent> newSimpleComponentRegistry = getComponent(newGraph, ComponentTakingAllSimpleComponents.class).simpleComponents;

        assertThat(newSimpleComponentRegistry, not(sameInstance(oldSimpleComponentRegistry)));
    }

    @Test
    public void require_that_injected_component_is_reused_even_when_dependent_component_is_changed() {
        Function<String, ComponentGraph> buildGraph = config -> {
            ComponentGraph graph = new ComponentGraph();

            String configId = "componentTakingConfigAndComponent";
            ComponentNode rootComponent = mockComponentNode(ComponentTakingConfigAndComponent.class, "root_component", configId);

            ComponentNode injectedComponent = mockComponentNode(SimpleComponent.class, "injected_component");

            rootComponent.inject(injectedComponent);

            graph.add(rootComponent);
            graph.add(injectedComponent);

            graph.complete();
            graph.setAvailableConfigs(Collections.singletonMap(new ConfigKey<>(TestConfig.class, configId),
                    ConfigGetter.getConfig(TestConfig.class, "raw: stringVal \"" + config + "\"")));

            return graph;
        };

        ComponentGraph oldGraph = buildGraph.apply("oldGraph");
        SimpleComponent oldInjectedComponent = getComponent(oldGraph, SimpleComponent.class);
        ComponentTakingConfigAndComponent oldDependentComponent = getComponent(oldGraph, ComponentTakingConfigAndComponent.class);

        ComponentGraph newGraph = buildGraph.apply("newGraph");
        newGraph.reuseNodes(oldGraph);
        SimpleComponent newInjectedComponent = getComponent(newGraph, SimpleComponent.class);
        ComponentTakingConfigAndComponent newDependentComponent = getComponent(newGraph, ComponentTakingConfigAndComponent.class);

        assertThat(newDependentComponent, not(sameInstance(oldDependentComponent)));
        assertThat(newInjectedComponent, sameInstance(oldInjectedComponent));
    }

    @Test
    public void require_that_node_depending_on_guice_node_is_reused() {
        Supplier<ComponentGraph> makeGraph = () -> {
            ComponentGraph graph = new ComponentGraph();
            graph.add(mockComponentNode(ComponentTakingExecutor.class, "dummyId"));
            graph.complete(ComponentGraphTest.singletonExecutorInjector);
            graph.setAvailableConfigs(Collections.emptyMap());
            return graph;
        };

        Function<ComponentGraph, ComponentTakingExecutor> componentRetriever = graph -> getComponent(graph, ComponentTakingExecutor.class);

        ComponentGraph oldGraph = makeGraph.get();
        componentRetriever.apply(oldGraph);  // Ensure creation of GuiceNode
        ComponentGraph newGraph = makeGraph.get();
        newGraph.reuseNodes(oldGraph);
        assertThat(componentRetriever.apply(oldGraph), sameInstance(componentRetriever.apply(newGraph)));
    }

    @Test
    public void require_that_node_equals_only_checks_first_level_components_to_inject() {
        Function<String, Node> createNodeWithInjectedNodeWithInjectedNode = indirectlyInjectedComponentId -> {
            ComponentNode targetComponent = mockComponentNode(SimpleComponent.class, "target");
            ComponentNode directlyInjectedComponent = mockComponentNode(SimpleComponent.class, "directlyInjected");
            ComponentNode indirectlyInjectedComponent = mockComponentNode(SimpleComponent.class, indirectlyInjectedComponentId);
            directlyInjectedComponent.inject(indirectlyInjectedComponent);
            targetComponent.inject(directlyInjectedComponent);

            completeNode(targetComponent);
            completeNode(directlyInjectedComponent);
            completeNode(indirectlyInjectedComponent);

            return targetComponent;
        };

        Node targetNode1 = createNodeWithInjectedNodeWithInjectedNode.apply("indirectlyInjected_1");
        Node targetNode2 = createNodeWithInjectedNodeWithInjectedNode.apply("indirectlyInjected_2");
        assertThat(targetNode1, equalTo(targetNode2));
    }

    private void completeNode(ComponentNode node) {
        node.setArguments(new Object[0]);
        node.setAvailableConfigs(Collections.emptyMap());
    }

    private ComponentGraph buildGraph(Class<?> componentClass) {
        String commonComponentId = "component";
        ComponentGraph g = new ComponentGraph();
        g.add(mockComponentNode(componentClass, commonComponentId, commonComponentId));
        g.complete();
        return g;
    }

    private ComponentGraph buildGraphAndSetNoConfigs(Class<?> componentClass) {
        ComponentGraph g = buildGraph(componentClass);
        g.setAvailableConfigs(Collections.emptyMap());
        return g;
    }

    private static ComponentNode mockComponentNode(Class<?> clazz, String componentId, String configId) {
        return new ComponentNode(new ComponentId(componentId), configId, clazz);
    }

    private static ComponentNode mockComponentNode(Class<?> clazz, String componentId) {
        return mockComponentNode(clazz, componentId, "");
    }

    private static <T> T getComponent(ComponentGraph graph, Class<T> clazz) {
        return graph.getInstance(clazz);
    }
}
