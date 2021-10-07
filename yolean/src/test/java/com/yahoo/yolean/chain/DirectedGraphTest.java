// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.chain;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertTrue;

public class DirectedGraphTest {

    private DirectedGraph graph;
    private final Vertex[] v = new Vertex[10];

    @Before
    public void setup() {
        for (int i = 0; i < v.length; i++) {
            v[i] = new TestVertex(i);
        }

        graph = new DirectedGraph();
    }

    @Test
    public void before_all_are_prioritized_first() {
        graph.addVertex(v[0]);
        graph.addBeginningVertex(v[1]);

        assertTrue(graph.topologicalSort().containsAll(Arrays.asList(v[1], v[0])));
    }

    @Test
    public void vertex_can_be_placed_before_before_all_vertices() {
        graph.addVertex(v[0]);
        graph.addBeginningVertex(v[1]);
        graph.addEdge(v[0], v[1]);

        assertTrue(graph.topologicalSort().containsAll(Arrays.asList(v[0], v[1])));
    }

    static class TestVertex implements Vertex {

        private final int id;

        TestVertex(int id) {
            this.id = id;
        }

        @Override
        public String toString() {
            return "Vertex{" + id + '}';
        }
    }
}
