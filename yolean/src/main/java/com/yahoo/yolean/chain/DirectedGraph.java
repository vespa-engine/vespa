// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO: prioritize vertices in edge map.
 *
 * @author Tony Vaagenes
 */
class DirectedGraph {

    private IdentityHashMap<Vertex, List<Vertex>> incommingEdges = new IdentityHashMap<>();
    private List<Vertex> vertices = new ArrayList<>();
    private List<Vertex> beginningVertices = new ArrayList<>();
    private List<Vertex> endingVertices = new ArrayList<>();

    public void addVertex(Vertex vertex) {
        vertices.add(vertex);
    }

    public void addBeginningVertex(Vertex vertex) {
        beginningVertices.add(vertex);
    }

    public void addEndingVertex(Vertex vertex) {
        endingVertices.add(vertex);
    }

    public void addEdge(Vertex start, Vertex end) {
        get(incommingEdges, end).add(start);
    }

    private static List<Vertex> get(Map<Vertex, List<Vertex>> edgeMap, Vertex key) {
        List<Vertex> value = edgeMap.get(key);
        return value == null ?
               addEmptyList(edgeMap, key) :
               value;
    }

    private static List<Vertex> addEmptyList(Map<Vertex, List<Vertex>> edgeMap, Vertex key) {
        List<Vertex> value = new ArrayList<>();
        edgeMap.put(key, value);
        return value;
    }

    public List<Vertex> topologicalSort() {
        EnumeratedIdentitySet<Vertex> visitedVertices = new EnumeratedIdentitySet<>();

        for (Vertex v : beginningVertices) {
            topologicalSortVisit(v, visitedVertices);
        }

        warnIfVisitedEndVertices(visitedVertices);

        for (Vertex v : vertices) {
            topologicalSortVisit(v, visitedVertices);
        }

        // TODO: review this!
        for (Vertex v : endingVertices) {
            topologicalSortVisit(v, visitedVertices);
        }

        return visitedVertices.insertionOrderedList();
    }

    private void warnIfVisitedEndVertices(EnumeratedIdentitySet<Vertex> visitedVertices) {
        //TVT:
    }

    private void topologicalSortVisit(Vertex vertex, Set<Vertex> visitedVertices) {
        topologicalSortVisitImpl(vertex, visitedVertices, new EnumeratedIdentitySet<Vertex>());
    }

    private void topologicalSortVisitImpl(Vertex vertex, Set<Vertex> visitedVertices,
                                          EnumeratedIdentitySet<Vertex> cycleDetector) {
        if (cycleDetector.contains(vertex)) {
            throw new ChainCycleException(cycleDetector.insertionOrderedList());
        }

        if (visitedVertices.contains(vertex)) {
            return;
        }

        cycleDetector.add(vertex);

        for (Vertex endVertex : emptyIfNull(incommingEdges.get(vertex))) {
            topologicalSortVisitImpl(endVertex, visitedVertices, cycleDetector);
        }

        visitedVertices.add(vertex);
        cycleDetector.remove(vertex);
    }

    private <T> List<T> emptyIfNull(List<T> list) {
        return list == null ?
               Collections.<T>emptyList() :
               list;
    }

}
