// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import java.util.Objects;

/**
 * A node that is part of a cluster of e.g. Jdisc containers.
 *
 * @author gjoranv
 */
public class Node {

    private final int index;

    public Node(int index) {
        this.index = index;
    }

    /** Returns the unique index for this node in the cluster.
     * Indices are non-negative, but not necessarily contiguous or starting from zero. */
    public int index() { return index; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return index == node.index;
    }

    @Override
    public int hashCode() {
        return Objects.hash(index);
    }
}
