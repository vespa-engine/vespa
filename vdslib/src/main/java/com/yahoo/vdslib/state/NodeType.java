// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

public enum NodeType {
    STORAGE("storage"),
    DISTRIBUTOR("distributor");

    private final String serializeAs;

    private NodeType(String serializeAs) {
        this.serializeAs = serializeAs;
    }

    public String toString() {
        return serializeAs;
    }

    public static NodeType get(String serialized) {
        for(NodeType type : values()) {
            if (type.serializeAs.equals(serialized)) return type;
        }
        throw new IllegalArgumentException("Unknown node type '" + serialized + "'. Legal values are 'storage' and 'distributor'.");
    }

    public static NodeType[] getTypes() {
        NodeType types[] = new NodeType[2];
        types[0] = STORAGE;
        types[1] = DISTRIBUTOR;
        return types;
    }

}
