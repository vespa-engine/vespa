// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;


import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Ensures that Searchers are ordered deterministically.
 *
 * @author Tony Vaagenes
 */
class OrderedReadyNodes {

    private class PriorityComparator implements Comparator<Node> {
        @Override
        public int compare(Node lhs, Node rhs) {
            int result = new Integer(lhs.classPriority()).compareTo(rhs.classPriority());

            return result != 0 ?
                    result :
                    new Integer(lhs.priority).compareTo(rhs.priority);
        }
    }

    final private PriorityQueue<Node> nodes = new PriorityQueue<>(10, new PriorityComparator());

    public void add(Node node) {
        nodes.add(node);
    }

    public Node pop() {
        return nodes.poll();
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

}
