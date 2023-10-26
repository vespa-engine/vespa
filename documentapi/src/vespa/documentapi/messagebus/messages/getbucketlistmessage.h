// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "documentmessage.h"
#include <vespa/document/bucket/bucketid.h>
#include <vespa/vespalib/stllike/string.h>

namespace documentapi {

class GetBucketListMessage : public DocumentMessage {
private:
    document::BucketId _bucketId;
    vespalib::string _bucketSpace;

protected:
    // Implements DocumentMessage.
    DocumentReply::UP doCreateReply() const override;

public:
    /**
     * Constructs a new message with initial content.
     *
     * @param bucketId The bucket whose list to retrieve.
     */
    GetBucketListMessage(const document::BucketId &bucketId);

    ~GetBucketListMessage();

    /**
     * Returns the bucket whose list to retrieve.
     *
     * @return The bucket.
     */
    const document::BucketId &getBucketId() const { return _bucketId; }

    const vespalib::string &getBucketSpace() const { return _bucketSpace; }
    void setBucketSpace(const vespalib::string &value) { _bucketSpace = value; }
    uint32_t getType() const override;
    string toString() const override { return "getbucketlistmessage"; }
};

}
