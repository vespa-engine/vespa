// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.state;

/**
 * A node in a content cluster. This is immutable.
 */
public class Node implements Comparable<Node> {

    private final NodeType type;
    private final int index;

    public Node(NodeType type, int index) {
        this.type = type;
        this.index = index;
    }

    public Node(String serialized) {
        int dot = serialized.lastIndexOf('.');
        if (dot < 0) throw new IllegalArgumentException("Not a legal node string '" + serialized + "'.");
        type = NodeType.get(serialized.substring(0, dot));
        index = Integer.valueOf(serialized.substring(dot + 1));
    }

    public static Node ofStorage(int index) {
        return new Node(NodeType.STORAGE, index);
    }

    public static Node ofDistributor(int index) {
        return new Node(NodeType.DISTRIBUTOR, index);
    }

    public String toString() {
        return type.toString() + "." + index;
    }

    public NodeType getType() { return type; }
    public int getIndex() { return index; }

    private int getOrdering() {
        return (type.equals(NodeType.STORAGE) ? 65536 : 0) + index;
    }

    @Override
    public int compareTo(Node n) {
        return getOrdering() - n.getOrdering();
    }

    @Override
    public int hashCode() {
        return type.hashCode() ^ index;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Node)) return false;
        Node n = (Node) o;
        return (type.equals(n.type) && index == n.index);
    }

}
