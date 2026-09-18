// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author arnej
 */
public class GroupTest {

    private static Node node(int key, String availabilityZone) {
        return new Node("test", key, "test-node" + key, 0, true, availabilityZone);
    }

    @Test
    void group_is_in_the_availability_zone_all_its_nodes_agree_on() {
        assertEquals("az1", new Group(0, List.of(node(0, "az1"))).availabilityZone());
        assertEquals("az1", new Group(0, List.of(node(0, "az1"), node(1, "az1"), node(2, "az1"))).availabilityZone());
    }

    @Test
    void group_has_no_availability_zone_when_its_nodes_disagree() {
        assertEquals(Node.UNKNOWN_AVAILABILITY_ZONE,
                     new Group(0, List.of(node(0, "az1"), node(1, "az2"))).availabilityZone());

        // A conflict counts wherever in the group it appears, not just between the first two nodes.
        assertEquals(Node.UNKNOWN_AVAILABILITY_ZONE,
                     new Group(0, List.of(node(0, "az1"), node(1, "az1"), node(2, "az2"))).availabilityZone());

        // A node without a zone configured conflicts with one which has it.
        assertEquals(Node.UNKNOWN_AVAILABILITY_ZONE,
                     new Group(0, List.of(node(0, Node.UNKNOWN_AVAILABILITY_ZONE), node(1, "az1"))).availabilityZone());
    }

    @Test
    void group_has_no_availability_zone_when_unconfigured_or_empty() {
        assertEquals(Node.UNKNOWN_AVAILABILITY_ZONE,
                     new Group(0, List.of(new Node("test", 0, "test-node0", 0, true))).availabilityZone());
        assertEquals(Node.UNKNOWN_AVAILABILITY_ZONE, new Group(0, List.of()).availabilityZone());
    }

}
