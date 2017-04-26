// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.restapi.v2.NodeStateSerializer;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author bakksjo
 */
public class NodeStateSerializerTest {

    @Test
    public void allStatesHaveASerializedForm() {
        for (Node.State nodeState : Node.State.values()) {
            assertNotNull(NodeStateSerializer.wireNameOf(nodeState));
        }
    }

    @Test
    public void wireNamesDoNotOverlap() {
        Set<String> wireNames = new HashSet<>();
        for (Node.State nodeState : Node.State.values()) {
            wireNames.add(NodeStateSerializer.wireNameOf(nodeState));
        }
        assertEquals(Node.State.values().length, wireNames.size());
    }

    @Test
    public void serializationAndDeserializationIsSymmetric() {
        for (Node.State nodeState : Node.State.values()) {
            String serialized = NodeStateSerializer.wireNameOf(nodeState);
            Node.State deserialized = NodeStateSerializer.fromWireName(serialized)
                    .orElseThrow(() -> new RuntimeException(
                            "Cannot deserialize '" + serialized + "', serialized form of " + nodeState.name()));
            assertEquals(nodeState, deserialized);
        }
    }

}
