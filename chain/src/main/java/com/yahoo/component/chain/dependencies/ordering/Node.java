// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;

import java.util.HashSet;
import java.util.Set;

/**
 * A node in a dependency graph.
 *
 * Dependencies must declared as follows:
 *     a.before(b) , where a,b are nodes.
 *
 * The following dependencies are currently allowed:
 *     searcher.before(name)
 *     name.before(searcher)
 *     searcher1.before(searcher2)
 *
 *  Where name designates a NameProvider( either a phase or a set of searchers).
 *
 * @author Tony Vaagenes
*/
abstract class Node {
    //How this node should be prioritized if its compared with a node of the same class, see class priority.
    final int priority;

    private int numNodesBeforeThis = 0;
    Set<Node> nodesAfterThis = new HashSet<>();

    public Node(int priority) {
        this.priority = priority;
    }

    protected void before(Node node) {
        if (nodesAfterThis.add(node)) {
            node.notifyAfter();
        }
    }

    void notifyAfter() {
        ++numNodesBeforeThis;
    }

    void removed(OrderedReadyNodes readyNodes) {
        handleRemoved(readyNodes);
        for (Node node: nodesAfterThis) {
            node.beforeRemoved(readyNodes);
        }
    }

    void beforeRemoved(OrderedReadyNodes readyNodes) {
        --numNodesBeforeThis;

        if (ready()) {
            readyNodes.add(this);
        }
    }

    boolean ready() {
        return numNodesBeforeThis == 0;
    }

    protected void handleRemoved(OrderedReadyNodes readyNodes)  {}

    void dotDependenciesString(StringBuilder s, Set<Node> used) {
        if (used.contains(this))
            return;
        used.add(this);

        for (Node afterNode : nodesAfterThis) {
            String indent = "    ";
            s.append(indent);
            s.append(dotName()).append(" -> ").append(afterNode.dotName())
                    .append('\n');
            afterNode.dotDependenciesString(s, used);
        }
    }

    abstract protected String dotName();

    /*
     * Ensures that PhaseNameProviders < ComponentNameProviders < ComponentNodes
     * The regular priority is only considered if the class priorities are equal.
     */
    abstract int classPriority();
}
