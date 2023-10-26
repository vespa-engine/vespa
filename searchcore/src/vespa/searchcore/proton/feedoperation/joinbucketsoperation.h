// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"
#include <vespa/document/bucket/bucketid.h>

namespace proton {

class JoinBucketsOperation : public FeedOperation
{
private:
    document::BucketId _source1;
    document::BucketId _source2;
    document::BucketId _target;
public:
    JoinBucketsOperation();
    JoinBucketsOperation(const document::BucketId &source1,
                         const document::BucketId &source2,
                         const document::BucketId &target);
    virtual ~JoinBucketsOperation() {}
    const document::BucketId &getSource1() const { return _source1; }
    const document::BucketId &getSource2() const { return _source2; }
    const document::BucketId &getTarget() const { return _target; }
    virtual void serialize(vespalib::nbostream &os) const override;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &repo) override;
    virtual vespalib::string toString() const override;
};

} // namespace proton

