// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.google.inject.Guice;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.di.IntConfig;
import com.yahoo.config.test.TestConfig;
import com.yahoo.container.bundle.MockBundle;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.di.componentgraph.core.ComponentGraph;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.SimpleComponent;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.SimpleComponent2;
import com.yahoo.container.di.componentgraph.core.ComponentNode.ComponentConstructorException;
import com.yahoo.container.di.config.RestApiContext;
import org.junit.Ignore;
import org.junit.Test;
import org.osgi.framework.Bundle;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class ContainerTest extends ContainerTestBase {

    @Test
    public void components_can_be_created_from_config() {
        writeBootstrapConfigs();
        dirConfigSource.writeConfig("test", "stringVal \"myString\"");

        Container container = newContainer(dirConfigSource);

        ComponentTakingConfig component = createComponentTakingConfig(getNewComponentGraph(container));
        assertEquals("myString", component.config.stringVal());

        container.shutdownConfigurer();
    }

    @Test
    public void components_are_reconfigured_after_config_update_without_bootstrap_configs() {
        writeBootstrapConfigs();
        dirConfigSource.writeConfig("test", "stringVal \"original\"");

        Container container = newContainer(dirConfigSource);
        ComponentGraph componentGraph = getNewComponentGraph(container);
        ComponentTakingConfig component = createComponentTakingConfig(componentGraph);

        assertEquals("original", component.config.stringVal());

        // Reconfigure
        dirConfigSource.writeConfig("test", "stringVal \"reconfigured\"");
        container.reloadConfig(2);

        ComponentGraph newComponentGraph = getNewComponentGraph(container, componentGraph);
        ComponentTakingConfig component2 = createComponentTakingConfig(newComponentGraph);
        assertEquals("reconfigured", component2.config.stringVal());

        container.shutdownConfigurer();
    }

    @Test
    public void graph_is_updated_after_bootstrap_update() {
        dirConfigSource.writeConfig("test", "stringVal \"original\"");
        writeBootstrapConfigs("id1");

        Container container = newContainer(dirConfigSource);

        ComponentGraph graph = getNewComponentGraph(container);
        ComponentTakingConfig component = createComponentTakingConfig(graph);
        assertEquals("id1", component.getId().toString());

        writeBootstrapConfigs(
                new ComponentEntry("id1", ComponentTakingConfig.class),
                new ComponentEntry("id2", ComponentTakingConfig.class));

        container.reloadConfig(2);
        ComponentGraph newGraph = getNewComponentGraph(container, graph);

        assertNotNull(ComponentGraph.getNode(newGraph, "id1"));
        assertNotNull(ComponentGraph.getNode(newGraph, "id2"));

        container.shutdownConfigurer();
    }

    //@Test TODO
    public void deconstructor_is_given_guice_components() {
    }

    @Test
    public void component_is_deconstructed_when_not_reused() {
        writeBootstrapConfigs("id1", DestructableComponent.class);

        Container container = newContainer(dirConfigSource);

        ComponentGraph oldGraph = getNewComponentGraph(container);
        DestructableComponent componentToDestruct = oldGraph.getInstance(DestructableComponent.class);

        writeBootstrapConfigs("id2", DestructableComponent.class);
        container.reloadConfig(2);
        getNewComponentGraph(container, oldGraph);
        assertTrue(componentToDestruct.deconstructed);
    }

    @Ignore  // because logAndDie is impossible(?) to verify programmatically
    @Test
    public void manually_verify_what_happens_when_first_graph_contains_component_that_throws_exception_in_ctor() {
        writeBootstrapConfigs("thrower", ComponentThrowingExceptionInConstructor.class);
        Container container = newContainer(dirConfigSource);
        try {
            getNewComponentGraph(container);
            fail("Expected to log and die.");
        } catch (Throwable t) {
            fail("Expected to log and die");
        }
    }

    @Test
    public void previous_graph_is_retained_when_new_graph_contains_component_that_throws_exception_in_ctor() {
        ComponentEntry simpleComponentEntry = new ComponentEntry("simpleComponent", SimpleComponent.class);

        writeBootstrapConfigs(simpleComponentEntry);
        Container container = newContainer(dirConfigSource);
        ComponentGraph currentGraph = getNewComponentGraph(container);

        SimpleComponent simpleComponent = currentGraph.getInstance(SimpleComponent.class);

        writeBootstrapConfigs("thrower", ComponentThrowingExceptionInConstructor.class);
        container.reloadConfig(2);
        try {
            currentGraph = getNewComponentGraph(container, currentGraph);
            fail("Expected exception");
        } catch (ComponentConstructorException ignored) {
            // Expected, do nothing
        } catch (Throwable t) {
            fail("Expected ComponentConstructorException");
        }
        assertEquals(1, currentGraph.generation());

        // Also verify that next reconfig is successful
        ComponentEntry componentTakingConfigEntry = new ComponentEntry("componentTakingConfig", ComponentTakingConfig.class);
        dirConfigSource.writeConfig("test", "stringVal \"myString\"");
        writeBootstrapConfigs(simpleComponentEntry, componentTakingConfigEntry);
        container.reloadConfig(3);
        currentGraph = getNewComponentGraph(container, currentGraph);

        assertEquals(3, currentGraph.generation());
        assertSame(simpleComponent, currentGraph.getInstance(SimpleComponent.class));
        assertNotNull(currentGraph.getInstance(ComponentTakingConfig.class));
    }

    @Test
    public void previous_graph_is_retained_when_new_graph_throws_exception_for_missing_config() {
        ComponentEntry simpleComponentEntry = new ComponentEntry("simpleComponent", SimpleComponent.class);

        writeBootstrapConfigs(simpleComponentEntry);
        Container container = newContainer(dirConfigSource);
        ComponentGraph currentGraph = getNewComponentGraph(container);

        currentGraph.getInstance(SimpleComponent.class);

        writeBootstrapConfigs("thrower", ComponentThrowingExceptionForMissingConfig.class);
        dirConfigSource.writeConfig("test", "stringVal \"myString\"");
        container.reloadConfig(2);
        try {
            currentGraph = getNewComponentGraph(container, currentGraph);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {
            // Expected, do nothing
        } catch (Throwable t) {
            fail("Expected IllegalArgumentException");
        }
        assertEquals(1, currentGraph.generation());
    }

    @Test
    public void getNewComponentGraph_hangs_waiting_for_valid_config_after_invalid_config() throws Exception {
        dirConfigSource.writeConfig("test", "stringVal \"original\"");
        writeBootstrapConfigs("myId", ComponentTakingConfig.class);

        Container container = newContainer(dirConfigSource);
        final ComponentGraph currentGraph = getNewComponentGraph(container);

        writeBootstrapConfigs("thrower", ComponentThrowingExceptionForMissingConfig.class);
        container.reloadConfig(2);

        try {
            getNewComponentGraph(container, currentGraph);
            fail("expected exception");
        } catch (Exception ignored) {
        }
        ExecutorService exec = Executors.newFixedThreadPool(1);
        Future<ComponentGraph> newGraph = exec.submit(() -> getNewComponentGraph(container, currentGraph));

        try {
            newGraph.get(1, TimeUnit.SECONDS);
            fail("Expected waiting for new config.");
        } catch (Exception ignored) {
            // expect to time out
        }

        writeBootstrapConfigs("myId2", ComponentTakingConfig.class);
        container.reloadConfig(3);

        assertNotNull(newGraph.get(5, TimeUnit.MINUTES));
    }


    @Test
    public void bundle_info_is_set_on_rest_api_context() {
        Class<RestApiContext> clazz = RestApiContext.class;

        writeBootstrapConfigs("restApiContext", clazz);
        dirConfigSource.writeConfig("jersey-bundles", "bundles[0].spec \"mock-entry-to-enforce-a-MockBundle\"");
        dirConfigSource.writeConfig("jersey-injection", "inject[0]");

        Container container = newContainer(dirConfigSource);
        ComponentGraph componentGraph = getNewComponentGraph(container);

        RestApiContext restApiContext = componentGraph.getInstance(clazz);
        assertNotNull(restApiContext);

        assertEquals(1, restApiContext.getBundles().size());
        assertEquals(MockBundle.SymbolicName, restApiContext.getBundles().get(0).symbolicName);
        assertEquals(MockBundle.BundleVersion, restApiContext.getBundles().get(0).version);

        container.shutdownConfigurer();
    }

    @Test
    public void restApiContext_has_all_components_injected() {
        Class<RestApiContext> restApiClass = RestApiContext.class;
        Class<SimpleComponent> injectedClass = SimpleComponent.class;
        String injectedComponentId = "injectedComponent";
        Class<SimpleComponent2> anotherComponentClass = SimpleComponent2.class;
        String anotherComponentId = "anotherComponent";

        String componentsConfig =
                new ComponentEntry(injectedComponentId, injectedClass).asConfig(0) + "\n" +
                        new ComponentEntry(anotherComponentId, anotherComponentClass).asConfig(1) + "\n" +
                        new ComponentEntry("restApiContext", restApiClass).asConfig(2) + "\n" +
                        "components[2].inject[0].id " + injectedComponentId + "\n" +
                        "components[2].inject[1].id " + anotherComponentId + "\n";

        String injectionConfig = "inject[1]\n" +//
                "inject[0].instance " + injectedComponentId + "\n" +//
                "inject[0].forClass \"" + injectedClass.getName() + "\"\n";

        dirConfigSource.writeConfig("components", componentsConfig);
        dirConfigSource.writeConfig("platform-bundles", "");
        dirConfigSource.writeConfig("application-bundles", "");
        dirConfigSource.writeConfig("jersey-bundles", "bundles[0].spec \"mock-entry-to-enforce-a-MockBundle\"");
        dirConfigSource.writeConfig("jersey-injection", injectionConfig);

        Container container = newContainer(dirConfigSource);
        ComponentGraph componentGraph = getNewComponentGraph(container);

        RestApiContext restApiContext = componentGraph.getInstance(restApiClass);

        assertFalse(restApiContext.getInjectableComponents().isEmpty());
        assertEquals(2, restApiContext.getInjectableComponents().size());

        container.shutdownConfigurer();
    }

    @Test
    public void providers_are_destructed() {
        writeBootstrapConfigs("id1", DestructableProvider.class);

        ComponentDeconstructor deconstructor = (components, bundles) -> {
            components.forEach(component -> {
                if (component instanceof AbstractComponent) {
                    ((AbstractComponent) component).deconstruct();
                } else if (component instanceof Provider) {
                    ((Provider<?>) component).deconstruct();
                }
            });
            if (! bundles.isEmpty()) throw new IllegalArgumentException("This test should not use bundles");
        };

        Container container = newContainer(dirConfigSource, deconstructor);

        ComponentGraph oldGraph = getNewComponentGraph(container);
        DestructableEntity destructableEntity = oldGraph.getInstance(DestructableEntity.class);

        writeBootstrapConfigs("id2", DestructableProvider.class);
        container.reloadConfig(2);
        getNewComponentGraph(container, oldGraph);

        assertTrue(destructableEntity.deconstructed);
    }

    @Test
    public void providers_are_invoked_only_when_needed() {
        writeBootstrapConfigs("id1", FailOnGetProvider.class);

        Container container = newContainer(dirConfigSource);

        ComponentGraph oldGraph = getNewComponentGraph(container);
    }

    static class DestructableEntity {
        private boolean deconstructed = false;
    }

    public static class DestructableProvider implements Provider<DestructableEntity> {
        DestructableEntity instance = new DestructableEntity();

        public DestructableEntity get() {
            return instance;
        }

        public void deconstruct() {
            assertFalse(instance.deconstructed);
            instance.deconstructed = true;
        }
    }

    public static class FailOnGetProvider implements Provider<Integer> {

        public Integer get() {
            fail("Should never be called.");
            return null;
        }

        public void deconstruct() {
        }

    }

    public static class ComponentTakingConfig extends AbstractComponent {
        private final TestConfig config;

        public ComponentTakingConfig(TestConfig config) {
            assertNotNull(config);
            this.config = config;
        }
    }

    public static class ComponentThrowingExceptionInConstructor {
        public ComponentThrowingExceptionInConstructor() {
            throw new RuntimeException("This component fails upon construction.");
        }
    }

    public static class ComponentThrowingExceptionForMissingConfig extends AbstractComponent {
        public ComponentThrowingExceptionForMissingConfig(IntConfig intConfig) {
            fail("This component should never be created. Only used for tests where 'int' config is missing.");
        }
    }

    public static class DestructableComponent extends AbstractComponent {
        private boolean deconstructed = false;

        @Override
        public void deconstruct() {
            deconstructed = true;
        }
    }

    public static class TestDeconstructor implements ComponentDeconstructor {
        @Override
        public void deconstruct(List<Object> components, Collection<Bundle> bundles) {
            components.forEach(component -> {
                if (component instanceof DestructableComponent) {
                    DestructableComponent vespaComponent = (DestructableComponent) component;
                    vespaComponent.deconstruct();
                }
            });
            if (! bundles.isEmpty()) throw new IllegalArgumentException("This test should not use bundles");
        }
    }

    private static Container newContainer(DirConfigSource dirConfigSource,
                                          ComponentDeconstructor deconstructor) {
        return new Container(new CloudSubscriberFactory(dirConfigSource.configSource), dirConfigSource.configId(), deconstructor);
    }

    private static Container newContainer(DirConfigSource dirConfigSource) {
        return newContainer(dirConfigSource, new TestDeconstructor());
    }

    ComponentGraph getNewComponentGraph(Container container, ComponentGraph oldGraph) {
        return container.getNewComponentGraph(oldGraph, Guice.createInjector(), true);
    }

    ComponentGraph getNewComponentGraph(Container container) {
        return container.getNewComponentGraph(new ComponentGraph(), Guice.createInjector(), true);
    }

    private ComponentTakingConfig createComponentTakingConfig(ComponentGraph componentGraph) {
        return componentGraph.getInstance(ComponentTakingConfig.class);
    }

}
