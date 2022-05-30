// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    virtual void serialize(vespalib::nbostream &os) const override;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &repo) override;
    virtual vespalib::string toString() const override;
};

} // namespace proton

