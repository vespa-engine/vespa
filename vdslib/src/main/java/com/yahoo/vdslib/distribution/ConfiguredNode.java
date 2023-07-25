// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib.distribution;

/**
 * A node configured to exist, with its configured node specific information.
 * This is immutable. The identity and natural order of a node is its index.
 *
 * @author bratseth
 */
public record ConfiguredNode(int index, boolean retired) implements Comparable<ConfiguredNode> {

    /**
     * Return the index (distribution key) of this node
     */
    @Override
    public int index() {return index;}

    /**
     * Returns whether the node is configured to be retired
     */
    @Override
    public boolean retired() {return retired;}

    @Override
    public int hashCode() {return index;}

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (! (other instanceof ConfiguredNode)) return false;
        return ((ConfiguredNode) other).index == this.index;
    }

    @Override
    public int compareTo(ConfiguredNode other) {
        return Integer.compare(this.index, other.index);
    }

}
