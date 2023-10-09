// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;


import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.restapi.NodeSerializer;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class NodeSerializerTest {

    @Test
    public void serialize_node_types() {
        for (NodeType t : NodeType.values()) {
            assertEquals(t, NodeSerializer.typeFrom(NodeSerializer.toString(t)));
        }
    }

    @Test
    public void serialize_node_states() {
        for (Node.State s : Node.State.values()) {
            assertEquals(s, NodeSerializer.stateFrom(NodeSerializer.toString(s)));
        }
    }

}
