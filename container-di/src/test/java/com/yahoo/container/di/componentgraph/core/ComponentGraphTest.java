// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.yahoo.collections.Pair;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.ConfigInstance;
import com.yahoo.config.subscription.ConfigGetter;
import com.yahoo.config.test.Test2Config;
import com.yahoo.config.test.TestConfig;
import com.yahoo.container.di.Osgi;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.di.config.JerseyBundlesConfig;
import com.yahoo.container.di.config.JerseyInjectionConfig;
import com.yahoo.container.di.config.RestApiContext;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static com.yahoo.container.di.componentgraph.core.ComponentGraph.isBindingAnnotation;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author gjoranv
 * @author tonytv
 * @author ollivir
 */
public class ComponentGraphTest {
    public static class ConfigMap extends HashMap<ConfigKey<? extends ConfigInstance>, ConfigInstance> {
        public ConfigMap() {
            super();
        }

        public <T extends ConfigInstance> ConfigMap add(Class<T> clazz, String configId) {
            ConfigKey<T> key = new ConfigKey<>(clazz, configId);
            put(key, ConfigGetter.getConfig(key.getConfigClass(), key.getConfigId()));
            return this;
        }

        public static <T extends ConfigInstance> ConfigMap newMap(Class<T> clazz, String configId) {
            ConfigMap ret = new ConfigMap();
            ret.add(clazz, configId);
            return ret;
        }
    }

    @Test
    public void component_taking_config_can_be_instantiated() {
        ComponentGraph componentGraph = new ComponentGraph();
        String configId = "raw:stringVal \"test-value\"";
        Node componentNode = mockComponentNode(ComponentTakingConfig.class, configId);

        componentGraph.add(componentNode);
        componentGraph.complete();
        componentGraph.setAvailableConfigs(ConfigMap.newMap(TestConfig.class, configId));

        ComponentTakingConfig instance = componentGraph.getInstance(ComponentTakingConfig.class);
        assertNotNull(instance);
        assertThat(instance.config.stringVal(), is("test-value"));
    }

    @Test
    public void component_can_be_injected_into_another_component() {
        Node injectedComponent = mockComponentNode(SimpleComponent.class);
        Node targetComponent = mockComponentNode(ComponentTakingComponent.class);
        targetComponent.inject(injectedComponent);

        Node destroyGlobalLookupComponent = mockComponentNode(SimpleComponent.class);

        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(injectedComponent);
        componentGraph.add(targetComponent);
        componentGraph.add(destroyGlobalLookupComponent);
        componentGraph.complete();

        ComponentTakingComponent instance = componentGraph.getInstance(ComponentTakingComponent.class);
        assertNotNull(instance);
    }

