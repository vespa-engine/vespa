// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.model.graph;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.test.MockRoot;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.util.List;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

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
        assertThat(sb.toString(), is(expectedOrdering));
    }

    @Test
    public void require_that_dependencies_are_correctly_set() {
        ModelGraphBuilder builder = new ModelGraphBuilder();
        builder.addBuilder(new GraphMock.BC()).addBuilder(new GraphMock.BB()).addBuilder(new GraphMock.BA());
        ModelGraph graph = builder.build();
        List<ModelNode> nodes = graph.getNodes();
        assertThat(graph.getNodes().size(), is(3));
        assertTrue(nodes.get(0).hasDependencies());
        assertTrue(nodes.get(1).hasDependencies());
        assertFalse(nodes.get(2).hasDependencies());
        assertTrue(nodes.get(0).dependsOn(nodes.get(1)));
        assertTrue(nodes.get(1).dependsOn(nodes.get(2)));
        assertFalse(nodes.get(2).dependsOn(nodes.get(0)));
    }

    @Test
    public void require_that_dependencies_are_correctly_sorted() {
        ModelGraph graph = new ModelGraphBuilder().addBuilder(new GraphMock.BC()).addBuilder(new GraphMock.BB()).addBuilder(new GraphMock.BA()).build();
        assertOrdering(graph, "ABC");
    }

    @Test(expected = IllegalArgumentException.class)
    public void require_that_cycles_are_detected() {
        ModelGraph graph = new ModelGraphBuilder().addBuilder(new GraphMock.BD()).addBuilder(new GraphMock.BE()).build();
        assertThat(graph.getNodes().size(), is(2));
        assertTrue(graph.getNodes().get(0).dependsOn(graph.getNodes().get(1)));
        assertTrue(graph.getNodes().get(1).dependsOn(graph.getNodes().get(0)));
        graph.topologicalSort();
    }

    @Test
    public void require_that_instance_can_be_created() {
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
        assertThat(a.getId(), is("first"));
        assertThat(b.getId(), is("second"));
        assertThat(b2.getId(), is("second2"));
        assertThat(c.getId(), is("third"));
        assertThat(b.a, is(a));
        assertNotNull(c.b);
        assertThat(c.b.size(), is(2));
        assertTrue(c.b.contains(b));
        assertTrue(c.b.contains(b2));
    }

    @Rule
    public ExpectedException expectedEx = ExpectedException.none();

    @Test
    public void require_that_context_must_be_first_ctor_param() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Constructor for " + GraphMock.Bad.class.getName() + " must have as its first argument a " + ConfigModelContext.class.getName());
        ModelNode node = new ModelNode(new GraphMock.Bad.Builder());
        MockRoot root = new MockRoot();
        node.createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "foo"));
    }

    @Test
    public void require_that_ctor_arguments_must_be_models_or_collections_of_models() {
        expectedEx.expect(IllegalArgumentException.class);
        expectedEx.expectMessage("Unable to find constructor argument class java.lang.String for com.yahoo.config.model.graph.GraphMock$Bad2");
        ModelNode node = new ModelNode(new GraphMock.Bad2.Builder());
        MockRoot root = new MockRoot();
        node.createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "foo"));
    }

    @Test
    public void require_that_collections_can_be_empty() {
        ModelGraph graph = new ModelGraphBuilder().addBuilder(new GraphMock.BC()).addBuilder(new GraphMock.BA()).build();
        List<ModelNode> nodes = graph.topologicalSort();
        MockRoot root = new MockRoot();
        GraphMock.A a = (GraphMock.A) nodes.get(0).createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "first"));
        GraphMock.C c = (GraphMock.C) nodes.get(1).createModel(ConfigModelContext.create(root.getDeployState(), null, null, root, "second"));
        assertThat(c.a, is(a));
        assertTrue(c.b.isEmpty());
    }

}
