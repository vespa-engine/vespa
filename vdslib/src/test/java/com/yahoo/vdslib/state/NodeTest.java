// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

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

        assertNotEquals(n1, n2);
        assertNotEquals(n1, n3);
        assertNotEquals(n1, n4);

        assertNotEquals(n2, n1);
        assertNotEquals(n2, n3);
        assertNotEquals(n2, n4);

        assertNotEquals(n3, n1);
        assertNotEquals(n3, n2);
        assertNotEquals(n3, n4);

        assertNotEquals(n4, n1);
        assertNotEquals(n4, n2);
        assertNotEquals(n4, n3);

        assertNotEquals("class not instance of Node", n1);
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
            fail("Method expected to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Not a legal node string 'nodewithoutdot'.", e.getMessage());
        }
        try {
            new Node("fleetcontroller.0");
            fail("Method expected to throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertEquals("Unknown node type 'fleetcontroller'. Legal values are 'storage' and 'distributor'.", e.getMessage());
        }
        try {
            new Node("storage.badindex");
            fail("Method expected to throw NumberFormatException");
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
