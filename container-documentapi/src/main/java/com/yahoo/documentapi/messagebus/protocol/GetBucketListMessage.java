// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;
import com.yahoo.document.FixedBucketSpaces;

public class GetBucketListMessage extends DocumentMessage {

    private BucketId bucketId;
    private String bucketSpace = FixedBucketSpaces.defaultSpace();

    GetBucketListMessage() {
        // must be deserialized into
    }

    public GetBucketListMessage(BucketId bucketId) {
        this(bucketId, FixedBucketSpaces.defaultSpace());
    }

    public GetBucketListMessage(BucketId bucketId, String bucketSpace) {
        this.bucketId = bucketId;
        this.bucketSpace = bucketSpace;
    }

    public BucketId getBucketId() {
        return bucketId;
    }

    void setBucketId(BucketId id) {
        bucketId = id;
    }

    public String getBucketSpace() {
        return bucketSpace;
    }

    public void setBucketSpace(String bucketSpace) {
        this.bucketSpace = bucketSpace;
    }

    @Override
    public DocumentReply createReply() {
        return new StatBucketReply();
    }

    @Override
    public int getApproxSize() {
        return super.getApproxSize() + 8 + bucketSpace.length();
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_GETBUCKETLIST;
    }
}
