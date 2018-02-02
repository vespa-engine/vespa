// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;
import com.yahoo.document.FixedBucketSpaces;

public class StatBucketMessage extends DocumentMessage {

    private BucketId bucketId;
    private String bucketSpace = FixedBucketSpaces.defaultSpace();
    private String documentSelection;

    StatBucketMessage() {
        // need to deserialize into
    }

    public StatBucketMessage(BucketId bucket, String documentSelection) {
        this(bucket, FixedBucketSpaces.defaultSpace(), documentSelection);
    }

    public StatBucketMessage(BucketId bucketId, String bucketSpace, String documentSelection) {
        this.bucketId = bucketId;
        this.bucketSpace = bucketSpace;
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
        return super.getApproxSize() + 8 + bucketSpace.length() + documentSelection.length();
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_STATBUCKET;
    }
}
