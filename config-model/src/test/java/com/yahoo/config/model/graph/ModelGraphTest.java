// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.graph;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.test.MockRoot;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class ModelGraphTest {

    void assertOrdering(ModelGraph graph, String expectedOrdering) {
        List<ModelNode> sortedEntries = graph.topologicalSort();
        StringBuilder sb = new StringBuilder();
        for (ModelNode<?> node : sortedEntries) {
            sb.append(node.builder.getModelClass().getSimpleName());
        }
        assertEquals(expectedOrdering, sb.toString());
    }

    @Test
    void require_that_dependencies_are_correctly_set() {
        ModelGraphBuilder builder = new ModelGraphBuilder();
        builder.addBuilder(new GraphMock.BC()).addBuilder(new GraphMock.BB()).addBuilder(new GraphMock.BA());
        ModelGraph graph = builder.build();
        List<ModelNode> nodes = graph.getNodes();
        assertEquals(3, graph.getNodes().size());
        assertTrue(nodes.get(0).hasDependencies());
        assertTrue(nodes.get(1).hasDependencies());
        assertFalse(nodes.get(2).hasDependencies());
        assertTrue(nodes.get(0).dependsOn(nodes.get(1)));
        assertTrue(nodes.get(1).dependsOn(nodes.get(2)));
        assertFalse(nodes.get(2).dependsOn(nodes.get(0)));
    }

    @Test
    void require_that_dependencies_are_correctly_sorted() {
        ModelGraph graph = new ModelGraphBuilder().addBuilder(new GraphMock.BC()).addBuilder(new GraphMock.BB()).addBuilder(new GraphMock.BA()).build();
        assertOrdering(graph, "ABC");
    }

    @Test
    void require_that_cycles_are_detected() {
        assertThrows(IllegalArgumentException.class, () -> {
            ModelGraph graph = new ModelGraphBuilder().addBuilder(new GraphMock.BD()).addBuilder(new GraphMock.BE()).build();
            assertEquals(2, graph.getNodes().size());
            assertTrue(graph.getNodes().get(0).dependsOn(graph.getNodes().get(1)));
            assertTrue(graph.getNodes().get(1).dependsOn(graph.getNodes().get(0)));
            graph.topologicalSort();
        });
    }

    @Test
    void require_that_instance_can_be_created() {
        ModelGraph graph = new ModelGraphBuilder().addBuilder(new GraphMock.BC()).addBuilder(new GraphMock.BB()).addBuilder(new GraphMock.BA()).build();
        List<ModelNode> nodes = graph.topologicalSort();
        MockRoot root = new MockRoot();
        GraphMock.A a = (GraphMock.A) nodes.get(0).createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "first"));
        GraphMock.B b = (GraphMock.B) nodes.get(1).createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "second"));
        GraphMock.B b2 = (GraphMock.B) nodes.get(1).createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "second2"));
        GraphMock.C c = (GraphMock.C) nodes.get(2).createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "third"));
        assertNotNull(a);
        assertNotNull(b);
        assertNotNull(b2);
        assertNotNull(c);
        assertEquals("first", a.getId());
        assertEquals("second", b.getId());
        assertEquals("second2", b2.getId());
        assertEquals("third", c.getId());
        assertEquals(a, b.a);
        assertNotNull(c.b);
        assertEquals(2, c.b.size());
        assertTrue(c.b.contains(b));
        assertTrue(c.b.contains(b2));
    }

    @Test
    void require_that_context_must_be_first_ctor_param() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            ModelNode node = new ModelNode(new GraphMock.Bad.Builder());
            MockRoot root = new MockRoot();
            node.createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "foo"));
        });
        assertTrue(exception.getMessage().contains("Constructor for " + GraphMock.Bad.class.getName() + " must have as its first argument a " + ConfigModelContext.class.getName()));
    }

    @Test
    void require_that_ctor_arguments_must_be_models_or_collections_of_models() {
        Throwable exception = assertThrows(IllegalArgumentException.class, () -> {
            ModelNode node = new ModelNode(new GraphMock.Bad2.Builder());
            MockRoot root = new MockRoot();
            node.createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "foo"));
        });
        assertTrue(exception.getMessage().contains("Unable to find constructor argument class java.lang.String for com.yahoo.config.model.graph.GraphMock$Bad2"));
    }

    @Test
    void require_that_collections_can_be_empty() {
        ModelGraph graph = new ModelGraphBuilder().addBuilder(new GraphMock.BC()).addBuilder(new GraphMock.BA()).build();
        List<ModelNode> nodes = graph.topologicalSort();
        MockRoot root = new MockRoot();
        GraphMock.A a = (GraphMock.A) nodes.get(0).createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "first"));
        GraphMock.C c = (GraphMock.C) nodes.get(1).createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "second"));
        assertEquals(a, c.a);
        assertTrue(c.b.isEmpty());
    }

}
