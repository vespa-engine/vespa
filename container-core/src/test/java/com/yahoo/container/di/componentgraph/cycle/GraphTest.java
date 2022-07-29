// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.di.componentgraph.cycle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.container.di.componentgraph.cycle.GraphTest.Vertices.A;
import static com.yahoo.container.di.componentgraph.cycle.GraphTest.Vertices.B;
import static com.yahoo.container.di.componentgraph.cycle.GraphTest.Vertices.C;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author gjoranv
 */
public class GraphTest {

    enum Vertices {A, B, C}

    @Test
    void vertices_and_edges_are_added_and_can_be_retrieved() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(B, C);
        graph.edge(A, C);

        assertEquals(3, graph.getVertices().size());
        assertTrue(graph.getAdjacent(A).containsAll(List.of(B, C)));
        assertTrue(graph.getAdjacent(B).contains(C));
        assertTrue(graph.getAdjacent(C).isEmpty());
    }

    @Test
    void null_vertices_are_not_allowed() {
        var graph = new Graph<Vertices>();

        try {
            graph.edge(A, null);
            fail();
        } catch (IllegalArgumentException e) {
            assertEquals("Null vertices are not allowed, edge: A->null", e.getMessage());
        }
    }

    @Test
    void duplicate_edges_are_ignored() {
        var graph = new Graph<Vertices>();
        graph.edge(A, B);
        graph.edge(A, B);

        assertEquals(1, graph.getAdjacent(A).size());
    }

    @Test
    void self_edges_are_allowed() {
        var graph = new Graph<Vertices>();
        graph.edge(A, A);

        assertTrue(graph.getAdjacent(A).contains(A));
    }

}
