// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi.v2;

import com.yahoo.vespa.hosted.provision.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Converts {@link Node.State} to/from serialized form in REST APIs.
 *
 * @author bakksjo
 */
public class NodeStateSerializer {

    private static final Map<Node.State, String> serializationMap = new HashMap<>();
    private static final Map<String, Node.State> deserializationMap = new HashMap<>();

    private static void addMapping(final Node.State nodeState, final String wireName) {
        serializationMap.put(nodeState, wireName);
        deserializationMap.put(wireName, nodeState);
    }

    static {
        // Alphabetical order. No cheating, please - don't use .name(), .toString(), reflection etc. to get wire name.
        addMapping(Node.State.active, "active");
        addMapping(Node.State.dirty, "dirty");
        addMapping(Node.State.failed, "failed");
        addMapping(Node.State.inactive, "inactive");
        addMapping(Node.State.parked, "parked");
        addMapping(Node.State.provisioned, "provisioned");
        addMapping(Node.State.ready, "ready");
        addMapping(Node.State.reserved, "reserved");
    }

    private NodeStateSerializer() {}  // Utility class, no instances.

    public static Optional<Node.State> fromWireName(final String wireName) {
        return Optional.ofNullable(deserializationMap.get(wireName));
    }

    public static String wireNameOf(final Node.State nodeState) {
        final String wireName = serializationMap.get(nodeState);
        if (wireName == null) {
            throw new RuntimeException("Bug: Unknown serialization form of node state " + nodeState.name());
        }
        return wireName;
    }
}
