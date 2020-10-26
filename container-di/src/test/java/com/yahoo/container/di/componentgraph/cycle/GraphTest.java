// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.di.componentgraph.cycle;

import org.junit.Test;

import static com.yahoo.container.di.componentgraph.cycle.GraphTest.Vertices.A;
import static com.yahoo.container.di.componentgraph.cycle.GraphTest.Vertices.B;
import static com.yahoo.container.di.componentgraph.cycle.GraphTest.Vertices.C;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

/**
 * @author gjoranv
 */
public class GraphTest {

    enum Vertices {A, B, C}

    @Test
    public void vertices_and_edges_are_added_and_can_be_retrieved() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(B, C);
        graph.edge(A, C);

        assertThat(graph.getVertices().size(), is(3));
        assertThat(graph.getAdjacent(A), containsInAnyOrder(B, C));
        assertThat(graph.getAdjacent(B), containsInAnyOrder(C));
        assertThat(graph.getAdjacent(C), empty());
    }

    @Test
    public void null_vertices_are_not_allowed() {
        var graph = new Graph<Vertices>();

        Exception e = assertThrows(IllegalArgumentException.class,
                () -> graph.edge(A, null));
        assertThat(e.getMessage(), startsWith("Null vertices are not allowed"));
    }

    @Test
    public void duplicate_edges_are_ignored() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(A, B);

        assertThat(graph.getAdjacent(A).size(), is(1));
    }

    @Test
    public void self_edges_are_allowed() {
        var graph = new Graph<Vertices>();
        graph.edge(A, A);

        assertThat(graph.getAdjacent(A), contains(A));
    }

}
