// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"
#include <vespa/document/bucket/bucketid.h>

namespace documentapi {

class GetBucketStateMessage : public DocumentMessage {
private:
    document::BucketId _bucket;

protected:
    // Implements DocumentMessage.
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Constructs a new message for deserialization.
     */
    GetBucketStateMessage();

    /**
     * Constructs a new message with initial content.
     *
     * @param bucket The bucket whose state to retrieve.
     */
    GetBucketStateMessage(const document::BucketId &bucket);

    /**
     * Returns the bucket whose state to retrieve.
     *
     * @return The bucket id.
     */
    document::BucketId getBucketId() const { return _bucket; }

    /**
     * Sets the bucket whose state to retrieve.
     *
     * @param bucket The bucket id to set.
     */
    void setBucketId(document::BucketId bucket) { _bucket = bucket; }
    bool hasSequenceId() const override;
    uint64_t getSequenceId() const override;
    uint32_t getType() const override;
    string toString() const override { return "getbucketstatemessage"; }
};

}

