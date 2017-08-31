// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;

import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.logging.Logger;

import com.yahoo.component.chain.ChainedComponent;

/**
 * A set of components providing a given name.
 *
 * @author Tony Vaagenes
 */
class ComponentNameProvider extends NameProvider {

    @SuppressWarnings("rawtypes")
    private Set<ComponentNode> nodes = new LinkedHashSet<>();
    private Logger logger = Logger.getLogger(getClass().getName());

    ComponentNameProvider(String name) {
        super(name, 0);
    }

    protected void addNode(@SuppressWarnings("rawtypes") ComponentNode componentNode) {
        if (nodes.add(componentNode))
            componentNode.notifyAfter();
    }

    @Override
    protected void handleRemoved(OrderedReadyNodes readyNodes) {
        for (Node node: nodes) {
            /*
            All providers must be run before dependencies are run.
            Adding these dependencies just in time improves dot output
            for the purpose of finding cycles manually.
            */
            for (Node afterThis : nodesAfterThis) {
                node.before(afterThis);
            }
            node.beforeRemoved(readyNodes);
        }
    }

    @Override
    int classPriority() {
        return 1;
    }

    @Override
    public String toString() {
        StringBuilder b=new StringBuilder("components [");
        for (@SuppressWarnings("rawtypes")
                Iterator<ComponentNode> i=nodes.iterator(); i.hasNext(); ) {
            b.append(i.next().getComponent().getId());
            if (i.hasNext())
                b.append(", ");
        }
        b.append("]");
        return b.toString();
    }

}
