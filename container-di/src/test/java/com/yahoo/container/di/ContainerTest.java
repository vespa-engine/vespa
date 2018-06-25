// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di;

import com.yahoo.component.AbstractComponent;
import com.yahoo.config.di.IntConfig;
import com.yahoo.config.test.TestConfig;
import com.yahoo.container.bundle.MockBundle;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.container.di.componentgraph.core.ComponentGraph;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.SimpleComponent;
import com.yahoo.container.di.componentgraph.core.ComponentGraphTest.SimpleComponent2;
import com.yahoo.container.di.componentgraph.core.ComponentNode.ComponentConstructorException;
import com.yahoo.container.di.componentgraph.core.Node;
import com.yahoo.container.di.config.RestApiContext;
import org.junit.Ignore;
import org.junit.Test;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
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

        ComponentTakingConfig component = createComponentTakingConfig(container.getNewComponentGraph());
        assertThat(component.config.stringVal(), is("myString"));

        container.shutdownConfigurer();
    }

    @Test
    public void components_are_reconfigured_after_config_update_without_bootstrap_configs() {
        writeBootstrapConfigs();
        dirConfigSource.writeConfig("test", "stringVal \"original\"");

        Container container = newContainer(dirConfigSource);

        ComponentGraph componentGraph = container.getNewComponentGraph();
        ComponentTakingConfig component = createComponentTakingConfig(componentGraph);

        assertThat(component.config.stringVal(), is("original"));

        // Reconfigure
        dirConfigSource.writeConfig("test", "stringVal \"reconfigured\"");
        container.reloadConfig(2);

        ComponentGraph newComponentGraph = container.getNewComponentGraph(componentGraph);
        ComponentTakingConfig component2 = createComponentTakingConfig(newComponentGraph);
        assertThat(component2.config.stringVal(), is("reconfigured"));

        container.shutdownConfigurer();
    }

    @Test
    public void graph_is_updated_after_bootstrap_update() {
        dirConfigSource.writeConfig("test", "stringVal \"original\"");
        writeBootstrapConfigs("id1");

        Container container = newContainer(dirConfigSource);

        ComponentGraph graph = container.getNewComponentGraph();
        ComponentTakingConfig component = createComponentTakingConfig(graph);
        assertThat(component.getId().toString(), is("id1"));

        writeBootstrapConfigs(
                new ComponentEntry("id1", ComponentTakingConfig.class),
                new ComponentEntry("id2", ComponentTakingConfig.class));

        container.reloadConfig(2);
        ComponentGraph newGraph = container.getNewComponentGraph(graph);

        assertThat(ComponentGraph.getNode(newGraph, "id1"), notNullValue(Node.class));
        assertThat(ComponentGraph.getNode(newGraph, "id2"), notNullValue(Node.class));

        container.shutdownConfigurer();
    }

    //@Test TODO
    public void deconstructor_is_given_guice_components() {
    }

    @Test
    public void osgi_component_is_deconstructed_when_not_reused() {
        writeBootstrapConfigs("id1", DestructableComponent.class);

        Container container = newContainer(dirConfigSource);

        ComponentGraph oldGraph = container.getNewComponentGraph();
        DestructableComponent componentToDestruct = oldGraph.getInstance(DestructableComponent.class);

        writeBootstrapConfigs("id2", DestructableComponent.class);
        container.reloadConfig(2);
        container.getNewComponentGraph(oldGraph);
        assertTrue(componentToDestruct.deconstructed);
    }

    @Ignore  // because logAndDie is impossible(?) to verify programmatically
    @Test
    public void manually_verify_what_happens_when_first_graph_contains_component_that_throws_exception_in_ctor() {
        writeBootstrapConfigs("thrower", ComponentThrowingExceptionInConstructor.class);
        Container container = newContainer(dirConfigSource);
        try {
            container.getNewComponentGraph();
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
        ComponentGraph currentGraph = container.getNewComponentGraph();

        SimpleComponent simpleComponent = currentGraph.getInstance(SimpleComponent.class);

        writeBootstrapConfigs("thrower", ComponentThrowingExceptionInConstructor.class);
        container.reloadConfig(2);
        try {
            currentGraph = container.getNewComponentGraph(currentGraph);
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
        currentGraph = container.getNewComponentGraph(currentGraph);

        assertEquals(3, currentGraph.generation());
        assertSame(simpleComponent, currentGraph.getInstance(SimpleComponent.class));
        assertNotNull(currentGraph.getInstance(ComponentTakingConfig.class));
    }

    @Test
    public void previous_graph_is_retained_when_new_graph_throws_exception_for_missing_config() {
        ComponentEntry simpleComponentEntry = new ComponentEntry("simpleComponent", SimpleComponent.class);

        writeBootstrapConfigs(simpleComponentEntry);
        Container container = newContainer(dirConfigSource);
        ComponentGraph currentGraph = container.getNewComponentGraph();

        currentGraph.getInstance(SimpleComponent.class);

        writeBootstrapConfigs("thrower", ComponentThrowingExceptionForMissingConfig.class);
        dirConfigSource.writeConfig("test", "stringVal \"myString\"");
        container.reloadConfig(2);
        try {
            currentGraph = container.getNewComponentGraph(currentGraph);
            fail("Expected exception");
        } catch (IllegalArgumentException ignored) {
            // Expected, do nothing
        } catch (Throwable t) {
            fail("Expected IllegalArgumentException");
        }
        assertEquals(1, currentGraph.generation());
    }

    @Test
    public void runOnce_hangs_waiting_for_valid_config_after_invalid_config() throws InterruptedException, ExecutionException, TimeoutException {
        dirConfigSource.writeConfig("test", "stringVal \"original\"");
        writeBootstrapConfigs("myId", ComponentTakingConfig.class);

        Container container = newContainer(dirConfigSource);
        final ComponentGraph currentGraph = container.getNewComponentGraph();

        writeBootstrapConfigs("thrower", ComponentThrowingExceptionForMissingConfig.class);
        container.reloadConfig(2);

        try {
            container.getNewComponentGraph(currentGraph);
            fail("expected exception");
        } catch (Exception ignored) {
        }
        ExecutorService exec = Executors.newFixedThreadPool(1);
        Future<ComponentGraph> newGraph = exec.submit(() -> container.getNewComponentGraph(currentGraph));

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
        ComponentGraph componentGraph = container.getNewComponentGraph();

        RestApiContext restApiContext = componentGraph.getInstance(clazz);
        assertNotNull(restApiContext);

        assertThat(restApiContext.getBundles().size(), is(1));
        assertThat(restApiContext.getBundles().get(0).symbolicName, is(MockBundle.SymbolicName));
        assertThat(restApiContext.getBundles().get(0).version, is(MockBundle.BundleVersion));

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
        dirConfigSource.writeConfig("bundles", "");
        dirConfigSource.writeConfig("jersey-bundles", "bundles[0].spec \"mock-entry-to-enforce-a-MockBundle\"");
        dirConfigSource.writeConfig("jersey-injection", injectionConfig);

        Container container = newContainer(dirConfigSource);
        ComponentGraph componentGraph = container.getNewComponentGraph();

        RestApiContext restApiContext = componentGraph.getInstance(restApiClass);

        assertFalse(restApiContext.getInjectableComponents().isEmpty());
        assertThat(restApiContext.getInjectableComponents().size(), is(2));

        container.shutdownConfigurer();
    }

    @Test
    public void providers_are_destructed() {
        writeBootstrapConfigs("id1", DestructableProvider.class);

        ComponentDeconstructor deconstructor = new ComponentDeconstructor() {
            @Override
            public void deconstruct(Object component) {
                if (component instanceof AbstractComponent) {
                    ((AbstractComponent) component).deconstruct();
                    ;
                } else if (component instanceof Provider) {
                    ((Provider<?>) component).deconstruct();
                }
            }
        };

        Container container = newContainer(dirConfigSource, deconstructor);

        ComponentGraph oldGraph = container.getNewComponentGraph();
        DestructableEntity destructableEntity = oldGraph.getInstance(DestructableEntity.class);

        writeBootstrapConfigs("id2", DestructableProvider.class);
        container.reloadConfig(2);
        container.getNewComponentGraph(oldGraph);

        assertTrue(destructableEntity.deconstructed);
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
        public void deconstruct(Object component) {
            if (component instanceof DestructableComponent) {
                DestructableComponent vespaComponent = (DestructableComponent) component;
                vespaComponent.deconstruct();
            }
        }
    }

    private static Container newContainer(DirConfigSource dirConfigSource,
                                          ComponentDeconstructor deconstructor) {
        return new Container(new CloudSubscriberFactory(dirConfigSource.configSource), dirConfigSource.configId(), deconstructor);
    }

    private static Container newContainer(DirConfigSource dirConfigSource) {
        return newContainer(dirConfigSource, new TestDeconstructor());
    }

    private ComponentTakingConfig createComponentTakingConfig(ComponentGraph componentGraph) {
        return componentGraph.getInstance(ComponentTakingConfig.class);
    }
}
