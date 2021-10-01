package ai.vespa.cloud;

import java.util.Collections;
import java.util.List;

/**
 * The properties of a cluster of nodes.
 *
 * @author gjoranv
 */
public class Cluster {

    private final int size;
    private final List<Integer> indices;

    public Cluster(int size, List<Integer> indices) {
        this.size = size;
        this.indices = Collections.unmodifiableList(indices);
    }

    /** Returns the number of nodes in this cluster. */
    public int size() { return size; }

    /** Returns a list of node indices in this cluster. */
    public List<Integer> indices() {
        return indices;
    }

}
