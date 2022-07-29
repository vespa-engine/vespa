// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.di.componentgraph.cycle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.container.di.componentgraph.cycle.CycleFinderTest.Vertices.A;
import static com.yahoo.container.di.componentgraph.cycle.CycleFinderTest.Vertices.B;
import static com.yahoo.container.di.componentgraph.cycle.CycleFinderTest.Vertices.C;
import static com.yahoo.container.di.componentgraph.cycle.CycleFinderTest.Vertices.D;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class CycleFinderTest {

    enum Vertices {A, B, C, D}

    @Test
    void graph_without_cycles_returns_no_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(B, C);
        graph.edge(A, C);
        graph.edge(D, A);

        var cycleFinder = new CycleFinder<>(graph);
        assertTrue(cycleFinder.findCycle().isEmpty());
    }

    @Test
    void graph_with_cycle_returns_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(B, C);
        graph.edge(C, A);

        var cycleFinder = new CycleFinder<>(graph);
        assertTrue(cycleFinder.findCycle().containsAll(List.of(A, B, C, A)));
    }

    @Test
    void graph_with_self_referencing_vertex_returns_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, A);

        var cycleFinder = new CycleFinder<>(graph);
        assertTrue(cycleFinder.findCycle().containsAll(List.of(A, A)));
    }

    @Test
    void leading_nodes_are_stripped_from_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(B, C);
        graph.edge(C, B);

        var cycleFinder = new CycleFinder<>(graph);
        assertTrue(cycleFinder.findCycle().containsAll(List.of(B, C, B)));
    }

    @Test
    void findCycle_is_idempotent_with_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, A);

        var cycleFinder = new CycleFinder<>(graph);
        assertTrue(cycleFinder.findCycle().containsAll(List.of(A, A)));
        assertTrue(cycleFinder.findCycle().containsAll(List.of(A, A)));
    }

    @Test
    void findCycle_is_idempotent_without_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);

        var cycleFinder = new CycleFinder<>(graph);
        assertTrue(cycleFinder.findCycle().isEmpty());
        assertTrue(cycleFinder.findCycle().isEmpty());
    }

}
