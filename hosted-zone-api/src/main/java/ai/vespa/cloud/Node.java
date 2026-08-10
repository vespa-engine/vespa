// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import java.util.Objects;

/**
 * A node that is part of a cluster of e.g. Jdisc containers.
 *
 * @author gjoranv
 */
public class Node {

    private final int index;
    private final String availabilityZone;

    @Deprecated // TODO: Remove on Vespa 9
    public Node(int index) {
        this(index, "default");
    }

    public Node(int index, String availabilityZone) {
        this.index = index;
        this.availabilityZone = Objects.requireNonNull(availabilityZone);
    }

    /**
     * Returns the unique index of this node in the cluster.
     * Indices are non-negative, but not necessarily contiguous or starting from zero.
     */
    public int index() { return index; }

    /**
     * Returns the name of the availability zone this node is allocated in
     * (or possibly "default" for single-AZ zones).
     */
    public String availabilityZone() { return availabilityZone; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return index == node.index && availabilityZone.equals(node.availabilityZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(index, availabilityZone);
    }
}
