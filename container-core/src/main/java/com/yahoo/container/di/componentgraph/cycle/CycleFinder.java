// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.di.componentgraph.cycle;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static com.yahoo.container.di.componentgraph.cycle.CycleFinder.State.BLACK;
import static com.yahoo.container.di.componentgraph.cycle.CycleFinder.State.GRAY;
import static com.yahoo.container.di.componentgraph.cycle.CycleFinder.State.WHITE;
import static java.util.logging.Level.FINE;
import static java.util.Collections.singletonList;


/**
 * <p>Applies the
 * <a href="https://www.geeksforgeeks.org/detect-cycle-direct-graph-using-colors/"> three-color algorithm</a>
 * to detect a cycle in a directed graph. If there are multiple cycles, this implementation only detects one
 * of them and does not guarantee that the shortest cycle is found.
 * </p>
 *
 * @author gjoranv
 */
public class CycleFinder<T> {
    private static final Logger log = Logger.getLogger(CycleFinder.class.getName());

    enum State {
        WHITE, GRAY, BLACK;
    }

    private final Graph<T> graph;

    private Map<T, State> colors;

    private List<T> cycle;

    public CycleFinder(Graph<T> graph) {
        this.graph = graph;
    }

    private void resetState() {
        cycle = null;
        colors = new LinkedHashMap<>();
        graph.getVertices().forEach(v -> colors.put(v, WHITE));
    }

    /**
     * Returns a list of vertices constituting a cycle in the graph, or an empty
     * list if no cycle was found. Only the first encountered cycle is returned.
     */
    public List<T> findCycle() {
        resetState();
        for (T vertex : graph.getVertices()) {
            if (colors.get(vertex) == WHITE) {
                if (visitDepthFirst(vertex, new ArrayList<>(singletonList(vertex)))) {
                    if (cycle == null) throw new IllegalStateException("Null cycle - this should never happen");
                    if (cycle.isEmpty()) throw new IllegalStateException("Empty cycle - this should never happen");
                    log.log(FINE, () -> "Cycle detected: " + cycle);
                    return cycle;
                }
            }
        }
        return new ArrayList<>();
    }

    private boolean visitDepthFirst(T vertex, List<T> path) {
        colors.put(vertex, GRAY);
        log.log(FINE, () -> "Vertex start " + vertex + " - colors: " + colors + " - path: " + path);
        for (T adjacent : graph.getAdjacent(vertex)) {
            path.add(adjacent);
            if (colors.get(adjacent) == GRAY) {
                cycle = removePathIntoCycle(path);
                return true;
            }
            if (colors.get(adjacent) == WHITE && visitDepthFirst(adjacent, path)) {
                return true;
            }
            path.remove(adjacent);
        }
        colors.put(vertex, BLACK);
        log.log(FINE, () -> "Vertex end " + vertex + " - colors: " + colors + " - path: " + path);
        return false;
    }

    private List<T> removePathIntoCycle(List<T> pathWithCycle) {
        T cycleStart = pathWithCycle.get(pathWithCycle.size() - 1);
        return pathWithCycle.stream()
                .dropWhile(vertex -> ! vertex.equals(cycleStart))
                .collect(Collectors.toList());
    }

}
