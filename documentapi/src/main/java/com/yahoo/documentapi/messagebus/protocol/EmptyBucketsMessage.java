// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;

import java.util.ArrayList;
import java.util.List;

/**
 * @author banino
 */
public class EmptyBucketsMessage extends VisitorMessage {

    private final List<BucketId> bids = new ArrayList<BucketId>();

    EmptyBucketsMessage() {
        // must be deserialized into
    }

    public EmptyBucketsMessage(List<BucketId> bids) {
        this.bids.addAll(bids);
    }

    public List<BucketId> getBucketIds() {
        return bids;
    }

    public void setBucketIds(List<BucketId> bids) {
        this.bids.clear();
        this.bids.addAll(bids);
    }

    @Override
    public DocumentReply createReply() {
        return new VisitorReply(DocumentProtocol.REPLY_EMPTYBUCKETS);
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_EMPTYBUCKETS;
    }
}
