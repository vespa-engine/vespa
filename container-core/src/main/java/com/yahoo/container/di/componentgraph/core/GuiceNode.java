// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.core;

import com.google.inject.Key;
import com.yahoo.component.ComponentId;
import com.yahoo.config.ConfigInstance;
import com.yahoo.vespa.config.ConfigKey;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.yahoo.container.di.componentgraph.core.Keys.createKey;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 * @author ollivir
 */
public final class GuiceNode extends Node {
    private static final ComponentId guiceNamespace = ComponentId.fromString("Guice");

    private final Object myInstance;
    private final Annotation annotation;

    public GuiceNode(Object myInstance,
                     Annotation annotation) {
        super(componentId(myInstance));
        this.myInstance = myInstance;
        this.annotation = annotation;
    }

    @Override
    public Set<ConfigKey<ConfigInstance>> configKeys() {
        return Collections.emptySet();
    }

    @Override
    public Key<?> instanceKey() {
        return createKey(myInstance.getClass(), annotation);
    }

    @Override
    public Class<?> instanceType() {
        return myInstance.getClass();
    }

    @Override
    public Class<?> componentType() {
        return instanceType();
    }


    @Override
    public List<Node> usedComponents() {
        return Collections.emptyList();
    }

    @Override
    protected Object newInstance() {
        return myInstance;
    }

    @Override
    public void inject(Node component) {
        throw new UnsupportedOperationException("Illegal to inject components to a GuiceNode!");
    }

    @Override
    public String label() {
        return String.format("{{%s|Guice}|%s}", instanceType().getSimpleName(), Node.packageName(instanceType()));
    }

    private static ComponentId componentId(Object instance) {
        return Node.syntheticComponentId(instance.getClass().getName(), instance, guiceNamespace);
    }
}
