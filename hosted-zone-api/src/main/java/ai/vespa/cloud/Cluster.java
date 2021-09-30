package ai.vespa.cloud;

/**
 * The properties of a cluster of nodes.
 *
 * @author gjoranv
 */
public class Cluster {

    private final int size;

    public Cluster(int size) {
        this.size = size;
    }

    /** Returns the number of nodes in this cluster. */
    public int size() { return size; }

}
