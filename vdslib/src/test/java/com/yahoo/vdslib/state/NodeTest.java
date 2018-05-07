// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NodeTest {

    @Test
    public void testEquals() {
        Node n1 = new Node(NodeType.STORAGE, 6);
        Node n2 = new Node(NodeType.STORAGE, 7);
        Node n3 = new Node(NodeType.DISTRIBUTOR, 6);
        Node n4 = new Node(NodeType.DISTRIBUTOR, 7);
        Node n5 = new Node(NodeType.STORAGE, 6);
        Node n6 = new Node(NodeType.STORAGE, 7);
        Node n7 = new Node(NodeType.DISTRIBUTOR, 6);
        Node n8 = new Node(NodeType.DISTRIBUTOR, 7);

        assertEquals(n1, n5);
        assertEquals(n2, n6);
        assertEquals(n3, n7);
        assertEquals(n4, n8);
        assertEquals(n1, n1);
        assertEquals(n2, n2);
        assertEquals(n3, n3);
        assertEquals(n4, n4);

        assertFalse(n1.equals(n2));
        assertFalse(n1.equals(n3));
        assertFalse(n1.equals(n4));

        assertFalse(n2.equals(n1));
        assertFalse(n2.equals(n3));
        assertFalse(n2.equals(n4));

        assertFalse(n3.equals(n1));
        assertFalse(n3.equals(n2));
        assertFalse(n3.equals(n4));

        assertFalse(n4.equals(n1));
        assertFalse(n4.equals(n2));
        assertFalse(n4.equals(n3));

        assertFalse(n1.equals("class not instance of Node"));
    }

    @Test
    public void testSerialization() {
        Node n = new Node(NodeType.STORAGE, 6);
        Node other = new Node(n.toString());
        assertEquals(n, other);
        assertEquals(n.hashCode(), other.hashCode());

        n = new Node(NodeType.DISTRIBUTOR, 5);
        other = new Node(n.toString());
        assertEquals(n, other);
        assertEquals(n.hashCode(), other.hashCode());

        try {
            new Node("nodewithoutdot");
            assertTrue("Method expected to throw IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            assertEquals("Not a legal node string 'nodewithoutdot'.", e.getMessage());
        }
        try {
            new Node("fleetcontroller.0");
            assertTrue("Method expected to throw IllegalArgumentException", false);
        } catch (IllegalArgumentException e) {
            assertEquals("Unknown node type 'fleetcontroller'. Legal values are 'storage' and 'distributor'.", e.getMessage());
        }
        try {
            new Node("storage.badindex");
            assertTrue("Method expected to throw NumberFormatException", false);
        } catch (NumberFormatException e) {
            assertEquals("For input string: \"badindex\"", e.getMessage());
        }
    }

    @Test
    public void testMaySetWantedState() {
        assertTrue(State.UP.maySetWantedStateForThisNodeState(State.DOWN));
        assertTrue(State.UP.maySetWantedStateForThisNodeState(State.MAINTENANCE));
        assertFalse(State.DOWN.maySetWantedStateForThisNodeState(State.UP));
        assertTrue(State.DOWN.maySetWantedStateForThisNodeState(State.MAINTENANCE));
        assertFalse(State.MAINTENANCE.maySetWantedStateForThisNodeState(State.UP));
        assertFalse(State.MAINTENANCE.maySetWantedStateForThisNodeState(State.DOWN));
    }

}
