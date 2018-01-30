// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;

public class GetBucketListMessage extends DocumentMessage {

    private BucketId bucketId;

    GetBucketListMessage() {
        // must be deserialized into
    }

    public GetBucketListMessage(BucketId bucketId) {
        this.bucketId = bucketId;
    }

    public BucketId getBucketId() {
        return bucketId;
    }

    void setBucketId(BucketId id) {
        bucketId = id;
    }

    @Override
    public DocumentReply createReply() {
        return new StatBucketReply();
    }

    @Override
    public int getApproxSize() {
        return super.getApproxSize() + 8;
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_GETBUCKETLIST;
    }
}
