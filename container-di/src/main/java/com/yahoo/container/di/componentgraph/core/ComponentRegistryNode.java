// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.google.inject.Key;
import com.google.inject.util.Types;
import com.yahoo.component.ComponentId;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public class ComponentRegistryNode extends Node {
    private static ComponentId componentRegistryNamespace = ComponentId.fromString("ComponentRegistry");

    private final Class<?> componentClass;

    public ComponentRegistryNode(Class<?> componentClass) {
        super(componentId(componentClass));
        this.componentClass = componentClass;
    }

    @Override
    public List<Node> usedComponents() {
        return componentsToInject;
    }

    @Override
    protected Object newInstance() {
        ComponentRegistry<Object> registry = new ComponentRegistry<>();
        componentsToInject.forEach(component -> registry.register(component.componentId(), component.newOrCachedInstance()));

        return registry;
    }

    @Override
    public Key<?> instanceKey() {
        return Key.get(Types.newParameterizedType(ComponentRegistry.class, componentClass));
    }

    @Override
    public Class<?> instanceType() {
        return instanceKey().getTypeLiteral().getRawType();
    }

    @Override
    public Class<?> componentType() {
        return instanceType();
    }

    public Class<?> componentClass() {
        return componentClass;
    }

    @Override
    public Set<ConfigKey<ConfigInstance>> configKeys() {
        return Collections.emptySet();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((componentClass == null) ? 0 : componentClass.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof ComponentRegistryNode) {
            ComponentRegistryNode that = (ComponentRegistryNode) other;
            return this.componentId().equals(that.componentId()) && this.instanceType().equals(that.instanceType())
                    && equalNodeEdges(this.usedComponents(), that.usedComponents());
        } else {
            return false;
        }
    }

    @Override
    public String label() {
        return String.format("{ComponentRegistry\\<%s\\>|%s}", componentClass.getSimpleName(), Node.packageName(componentClass));
    }

    private static ComponentId componentId(Class<?> componentClass) {
        return syntheticComponentId(componentClass.getName(), componentClass, componentRegistryNamespace);
    }

    public static boolean equalNodeEdges(List<Node> edges, List<Node> otherEdges) {
        if (edges.size() == otherEdges.size()) {
            List<ComponentId> left = edges.stream().map(Node::componentId).sorted().collect(Collectors.toList());
            List<ComponentId> right = otherEdges.stream().map(Node::componentId).sorted().collect(Collectors.toList());
            return left.equals(right);
        } else {
            return false;
        }
    }
}
