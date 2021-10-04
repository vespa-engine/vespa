package ai.vespa.cloud;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The properties of a cluster of nodes.
 *
 * @author gjoranv
 */
public class Cluster {

    private final int size;
    private final List<Integer> indices;

    public Cluster(int size, List<Integer> indices) {
        Objects.requireNonNull(indices, "Indices cannot be null!");
        this.size = size;
        this.indices = Collections.unmodifiableList(indices);
    }

    /** Returns the number of nodes in this cluster. */
    public int size() { return size; }

    /** Returns a list of node indices in this cluster. */
    public List<Integer> indices() {
        return indices;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Cluster cluster = (Cluster) o;
        return size == cluster.size &&
                indices.equals(cluster.indices);
    }

    @Override
    public int hashCode() {
        return Objects.hash(size, indices);
    }

}
