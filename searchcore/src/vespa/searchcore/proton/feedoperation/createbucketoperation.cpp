// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "createbucketoperation.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

using document::BucketId;
using document::DocumentTypeRepo;
using vespalib::make_string;

namespace proton {

CreateBucketOperation::CreateBucketOperation()
    : FeedOperation(FeedOperation::CREATE_BUCKET),
      _bucketId()
{
}


CreateBucketOperation::CreateBucketOperation(const BucketId &bucketId)
    : FeedOperation(FeedOperation::CREATE_BUCKET),
      _bucketId(bucketId)
{
}


void
CreateBucketOperation::serialize(vespalib::nbostream &os) const
{
    assert(_bucketId.valid());
    os << _bucketId;
}


void
CreateBucketOperation::deserialize(vespalib::nbostream &is,
                                   const DocumentTypeRepo &)
{
    is >> _bucketId;
}

vespalib::string CreateBucketOperation::toString() const {
    return make_string("CreateBucket(%s, serialNum=%" PRIu64 ")",
                       _bucketId.toString().c_str(), getSerialNum());
}

} // namespace proton
