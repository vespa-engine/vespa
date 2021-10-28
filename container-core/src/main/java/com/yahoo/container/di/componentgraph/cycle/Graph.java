// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.di.componentgraph.cycle;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Class representing a directed graph.
 *
 * @author gjoranv
 */
public class Graph<T> {

    private final Map<T, LinkedHashSet<T>> adjMap = new LinkedHashMap<>();

    public void edge(T from, T to) {
        if (from == null || to == null)
            throw new IllegalArgumentException("Null vertices are not allowed, edge: " + from + "->" + to);

        adjMap.computeIfAbsent(from, k -> new LinkedHashSet<>()).add(to);
        adjMap.computeIfAbsent(to, k -> new LinkedHashSet<>());
    }

    Set<T> getVertices() {
        return adjMap.keySet();
    }

    /**
     * Returns the outgoing edges of the given vertex.
     */
    Set<T> getAdjacent(T vertex) {
        return adjMap.get(vertex);
    }

    private void throwIfMissingVertex(T vertex) {
        if (! adjMap.containsKey(vertex)) throw new IllegalArgumentException("No such vertex in the graph: " + vertex);
    }
}
