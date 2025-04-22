package com.yahoo.vespa.clustercontroller.core.hostinfo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Cross-node/bucket-space/replica statistics from a particular distributor.
 *
 * Fields are nullable to be able to distinguish between a node being on a version that
 * does not send the fields vs. a node that sends the fields with zero values.
 *
 * @param storedDocumentCount Number of documents stored across all bucket spaces on all
 *                            content node replicas controlled by the distributor.
 * @param storedDocumentBytes Combined byte size of the documents reported by storedDocumentCount.
 */
public record DistributorGlobalStats(@JsonProperty("stored-document-count") Long storedDocumentCount,
                                     @JsonProperty("stored-document-bytes") Long storedDocumentBytes) {
    public static final DistributorGlobalStats EMPTY = new DistributorGlobalStats(null, null);
}
