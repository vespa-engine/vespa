// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.component.chain.dependencies.ordering;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import com.yahoo.component.chain.ChainedComponent;
import com.yahoo.component.chain.dependencies.Dependencies;
import org.junit.Before;
import org.junit.Test;

import com.yahoo.component.ComponentId;



/**
 * Test for OrderedReadyNodes.
 *
 * @author Tony Vaagenes
 */
@SuppressWarnings("rawtypes")
public class OrderedReadyNodesTest {

    class ComponentA extends ChainedComponent {
        public ComponentA(ComponentId id) {
            super(id);
        }

        @Override
        public Dependencies getDependencies() {
            return new Dependencies(Arrays.asList(getId().getName()), null, null);
        }
    }

    class ComponentB extends ComponentA {
        public ComponentB(ComponentId id) {
            super(id);
        }
    }

    private OrderedReadyNodes readyNodes;

    @Before
    public void setup() {
        readyNodes = new OrderedReadyNodes();
    }

    @Test
    public void require_NameProviders_before_SearcherNodes() {
        NameProvider nameProvider = createDummyNameProvider(100);
        ComponentNode componentNode = new ComponentNode<>(createFakeComponentA("a"), 1);

        addNodes(nameProvider, componentNode);

        assertEquals(nameProvider, pop());
        assertEquals(componentNode, pop());
    }

    private NameProvider createDummyNameProvider(int priority) {
        return new NameProvider("anonymous", priority) {
            @Override
            protected void addNode(ComponentNode node) {
                throw new UnsupportedOperationException();
            }

            @Override
            int classPriority() {
                return 0;
            }
        };
    }

    @Test
    public void require_SearcherNodes_ordered_by_insertion_order() {
        int priority = 0;
        ComponentNode a = new ComponentNode<>(createFakeComponentB("1"), priority++);
        ComponentNode b = new ComponentNode<>(createFakeComponentA("2"), priority++);
        ComponentNode c = new ComponentNode<>(createFakeComponentA("03"), priority++);

        addNodes(a, b, c);

        assertEquals(a, pop());
        assertEquals(b, pop());
        assertEquals(c, pop());
    }

    ChainedComponent createFakeComponentA(String id) {
        return new ComponentA(ComponentId.fromString(id));
    }

    ChainedComponent createFakeComponentB(String id) {
        return new ComponentB(ComponentId.fromString(id));
    }


    private void addNodes(Node... nodes) {
        for (Node node : nodes) {
            readyNodes.add(node);
        }
    }

    private Node pop() {
        return readyNodes.pop();
    }

}
