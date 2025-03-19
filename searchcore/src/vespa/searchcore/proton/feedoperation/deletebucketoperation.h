// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "removedocumentsoperation.h"
#include <vespa/document/bucket/bucketid.h>

namespace proton {

class DeleteBucketOperation : public RemoveDocumentsOperation
{
    document::BucketId   _bucketId;

public:
    DeleteBucketOperation();
    DeleteBucketOperation(const document::BucketId &bucketId);
    ~DeleteBucketOperation() override = default;
    const document::BucketId &getBucketId() const { return _bucketId; }
    void serialize(vespalib::nbostream &os) const override;
    void deserialize(vespalib::nbostream &is,
                     const document::DocumentTypeRepo &repo) override;
    std::string toString() const override;
};

} // namespace proton

