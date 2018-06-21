// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.google.inject.Key;
import com.yahoo.component.ComponentId;
import com.yahoo.config.ConfigInstance;
import com.yahoo.container.di.componentgraph.Provider;
import com.yahoo.vespa.config.ConfigKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

import static com.yahoo.log.LogLevel.DEBUG;
import static com.yahoo.log.LogLevel.SPAM;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public abstract class Node {
    private final static Logger log = Logger.getLogger(Node.class.getName());

    private final ComponentId componentId;
    protected Optional<Object> instance = Optional.empty();
    List<Node> componentsToInject = new ArrayList<>();

    public Node(ComponentId componentId) {
        this.componentId = componentId;
    }

    public abstract Key<?> instanceKey();

    /**
     * The components actually used by this node. Consist of a subset of the injected nodes + subset of the global nodes.
     */
    public abstract List<Node> usedComponents();

    protected abstract Object newInstance();

    public Object newOrCachedInstance() {
        Object inst;
        if (instance.isPresent()) {
            inst = instance.get();
            log.log(SPAM, "Reusing instance for component with ID " + componentId);
        } else {
            log.log(DEBUG, "Creating new instance for component with ID " + componentId);
            inst = newInstance();
            instance = Optional.of(inst);
        }
        return component(inst);
    }

    private Object component(Object instance) {
        if (instance instanceof Provider) {
            Provider<?> provider = (Provider<?>) instance;
            return provider.get();
        } else {
            return instance;
        }
    }

    public abstract Set<ConfigKey<ConfigInstance>> configKeys();

    public void inject(Node component) {
        componentsToInject.add(component);
    }

    public void injectAll(Collection<ComponentNode> componentNodes) {
        componentNodes.forEach(this::inject);
    }

    public abstract Class<?> instanceType();

    public abstract Class<?> componentType();

    public abstract String label();

    public String idAndType() {
        String className = instanceType().getName();

        if (className.equals(componentId.getName())) {
            return "'" + componentId + "'";
        } else {
            return "'" + componentId + "' of type '" + className + "'";
        }
    }

    private static boolean equalNodes(Object a, Object b) {
        if (a instanceof Node && b instanceof Node) {
            Node l = (Node) a;
            Node r = (Node) b;
            return l.componentId.equals(r.componentId);
        } else {
            return a.equals(b);
        }
    }

    public static boolean equalEdges(List<?> edges1, List<?> edges2) {
        Iterator<?> right = edges2.iterator();
        for (Object l : edges1) {
            if (!right.hasNext()) {
                return false;
            }
            Object r = right.next();
            if (!equalNodes(l, r)) {
                return false;
            }
        }
        return !right.hasNext();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((componentId == null) ? 0 : componentId.hashCode());
        result = prime * result + ((componentsToInject == null) ? 0 : componentsToInject.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof Node) {
            Node that = (Node) other;
            return getClass().equals(that.getClass()) && this.componentId.equals(that.componentId)
                    && this.instanceType().equals(that.instanceType()) && equalEdges(this.usedComponents(), that.usedComponents());
        } else {
            return false;
        }
    }

    public ComponentId componentId() {
        return componentId;
    }

    public Optional<?> instance() {
        return instance;
    }

    /**
     * @param identityObject
     *            The identifying object that makes the Node unique
     */
    protected static ComponentId syntheticComponentId(String className, Object identityObject, ComponentId namespace) {
        String name = className + "_" + System.identityHashCode(identityObject);
        return ComponentId.fromString(name).nestInNamespace(namespace);
    }

    public static String packageName(Class<?> componentClass) {
        String fullClassName = componentClass.getName();
        int index = fullClassName.lastIndexOf('.');
        if (index < 0) {
            return "";
        } else {
            return fullClassName.substring(0, index);
        }
    }
}
