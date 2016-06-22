// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.provision.restapi;

import com.yahoo.vespa.hosted.provision.Node;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Converts {@link Node.Type} to/from serialized form in REST APIs.
 * @author dybis
 */
public class NodeTypeSerializer {

    private static final Map<Node.Type, String> serializationMap = new HashMap<>();
    private static final Map<String, Node.Type> deserializationMap = new HashMap<>();

    private static void addMapping(final Node.Type nodeType, final String wireName) {
        serializationMap.put(nodeType, wireName);
        deserializationMap.put(wireName, nodeType);
    }

    static {
        // Alphabetical order. No cheating, please - don't use .name(), .toString(), reflection etc. to get wire name.
        addMapping(Node.Type.host, "host");
        addMapping(Node.Type.tenant, "tenant");
    }

    private NodeTypeSerializer() {}  // Utility class, no instances.

    public static Node.Type fromWireName(final String wireName) {
        // TODO: Remove the next lines when NodeRepo contains type information for all nodes.
        if (wireName == null || wireName.isEmpty()) {
            return Node.Type.tenant;
        }
        final Optional<Node.Type> type = Optional.ofNullable(deserializationMap.get(wireName));
        if (! type.isPresent()) {
            throw new RuntimeException("Bug: Unknown desseriliazation of node type string " + wireName);
        }
        return type.get();
    }

    public static String wireNameOf(final Node.Type nodeType) {
        final String wireName = serializationMap.get(nodeType);
        if (wireName == null) {
            throw new RuntimeException("Bug: Unknown serialization form of node type " + nodeType.name());
        }
        return wireName;
    }
}
