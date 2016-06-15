// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author bakksjo
 */
public class NodeStateSerializerTest {
    @Test
    public void allStatesHaveASerializedForm() {
        for (Node.State nodeState : Node.State.values()) {
            assertThat(NodeStateSerializer.wireNameOf(nodeState), is(notNullValue()));
        }
    }

    @Test
    public void wireNamesDoNotOverlap() {
        final Set<String> wireNames = new HashSet<>();
        for (Node.State nodeState : Node.State.values()) {
            wireNames.add(NodeStateSerializer.wireNameOf(nodeState));
        }
        assertThat(wireNames.size(), is(Node.State.values().length));
    }

    @Test
    public void serializationAndDeserializationIsSymmetric() {
        for (Node.State nodeState : Node.State.values()) {
            final String serialized = NodeStateSerializer.wireNameOf(nodeState);
            final Node.State deserialized = NodeStateSerializer.fromWireName(serialized)
                    .orElseThrow(() -> new RuntimeException(
                            "Cannot deserialize '" + serialized + "', serialized form of " + nodeState.name()));
            assertThat(deserialized, is(nodeState));
        }
    }
}
