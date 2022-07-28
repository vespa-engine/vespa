// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
@SuppressWarnings("unused")
public class FallbackToGuiceInjectorTest {

    private ComponentGraph componentGraph;
    private Injector injector;
    private final Map<ConfigKey<? extends ConfigInstance>, ConfigInstance> configs = new HashMap<>();


    @BeforeEach
    public void createGraph() {
        injector = Guice.createInjector();
        componentGraph = new ComponentGraph(0);
    }

    public static class MyComponent extends AbstractComponent {
        private final String url;
        private final Executor executor;

        @Inject
        public MyComponent(@Named("url") String url, Executor executor) {
            this.url = url;
            this.executor = executor;
        }

        public MyComponent() {
            throw new RuntimeException("Constructor annotated with @Inject is preferred.");
        }
    }

    public static class ComponentTakingDefaultString{
        private final String injectedString;

        public ComponentTakingDefaultString(String empty_string_created_by_guice) {
            this.injectedString = empty_string_created_by_guice;
        }
    }

    public static class ComponentThatCannotBeConstructed {
        public ComponentThatCannotBeConstructed(Integer cannot_be_injected_because_Integer_has_no_default_ctor) { }
    }

    @Test
    void guice_injector_is_used_when_no_global_component_exists() {
        setInjector(
                Guice.createInjector(new AbstractModule() {
                    @Override
                    protected void configure() {
                        bind(Executor.class).toInstance(Executors.newSingleThreadExecutor());
                        bind(String.class).annotatedWith(Names.named("url")).toInstance("http://yahoo.com");
                    }
                }));

        register(MyComponent.class);
        complete();

        MyComponent component = getInstance(MyComponent.class);
        assertEquals("http://yahoo.com", component.url);
        assertNotNull(component.executor);
    }

    @Test
    void guice_injector_creates_a_new_instance_with_default_ctor_when_no_explicit_binding_exists() {
        setInjector(emptyGuiceInjector());
        register(ComponentTakingDefaultString.class);
        complete();

        ComponentTakingDefaultString component = getInstance(ComponentTakingDefaultString.class);
        assertTrue(component.injectedString.isEmpty());
    }

    @Test
    void guice_injector_fails_when_no_explicit_binding_exists_and_class_has_no_default_ctor() {
        setInjector(emptyGuiceInjector());
        register(ComponentThatCannotBeConstructed.class);
        try {
            complete();
            fail();
        } catch (RuntimeException e) {
            assertEquals("When resolving dependencies of 'com.yahoo.container.di.componentgraph.core.FallbackToGuiceInjectorTest$ComponentThatCannotBeConstructed'", e.getMessage());
        }
    }

    public void register(Class<?> componentClass) {
        componentGraph.add(mockComponentNode(componentClass));
    }

    public ComponentId toId(Class<?> componentClass) {
        return ComponentId.fromString(componentClass.getName());
    }

    private Node mockComponentNode(Class<?> componentClass) {
        return new ComponentNode(toId(componentClass), toId(componentClass).toString(), componentClass, null);
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

    private Injector emptyGuiceInjector() {
        return Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
            }
        });
    }
}
