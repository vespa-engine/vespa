package com.yahoo.vespa.tenant.cd;

/**
 * A document selection expression, type and cluster, which can be used to visit an {@link Endpoint}.
 *
 * @author jonmv
 */
public class Selection {

    private final String selection;
    private final String namespace;
    private final String type;
    private final String group;
    private final String cluster;
    private final int concurrency;

    public Selection(String selection, String namespace, String type, String group, String cluster, int concurrency) {
        this.selection = selection;
        this.namespace = namespace;
        this.type = type;
        this.group = group;
        this.cluster = cluster;
        this.concurrency = concurrency;
    }

    /** Returns a new selection which will visit documents in the given cluster. */
    public static Selection in(String cluster) {
        if (cluster.isBlank()) throw new IllegalArgumentException("Cluster name can not be blank.");
        return new Selection(null, null, null, cluster, null, 1);
    }

    /** Returns a new selection which will visit documents in the given namespace and of the given type. */
    public static Selection of(String namespace, String type) {
        if (namespace.isBlank()) throw new IllegalArgumentException("Namespace can not be blank.");
        if (type.isBlank()) throw new IllegalArgumentException("Document type can not be blank.");
        return new Selection(null, namespace, type, null, null, 1);
    }

    /** Returns a copy of this with the given selection criterion set. */
    public Selection matching(String selection) {
        if (selection.isBlank()) throw new IllegalArgumentException("Selection can not be blank.");
        return new Selection(selection, namespace, type, cluster, group, concurrency);
    }

    /** Returns a copy of this selection, with the group set to the specified value. Requires namespace and type to be set. */
    public Selection limitedTo(String group) {
        if (namespace == null || type == null) throw new IllegalArgumentException("Namespace and type must be specified to set group.");
        if (group.isBlank()) throw new IllegalArgumentException("Group name can not be blank.");
        return new Selection(selection, namespace, type, cluster, group, concurrency);
    }

    /** Returns a copy of this, with concurrency set to the given positive value. */
    public Selection concurrently(int concurrency) {
        if (concurrency < 1) throw new IllegalArgumentException("Concurrency must be a positive integer.");
        return new Selection(selection, namespace, type, cluster, group, concurrency);
    }

}
