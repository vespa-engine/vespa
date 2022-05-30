// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"
#include <vespa/document/bucket/bucketid.h>

namespace proton {

class CreateBucketOperation : public FeedOperation
{
    document::BucketId   _bucketId;

public:
    CreateBucketOperation();
    CreateBucketOperation(const document::BucketId &bucketId);
    ~CreateBucketOperation() override = default;
    const document::BucketId &getBucketId() const { return _bucketId; }
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is, const document::DocumentTypeRepo &repo) override;
    vespalib::string toString() const override;
};

} // namespace proton

