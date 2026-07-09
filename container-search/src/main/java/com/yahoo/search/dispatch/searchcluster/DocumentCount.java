package com.yahoo.search.dispatch.searchcluster;

/**
 * Immutable class that stores the maximum number of documents over all groups in a search cluster.
 *
 * @author boeker
 */
public final class DocumentCount {
    // The maximum (target) active documents across all groups in the search cluster.
    private final long activeDocuments;
    private final long targetActiveDocuments;
    // Whether these numbers can be trusted (because they were gathered when all nodes in a group were working).
    private final boolean reliable;

    public DocumentCount() {
        this(0L, 0L, false);
    }

    public DocumentCount(long maximumActiveDocuments, long maximumTargetActiveDocuments, boolean reliable) {
        this.activeDocuments = maximumActiveDocuments;
        this.targetActiveDocuments = maximumTargetActiveDocuments;
        this.reliable = reliable;
    }

    public long getActiveDocuments() {
        return activeDocuments;
    }

    public long getTargetActiveDocuments() {
        return targetActiveDocuments;
    }

    public boolean isReliable () {
        return reliable;
    }
}
