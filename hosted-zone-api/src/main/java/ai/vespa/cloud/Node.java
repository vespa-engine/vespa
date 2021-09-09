package ai.vespa.cloud;

import java.util.Objects;

/**
 * The node where this process (usually a Jdisc container) is running.
 *
 * @author gjoranv
 */
public class Node {

    private final String id;

    public Node(String id) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("The 'id' field cannot be null or blank!");
        this.id = id;
    }

    /** Returns a unique ID for this node. */
    public String id() { return id; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Node node = (Node) o;
        return id.equals(node.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