    @Test
    public void all_components_of_a_type_can_be_injected() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(SimpleComponent.class));
        componentGraph.add(mockComponentNode(SimpleComponent.class));
        componentGraph.add(mockComponentNode(SimpleDerivedComponent.class));
        componentGraph.add(mockComponentNode(ComponentTakingAllSimpleComponents.class));
        componentGraph.complete();

        ComponentTakingAllSimpleComponents instance = componentGraph.getInstance(ComponentTakingAllSimpleComponents.class);
        assertThat(instance.simpleComponents.allComponents().size(), is(3));
    }

    @Test
    public void empty_component_registry_can_be_injected() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(ComponentTakingAllSimpleComponents.class));
        componentGraph.complete();

        ComponentTakingAllSimpleComponents instance = componentGraph.getInstance(ComponentTakingAllSimpleComponents.class);
        assertThat(instance.simpleComponents.allComponents().size(), is(0));
    }

    @Test
    public void component_registry_with_wildcard_upper_bound_can_be_injected() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(SimpleComponent.class));
        componentGraph.add(mockComponentNode(SimpleDerivedComponent.class));
        componentGraph.add(mockComponentNode(ComponentTakingAllSimpleComponentsUpperBound.class));
        componentGraph.complete();

        ComponentTakingAllSimpleComponentsUpperBound instance = componentGraph
                .getInstance(ComponentTakingAllSimpleComponentsUpperBound.class);
        assertThat(instance.simpleComponents.allComponents().size(), is(2));
    }

    @Test(expected = RuntimeException.class)
    public void require_exception_when_injecting_registry_with_unknown_type_variable() {
        @SuppressWarnings("rawtypes")
        Class<ComponentTakingAllComponentsWithTypeVariable> clazz = ComponentTakingAllComponentsWithTypeVariable.class;

        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(SimpleComponent.class));
        componentGraph.add(mockComponentNode(SimpleDerivedComponent.class));
        componentGraph.add(mockComponentNode(clazz));
        componentGraph.complete();

        componentGraph.getInstance(clazz);
    }

    @Test
    public void components_are_shared() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(SimpleComponent.class));
        componentGraph.complete();

        SimpleComponent instance1 = componentGraph.getInstance(SimpleComponent.class);
        SimpleComponent instance2 = componentGraph.getInstance(SimpleComponent.class);
        assertThat(instance1, sameInstance(instance2));
    }

    @Test
    public void singleton_components_can_be_injected() {
        ComponentGraph componentGraph = new ComponentGraph();
        String configId = "raw:stringVal \"test-value\"";

        componentGraph.add(mockComponentNode(ComponentTakingComponent.class));
        componentGraph.add(mockComponentNode(ComponentTakingConfig.class, configId));
        componentGraph.add(mockComponentNode(SimpleComponent2.class));
        componentGraph.complete();
        componentGraph.setAvailableConfigs(ConfigMap.newMap(TestConfig.class, configId));

        ComponentTakingComponent instance = componentGraph.getInstance(ComponentTakingComponent.class);
        ComponentTakingConfig injected = (ComponentTakingConfig) instance.injectedComponent;
        assertThat(injected.config.stringVal(), is("test-value"));
    }

    @Test(expected = RuntimeException.class)
    public void require_error_when_multiple_components_match_a_singleton_dependency() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(SimpleDerivedComponent.class));
        componentGraph.add(mockComponentNode(SimpleComponent.class));
        componentGraph.add(mockComponentNode(ComponentTakingComponent.class));
        componentGraph.complete();
    }

    @Test
    public void named_component_can_be_injected() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(SimpleComponent.class));
        componentGraph.add(mockComponentNode(SimpleComponent.class, Names.named("named-test")));
        componentGraph.add(mockComponentNode(ComponentTakingNamedComponent.class));
        componentGraph.complete();
    }

    @Test
    public void config_keys_can_be_retrieved() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(ComponentTakingConfig.class, "raw:stringVal \"component1\""));
        componentGraph.add(mockComponentNode(ComponentTakingConfig.class, "raw:stringVal \"component2\""));
        componentGraph.add(new ComponentRegistryNode(ComponentTakingConfig.class));
        componentGraph.complete();

        Set<ConfigKey<? extends ConfigInstance>> configKeys = componentGraph.configKeys();
        assertThat(configKeys.size(), is(2));

        configKeys.forEach(key -> {
            assertThat(key.getConfigClass(), equalTo(TestConfig.class));
            assertThat(key.getConfigId(), containsString("component"));
        });
    }

    @Test
    public void providers_can_be_instantiated() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(ExecutorProvider.class));
        componentGraph.complete();

        assertNotNull(componentGraph.getInstance(Executor.class));
    }

    @Test
    public void providers_can_be_inherited() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(DerivedExecutorProvider.class));
        componentGraph.complete();

        assertNotNull(componentGraph.getInstance(Executor.class));
    }

    @Test
    public void providers_can_deliver_a_new_instance_for_each_component() {
        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNode(NewIntProvider.class));
        componentGraph.complete();

        Integer instance1 = componentGraph.getInstance(Integer.class);
        Integer instance2 = componentGraph.getInstance(Integer.class);
        assertThat(instance1, not(equalTo(instance2)));
    }

    @Test
    public void providers_can_be_injected_explicitly() {
        ComponentGraph componentGraph = new ComponentGraph();

        Node componentTakingExecutor = mockComponentNode(ComponentTakingExecutor.class);
        Node executorProvider = mockComponentNode(ExecutorProvider.class);
        componentTakingExecutor.inject(executorProvider);

        componentGraph.add(executorProvider);
        componentGraph.add(mockComponentNode(ExecutorProvider.class));

        componentGraph.add(componentTakingExecutor);

        componentGraph.complete();
        assertNotNull(componentGraph.getInstance(ComponentTakingExecutor.class));
    }

    @Test
    public void global_providers_can_be_injected() {
        ComponentGraph componentGraph = new ComponentGraph();

        componentGraph.add(mockComponentNode(ComponentTakingExecutor.class));
        componentGraph.add(mockComponentNode(ExecutorProvider.class));
        componentGraph.add(mockComponentNode(IntProvider.class));
        componentGraph.complete();

        assertNotNull(componentGraph.getInstance(ComponentTakingExecutor.class));
    }

    @Test(expected = RuntimeException.class)
    public void throw_if_multiple_global_providers_exist() {
        ComponentGraph componentGraph = new ComponentGraph();

        componentGraph.add(mockComponentNode(ExecutorProvider.class));
        componentGraph.add(mockComponentNode(ExecutorProvider.class));
        componentGraph.add(mockComponentNode(ComponentTakingExecutor.class));
        componentGraph.complete();
    }

    @Test
    public void provider_is_not_used_when_component_of_provided_class_exists() {
        ComponentGraph componentGraph = new ComponentGraph();

        componentGraph.add(mockComponentNode(SimpleComponent.class));
        componentGraph.add(mockComponentNode(SimpleComponentProviderThatThrows.class));
        componentGraph.add(mockComponentNode(ComponentTakingComponent.class));
        componentGraph.complete();

        SimpleComponent injectedComponent = componentGraph.getInstance(ComponentTakingComponent.class).injectedComponent;
        assertNotNull(injectedComponent);
    }

    //TODO: move
    @Test
    public void check_if_annotation_is_a_binding_annotation() {
        assertTrue(isBindingAnnotation(Names.named("name")));
        assertFalse(isBindingAnnotation(Named.class.getAnnotations()[0]));
    }

    @Test
    public void cycles_gives_exception() {
        ComponentGraph componentGraph = new ComponentGraph();

        Node node1 = mockComponentNode(ComponentCausingCycle.class);
        Node node2 = mockComponentNode(ComponentCausingCycle.class);

        node1.inject(node2);
        node2.inject(node1);

        componentGraph.add(node1);
        componentGraph.add(node2);

        try {
            componentGraph.complete();
            fail("Cycle exception expected.");
        } catch (Throwable e) {
            assertThat(e.getMessage(), containsString("cycle"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void abstract_classes_are_rejected() {
        new ComponentNode(ComponentId.fromString("Test"), "", AbstractClass.class);
    }

    @Test
    public void inject_constructor_is_preferred() {
        assertThatComponentCanBeCreated(ComponentWithInjectConstructor.class);
    }

    @Test
    public void constructor_with_most_parameters_is_preferred() {
        assertThatComponentCanBeCreated(ComponentWithMultipleConstructors.class);
    }

    public void assertThatComponentCanBeCreated(Class<?> clazz) {
        ComponentGraph componentGraph = new ComponentGraph();
        String configId = "raw:stringVal \"dummy\"";

        componentGraph.add(mockComponentNode(clazz, configId));
        componentGraph.complete();

        componentGraph.setAvailableConfigs(ConfigMap.newMap(TestConfig.class, configId).add(Test2Config.class, configId));

        assertNotNull(componentGraph.getInstance(clazz));
    }

    @Test
    public void require_fallback_to_child_injector() {
        ComponentGraph componentGraph = new ComponentGraph();

        componentGraph.add(mockComponentNode(ComponentTakingExecutor.class));

        componentGraph.complete(singletonExecutorInjector);
        assertNotNull(componentGraph.getInstance(ComponentTakingExecutor.class));
    }

    @Test
    public void child_injector_can_inject_multiple_instances_for_same_key() {
        Pair<Integer, Pair<Executor, Executor>> graph = buildGraphWithChildInjector(Executors::newSingleThreadExecutor);
        int graphSize = graph.getFirst();
        Executor executorA = graph.getSecond().getFirst();
        Executor executorB = graph.getSecond().getSecond();

        assertThat(graphSize, is(4));
        assertThat(executorA, not(sameInstance(executorB)));
    }

    @Test
    public void components_injected_via_child_injector_can_be_shared() {
        Executor commonExecutor = Executors.newSingleThreadExecutor();
        Pair<Integer, Pair<Executor, Executor>> graph = buildGraphWithChildInjector(() -> commonExecutor);
        int graphSize = graph.getFirst();
        Executor executorA = graph.getSecond().getFirst();
        Executor executorB = graph.getSecond().getSecond();

        assertThat(graphSize, is(3));
        assertThat(executorA, sameInstance(executorB));
    }

    private Pair<Integer, Pair<Executor, Executor>> buildGraphWithChildInjector(Supplier<Executor> executorProvider) {
        Injector childInjector = Guice.createInjector(new AbstractModule() {
            @Override
            public void configure() {
                bind(Executor.class).toProvider(executorProvider::get);
            }
        });

        ComponentGraph componentGraph = new ComponentGraph();

        Key<ComponentTakingExecutor> keyA = Key.get(ComponentTakingExecutor.class, Names.named("A"));
        Key<ComponentTakingExecutor> keyB = Key.get(ComponentTakingExecutor.class, Names.named("B"));

        componentGraph.add(mockComponentNode(keyA));
        componentGraph.add(mockComponentNode(keyB));

        componentGraph.complete(childInjector);

        return new Pair<>(componentGraph.size(),
                new Pair<>(componentGraph.getInstance(keyA).executor, componentGraph.getInstance(keyB).executor));
    }

    @Test
    public void providers_can_be_reused() {

        ComponentGraph oldGraph = createReusingGraph();
        Executor executor = oldGraph.getInstance(Executor.class);

        ComponentGraph newGraph = createReusingGraph();
        newGraph.reuseNodes(oldGraph);

        Executor newExecutor = newGraph.getInstance(Executor.class);
        assertThat(executor, sameInstance(newExecutor));
    }

    private ComponentGraph createReusingGraph() {
        ComponentGraph graph = new ComponentGraph();
        graph.add(mockComponentNodeWithId(ExecutorProvider.class, "dummyId"));
        graph.complete();
        graph.setAvailableConfigs(Collections.emptyMap());
        return graph;
    }

    @Test
    public void component_id_can_be_injected() {
        String componentId = "myId:1.2@namespace";

        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(mockComponentNodeWithId(ComponentTakingComponentId.class, componentId));
        componentGraph.complete();

        assertThat(componentGraph.getInstance(ComponentTakingComponentId.class).componentId, is(ComponentId.fromString(componentId)));
    }

    @Test
    public void rest_api_context_can_be_instantiated() {
        String configId = "raw:\"\"";

        Class<RestApiContext> clazz = RestApiContext.class;
        JerseyNode jerseyNode = new JerseyNode(uniqueComponentId(clazz.getName()), configId, clazz, new Osgi() {
        });

        ComponentGraph componentGraph = new ComponentGraph();
        componentGraph.add(jerseyNode);
        componentGraph.complete();

        componentGraph
                .setAvailableConfigs(ConfigMap.newMap(JerseyBundlesConfig.class, configId).add(JerseyInjectionConfig.class, configId));

        RestApiContext restApiContext = componentGraph.getInstance(clazz);
        assertNotNull(restApiContext);
        assertThat(restApiContext.getBundles().size(), is(0));
    }

    //Note that all Components must be defined in a static context,
    //otherwise their constructor will take the outer class as the first parameter.
    private static int counter = 0;

    public static class SimpleComponent extends AbstractComponent {
    }

    public static class SimpleComponent2 extends AbstractComponent {
    }

    public static class SimpleDerivedComponent extends SimpleComponent {
    }

    public static class ComponentTakingConfig extends SimpleComponent {
        private final TestConfig config;

        public ComponentTakingConfig(TestConfig config) {
            assertThat(config, notNullValue());
            this.config = config;
        }
    }

    public static class ComponentTakingComponent extends AbstractComponent {
        private final SimpleComponent injectedComponent;

        public ComponentTakingComponent(SimpleComponent injectedComponent) {
            assertThat(injectedComponent, notNullValue());
            this.injectedComponent = injectedComponent;
        }
    }

    @SuppressWarnings("unused")
    public static class ComponentTakingConfigAndComponent extends AbstractComponent {
        private final TestConfig config;
        private final SimpleComponent simpleComponent;

        public ComponentTakingConfigAndComponent(TestConfig config, SimpleComponent injectedComponent) {
            assertThat(config, notNullValue());
            assertThat(injectedComponent, notNullValue());
            this.config = config;
            this.simpleComponent = injectedComponent;
        }
    }

    public static class ComponentTakingAllSimpleComponents extends AbstractComponent {
        public final ComponentRegistry<SimpleComponent> simpleComponents;

        public ComponentTakingAllSimpleComponents(ComponentRegistry<SimpleComponent> simpleComponents) {
            assertThat(simpleComponents, notNullValue());
            this.simpleComponents = simpleComponents;
        }
    }

    public static class ComponentTakingAllSimpleComponentsUpperBound extends AbstractComponent {
        private final ComponentRegistry<? extends SimpleComponent> simpleComponents;

        public ComponentTakingAllSimpleComponentsUpperBound(ComponentRegistry<? extends SimpleComponent> simpleComponents) {
            assertThat(simpleComponents, notNullValue());
            this.simpleComponents = simpleComponents;
        }
    }

    public static class ComponentTakingAllComponentsWithTypeVariable<COMPONENT extends AbstractComponent> extends AbstractComponent {
        public ComponentTakingAllComponentsWithTypeVariable(ComponentRegistry<COMPONENT> simpleComponents) {
            assertThat(simpleComponents, notNullValue());
        }
    }

    public static class ComponentTakingNamedComponent extends AbstractComponent {
        public ComponentTakingNamedComponent(@Named("named-test") SimpleComponent injectedComponent) {
            assertThat(injectedComponent, notNullValue());
        }
    }

    public static class ComponentCausingCycle extends AbstractComponent {
        public ComponentCausingCycle(ComponentCausingCycle component) {
        }
    }

    public static class SimpleComponentProviderThatThrows implements Provider<SimpleComponent> {
        public SimpleComponent get() {
            throw new AssertionError("Should never be called.");
        }

        public void deconstruct() {
        }
    }

    public static class ExecutorProvider implements Provider<Executor> {
        private Executor executor = Executors.newSingleThreadExecutor();

        public Executor get() {
            return executor;
        }

        public void deconstruct() {
            /*TODO */ }
    }

    public static class DerivedExecutorProvider extends ExecutorProvider {
    }

    public static class IntProvider implements Provider<Integer> {
        public Integer get() {
            throw new AssertionError("Should never be called.");
        }

        public void deconstruct() {
        }
    }

    public static class NewIntProvider implements Provider<Integer> {
        int i = 0;

        public Integer get() {
            i++;
            return i;
        }

        public void deconstruct() {
        }
    }

    public static class ComponentTakingExecutor extends AbstractComponent {
        private final Executor executor;

        public ComponentTakingExecutor(Executor executor) {
            assertThat(executor, notNullValue());
            this.executor = executor;
        }
    }

    public static class ComponentWithInjectConstructor {
        public ComponentWithInjectConstructor(TestConfig c, Test2Config c2) {
            throw new RuntimeException("Should not be called");
        }

        @Inject
        public ComponentWithInjectConstructor(Test2Config c) {
        }
    }

    public static class ComponentWithMultipleConstructors {
        private ComponentWithMultipleConstructors(int dummy) {
        }

        public ComponentWithMultipleConstructors() {
            this(0);
            throw new RuntimeException("Should not be called");
        }

        public ComponentWithMultipleConstructors(TestConfig c, Test2Config c2) {
            this(0);
        }

        public ComponentWithMultipleConstructors(Test2Config c) {
            this();
        }
    }

    public static class ComponentTakingComponentId {
        private final ComponentId componentId;

        public ComponentTakingComponentId(ComponentId componentId) {
            this.componentId = componentId;
        }
    }

    public static ComponentId uniqueComponentId(String className) {
        counter += 1;
        return ComponentId.fromString(className + counter);
    }

    public static Node mockComponentNode(Key<?> key) {
        return mockComponentNode(key.getTypeLiteral().getRawType(), "", key.getAnnotation());
    }

    public static Node mockComponentNode(Class<?> clazz, String configId, Annotation key) {
        return new ComponentNode(uniqueComponentId(clazz.getName()), configId, clazz, key);
    }

    public static Node mockComponentNode(Class<?> clazz, String configId) {
        return new ComponentNode(uniqueComponentId(clazz.getName()), configId, clazz, null);
    }

    public static Node mockComponentNode(Class<?> clazz, Annotation key) {
        return new ComponentNode(uniqueComponentId(clazz.getName()), "", clazz, key);
    }

    public static Node mockComponentNode(Class<?> clazz) {
        return new ComponentNode(uniqueComponentId(clazz.getName()), "", clazz, null);
    }

    public static Node mockComponentNodeWithId(Class<?> clazz, String componentId, String configId /*= ""*/, Annotation key /*= null*/) {
        return new ComponentNode(ComponentId.fromString(componentId), configId, clazz, key);
    }

    public static Node mockComponentNodeWithId(Class<?> clazz, String componentId, String configId /*= ""*/) {
        return new ComponentNode(ComponentId.fromString(componentId), configId, clazz, null);
    }

    public static Node mockComponentNodeWithId(Class<?> clazz, String componentId) {
        return new ComponentNode(ComponentId.fromString(componentId), "", clazz, null);
    }

    public static Injector singletonExecutorInjector = Guice.createInjector(new AbstractModule() {
        @Override
        public void configure() {
            bind(Executor.class).toInstance(Executors.newSingleThreadExecutor());
        }
    });

    public static abstract class AbstractClass {
    }
}
