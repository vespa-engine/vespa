// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud;

import java.util.List;
import java.util.Objects;

/**
 * The properties of a cluster of nodes.
 *
 * @author gjoranv
 */
public class Cluster {

    private final String id;
    private final int size;
    private final List<Integer> indices;

    // TODO: Remove on Vespa 9
    @Deprecated(forRemoval = true)
    public Cluster(int size, List<Integer> indices) {
        this("default", size, indices);
    }

    public Cluster(String id, int size, List<Integer> indices) {
        this.id = Objects.requireNonNull(id);
        this.size = size;
        this.indices = List.copyOf(Objects.requireNonNull(indices));
    }

    /** Returns the id of this cluster set in services.xml */
    public String id() { return id; }

    /** Returns the number of nodes in this cluster. */
    public int size() { return size; }

    /** Returns a list of node indices in this cluster. */
    public List<Integer> indices() {
        return indices;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if ( ! (o instanceof Cluster other)) return false;
        if ( ! this.id.equals(other.id)) return false;
        if ( this.size != other.size) return false;
        if ( ! this.indices.equals(other.indices)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, size, indices);
    }

}
