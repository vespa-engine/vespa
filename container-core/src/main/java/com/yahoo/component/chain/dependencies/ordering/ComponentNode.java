// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;

import com.yahoo.component.chain.ChainedComponent;

/**
 * A node representing a given component.
 *
 * @see Node
 * @author Tony Vaagenes
 */
class ComponentNode<T extends ChainedComponent> extends Node {

    private T component;

    public ComponentNode(T component, int priority) {
        super(priority);
        this.component = component;
    }

    T getComponent() {
        return component;
    }

    @Override
    protected String dotName() {
        //TODO: config dependent name
        return component.getClass().getSimpleName();
    }

    @Override
    int classPriority() {
        return 2;
    }

}

