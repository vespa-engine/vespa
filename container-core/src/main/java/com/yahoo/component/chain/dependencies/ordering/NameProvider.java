// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;

/**
 * A node containing nodes providing a given name.
 *
 * @author Tony Vaagenes
 */
abstract class NameProvider extends Node {

    final String name;

    public NameProvider(String name, int priority) {
        super(priority);
        this.name = name;
    }

    protected abstract void addNode(ComponentNode<?> node);

    protected String name() {
        return name;
    }

    @Override
    protected String dotName() {
        return name;
    }

}


