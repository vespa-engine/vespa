// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "feedoperation.h"
#include <vespa/document/bucket/bucketid.h>

namespace proton {

class SplitBucketOperation : public FeedOperation
{
private:
    document::BucketId _source;
    document::BucketId _target1;
    document::BucketId _target2;
public:
    SplitBucketOperation();
    SplitBucketOperation(const document::BucketId &source,
                         const document::BucketId &target1,
                         const document::BucketId &target2);
    virtual ~SplitBucketOperation() {}
    const document::BucketId &getSource() const { return _source; }
    const document::BucketId &getTarget1() const { return _target1; }
    const document::BucketId &getTarget2() const { return _target2; }
    virtual void serialize(vespalib::nbostream &os) const override;
    virtual void deserialize(vespalib::nbostream &is,
                             const document::DocumentTypeRepo &repo) override;
    virtual vespalib::string toString() const override;
};

} // namespace proton

