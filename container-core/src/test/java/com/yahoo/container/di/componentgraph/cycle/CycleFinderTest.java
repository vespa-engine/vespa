// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.di.componentgraph.cycle;

import org.junit.Test;

import static com.yahoo.container.di.componentgraph.cycle.CycleFinderTest.Vertices.A;
import static com.yahoo.container.di.componentgraph.cycle.CycleFinderTest.Vertices.B;
import static com.yahoo.container.di.componentgraph.cycle.CycleFinderTest.Vertices.C;
import static com.yahoo.container.di.componentgraph.cycle.CycleFinderTest.Vertices.D;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertThat;

/**
 * @author gjoranv
 */
public class CycleFinderTest {

    enum Vertices {A, B, C, D}

    @Test
    public void graph_without_cycles_returns_no_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(B, C);
        graph.edge(A, C);
        graph.edge(D, A);

        var cycleFinder = new CycleFinder<>(graph);
        assertThat(cycleFinder.findCycle(), empty());
    }

    @Test
    public void graph_with_cycle_returns_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(B, C);
        graph.edge(C, A);

        var cycleFinder = new CycleFinder<>(graph);
        assertThat(cycleFinder.findCycle(), contains(A, B, C, A));
    }

    @Test
    public void graph_with_self_referencing_vertex_returns_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, A);

        var cycleFinder = new CycleFinder<>(graph);
        assertThat(cycleFinder.findCycle(), contains(A, A));
    }

    @Test
    public void leading_nodes_are_stripped_from_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(B, C);
        graph.edge(C, B);

        var cycleFinder = new CycleFinder<>(graph);
        assertThat(cycleFinder.findCycle(), contains(B, C, B));
    }

    @Test
    public void findCycle_is_idempotent_with_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, A);

        var cycleFinder = new CycleFinder<>(graph);
        assertThat(cycleFinder.findCycle(), contains(A, A));
        assertThat(cycleFinder.findCycle(), contains(A, A));
    }

    @Test
    public void findCycle_is_idempotent_without_cycle() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);

        var cycleFinder = new CycleFinder<>(graph);
        assertThat(cycleFinder.findCycle(), empty());
        assertThat(cycleFinder.findCycle(), empty());
    }

}
