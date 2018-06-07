// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::BucketReply
 * @ingroup messageapi
 *
 * @brief Superclass for storage replies which operates on single bucket.
 */

#pragma once

#include "storagereply.h"

namespace storage::api {

class BucketCommand;

class BucketReply : public StorageReply {
    document::Bucket _bucket;
    document::BucketId _originalBucket;

protected:
    BucketReply(const BucketCommand& cmd, const ReturnCode& code = ReturnCode(ReturnCode::OK));

public:
    DECLARE_POINTER_TYPEDEFS(BucketReply);

    document::Bucket getBucket() const override { return _bucket; }
    bool hasSingleBucketId() const override { return true; }

    bool hasBeenRemapped() const { return (_originalBucket.getRawId() != 0); }
    const document::BucketId& getOriginalBucketId() const { return _originalBucket; }

    /** The deserialization code need access to set the remapping. */
    void remapBucketId(const document::BucketId& bucket);
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

}
