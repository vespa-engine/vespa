// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;

public class StatBucketMessage extends DocumentMessage {

    private BucketId bucketId;
    private String documentSelection;

    StatBucketMessage() {
        // need to deserialize into
    }

    public StatBucketMessage(BucketId bucket, String documentSelection) {
        this.bucketId = bucket;
        this.documentSelection = documentSelection;
    }

    public BucketId getBucketId() {
        return bucketId;
    }

    void setBucketId(BucketId bucket) {
        bucketId = bucket;
    }

    public String getDocumentSelection() {
        return documentSelection;
    }

    void setDocumentSelection(String documentSelection) {
        this.documentSelection = documentSelection;
    }

    @Override
    public DocumentReply createReply() {
        return new StatBucketReply();
    }

    @Override
    public int getApproxSize() {
        return super.getApproxSize() + 8 + documentSelection.length();
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_STATBUCKET;
    }
}
