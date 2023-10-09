// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.component.Version;
import com.yahoo.config.provision.NodeType;
import org.junit.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

/**
 * @author mpolden
 */
public class NodeTypeVersionsSerializerTest {

    @Test
    public void test_serialization() {
        Map<NodeType, Version> versions = new TreeMap<>();
        versions.put(NodeType.host, Version.fromString("7.1"));
        versions.put(NodeType.confighost, Version.fromString("7.2"));

        Map<NodeType, Version> serialized = NodeTypeVersionsSerializer.fromJson(NodeTypeVersionsSerializer.toJson(versions));
        assertEquals(versions, serialized);
    }

}
