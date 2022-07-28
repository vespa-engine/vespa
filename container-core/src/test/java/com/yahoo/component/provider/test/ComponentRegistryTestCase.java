// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.provider.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.provider.ComponentRegistry;

/**
 * Tests that ComponentRegistry handles namespaces correctly.
 * 
 * @author Tony Vaagenes
 */
public class ComponentRegistryTestCase {
    private static class TestComponent extends AbstractComponent {
        TestComponent(ComponentId componentId) {
            super(componentId);
        }
    }

    private static final String componentName = "component";

    private static final String namespace1 = "namespace1";
    private static final String namespace2 = "namespace2";
    private static final String namespace21 = "namespace2:1";

    private static final TestComponent component1 = componentInNamespace(namespace1);
    private static final TestComponent component2 = componentInNamespace(namespace2);
    private static final TestComponent component21 = componentInNamespace(namespace21);

    private ComponentRegistry<TestComponent> registry;

    private static ComponentSpecification specInNamespace(String namespace) {
        return new ComponentSpecification(componentName + "@" + namespace);
    }

    private static ComponentId idInNamespace(String namespace) {
        return specInNamespace(namespace).toId();
    }

    private static TestComponent componentInNamespace(String namespace) {
        return new TestComponent(idInNamespace(namespace));
    }

    @BeforeEach
    public void before() {
        registry = new ComponentRegistry<>();

        registry.register(component1.getId(), component1);
        registry.register(component2.getId(), component2);
        registry.register(component21.getId(), component21);
    }

    @Test
    void testAllPresent() {
        assertEquals(3, registry.getComponentCount());
    }

    @Test
    void testIdNamespaceLookup() {
        assertEquals(component1,  registry.getComponent(idInNamespace(namespace1)));
        assertEquals(component2,  registry.getComponent(idInNamespace(namespace2)));
        assertEquals(component21, registry.getComponent(idInNamespace(namespace21)));
    }

    @Test
    void testSpecNamespaceLookup() {
        assertEquals(component1, registry.getComponent(specInNamespace(namespace1)));

        // Version for namespace must match the specification exactly, so do not return version '1' when an
        // empty version is asked for.
        assertEquals(component2, registry.getComponent(specInNamespace(namespace2)));
        assertEquals(component21, registry.getComponent(specInNamespace(namespace21)));
    }

    @Test
    void testInnerComponentNotMixedWithTopLevelComponent() {
        assertNull(registry.getComponent(componentName));

        TestComponent topLevel = new TestComponent(new ComponentId(componentName));
        registry.register(topLevel.getId(), topLevel);
        assertEquals(topLevel, registry.getComponent(componentName));

        assertEquals(component1, registry.getComponent(specInNamespace(namespace1)));
        assertEquals(component1, registry.getComponent(idInNamespace(namespace1)));
    }

}
