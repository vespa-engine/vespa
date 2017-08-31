// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;

/**
 * A phase providing a given name.
 *
 * @author Tony Vaagenes
 */
class PhaseNameProvider extends NameProvider {

    public PhaseNameProvider(String name, int priority) {
        super(name,priority);
    }

    protected void addNode(ComponentNode<?> newNode) {
        throw new ConflictingNodeTypeException("Both a phase and a searcher provides the name '" + name + "'");
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "[name = " + name + "]";
    }


    @Override
    int classPriority() {
        return 0;
    }

}
