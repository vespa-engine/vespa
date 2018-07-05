// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.documentapi.messagebus.protocol;

import com.yahoo.document.BucketId;

/**
 * This message is a request to return the state of a given bucket. The corresponding reply is {@link
 * GetBucketStateReply}.
 *
 * @author Simon Thoresen Hult
 */
public class GetBucketStateMessage extends DocumentMessage {

    private BucketId bucket = null;

    /**
     * Constructs a new message for deserialization.
     */
    GetBucketStateMessage() {
        // empty
    }

    /**
     * Constructs a new reply with initial content.
     *
     * @param bucket The bucket whose state to reply with.
     */
    public GetBucketStateMessage(BucketId bucket) {
        this.bucket = bucket;
    }

    /**
     * Returns the bucket whose state this contains.
     *
     * @return The bucket id.
     */
    public BucketId getBucketId() {
        return bucket;
    }

    /**
     * Sets the bucket whose state this contains.
     *
     * @param bucket The bucket id to set.
     */
    public void setBucketId(BucketId bucket) {
        this.bucket = bucket;
    }

    @Override
    public DocumentReply createReply() {
        return new GetBucketStateReply();
    }

    @Override
    public long getSequenceId() {
        return bucket.getRawId();
    }

    @Override
    public int getApproxSize() {
        return super.getApproxSize() + 8;
    }

    @Override
    public int getType() {
        return DocumentProtocol.MESSAGE_GETBUCKETSTATE;
    }
}
