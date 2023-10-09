// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "deletebucketoperation.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

using document::BucketId;
using document::DocumentTypeRepo;
using vespalib::make_string;

namespace proton {

DeleteBucketOperation::DeleteBucketOperation()
    : RemoveDocumentsOperation(FeedOperation::DELETE_BUCKET),
      _bucketId()
{
}


DeleteBucketOperation::DeleteBucketOperation(const BucketId &bucketId)
    : RemoveDocumentsOperation(FeedOperation::DELETE_BUCKET),
      _bucketId(bucketId)
{
}


void
DeleteBucketOperation::serialize(vespalib::nbostream &os) const
{
    assert(_bucketId.valid());
    os << _bucketId;
    serializeLidsToRemove(os);
}


void
DeleteBucketOperation::deserialize(vespalib::nbostream &is,
                                   const DocumentTypeRepo &)
{
    is >> _bucketId;
    deserializeLidsToRemove(is);
}

vespalib::string DeleteBucketOperation::toString() const {
    return make_string("DeleteBucket(%s, serialNum=%" PRIu64 ")",
                       _bucketId.toString().c_str(), getSerialNum());
}

} // namespace proton
