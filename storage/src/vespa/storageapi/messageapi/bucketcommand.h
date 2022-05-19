// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * @class storage::api::BucketCommand
 * @ingroup messageapi
 *
 * @brief Superclass for storage commands that operate towards a single bucket.
 */

#pragma once

#include "storagecommand.h"
#include <vespa/document/bucket/bucket.h>

namespace storage::api {

class BucketCommand : public StorageCommand {
    document::Bucket   _bucket;
    document::BucketId _originalBucket;

protected:
    BucketCommand(const MessageType& type, const document::Bucket &bucket);

public:
    DECLARE_POINTER_TYPEDEFS(BucketCommand);

    void remapBucketId(const document::BucketId& bucket);
    document::Bucket getBucket() const override { return _bucket; }
    bool hasBeenRemapped() const { return (_originalBucket.getRawId() != 0); }
    const document::BucketId& getOriginalBucketId() const { return _originalBucket; }
    void print(std::ostream& out, bool verbose, const std::string& indent) const override;
};

}
