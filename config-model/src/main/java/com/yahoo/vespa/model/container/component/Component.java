// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.component;

import com.yahoo.collections.Pair;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.osgi.provider.model.ComponentModel;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * @author gjoranv
 * @author Tony Vaagenes
 */
public class Component<CCHILD extends AnyConfigProducer, MODEL extends ComponentModel>
        extends TreeConfigProducer<CCHILD> implements Comparable<Component<?, ?>> {

    public final MODEL model;
    final Set<Pair<String, Component>> injectedComponents = new LinkedHashSet<>();

    public Component(MODEL model) {
        super(model.getComponentId().stringValue());
        this.model = model;
    }

    /** Returns a component that uses its class name as id. */
    public static Component<?,?> fromClassAndBundle(Class<?> clazz, String bundle) {
        return new Component<>(new ComponentModel(clazz.getName(), null, bundle));
    }

    public ComponentId getGlobalComponentId() {
        return model.getComponentId();
    }

    public ComponentId getComponentId() {
        return model.getComponentId();
    }

    public ComponentSpecification getClassId() {
        return model.getClassId();
    }

    public void inject(Component component) {
        injectForName("", component);
    }

    public void injectForName(String name, Component component) {
        injectedComponents.add(new Pair<>(name, component));
    }

    public void addComponent(CCHILD child) {
        addChild(child);
    }

    /** For testing only */
    public Set<String> getInjectedComponentIds() {
        Set<String> injectedIds = new HashSet<>();
        for (Pair<String, Component> injected : injectedComponents) {
            injectedIds.add(injected.getSecond().getSubId());
        }
        return injectedIds;
    }

    @Override
    public int compareTo(Component<?, ?> other) {
        return getComponentId().compareTo(other.getComponentId());
    }

    @Override
    public String toString() {
        return "component " + getClassId() +
               (getClassId().toString().equals(getComponentId().toString()) ? "" : ": " + getComponentId());
    }

}
