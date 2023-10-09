// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi;

import com.yahoo.document.BucketId;

import java.util.List;

/**
 * Response containing list of empty buckets.
 *
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 */
public class EmptyBucketsVisitorResponse extends VisitorResponse {
    private List<BucketId> bucketIds;
    /**
     * Creates visitor response containing an ack token.
     *
     * @param bucketIds the empty buckets
     * @param token the ack token
     */
    public EmptyBucketsVisitorResponse(List<BucketId> bucketIds, AckToken token) {
        super(token);
        this.bucketIds = bucketIds;
    }

    public List<BucketId> getBucketIds() { return bucketIds; }
}
