// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"
#include <vespa/document/bucket/bucket.h>

namespace documentapi {

class GetBucketListMessage : public DocumentMessage {
private:
    document::Bucket _bucket;

protected:
    // Implements DocumentMessage.
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Constructs a new message with initial content.
     *
     * @param bucket The bucket whose list to retrieve.
     */
    GetBucketListMessage(const document::Bucket &bucket);

    /**
     * Returns the bucket whose list to retrieve.
     *
     * @return The bucket.
     */
    const document::Bucket &getBucket() const { return _bucket; }

    uint32_t getType() const override;
    string toString() const override { return "getbucketlistmessage"; }
};

}
