// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;


import com.yahoo.config.provision.NodeType;
import com.yahoo.vespa.hosted.provision.Node;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class NodeSerializerTest {

    private final NodeSerializer serializer = new NodeSerializer();

    @Test
    public void serialize_node_types() {
        for (NodeType t : NodeType.values()) {
            assertEquals(t, serializer.typeFrom(serializer.toString(t)));
        }
    }

    @Test
    public void serialize_node_states() {
        for (Node.State s : Node.State.values()) {
            assertEquals(s, serializer.stateFrom(serializer.toString(s)));
        }
    }

}
