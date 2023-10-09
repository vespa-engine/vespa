// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "joinbucketsoperation.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

using document::BucketId;
using document::DocumentTypeRepo;
using vespalib::make_string;

namespace proton {

JoinBucketsOperation::JoinBucketsOperation()
    : FeedOperation(FeedOperation::JOIN_BUCKETS),
      _source1(),
      _source2(),
      _target()
{
}


JoinBucketsOperation::JoinBucketsOperation(const document::BucketId &source1,
                                           const document::BucketId &source2,
                                           const document::BucketId &target)
    : FeedOperation(FeedOperation::JOIN_BUCKETS),
      _source1(source1),
      _source2(source2),
      _target(target)
{
}


void
JoinBucketsOperation::serialize(vespalib::nbostream &os) const
{
    {
        assert(_source1.valid() || _source2.valid());
        assert(_target.valid());
        if (_source1.valid()) {
            assert(_source1.getUsedBits() > _target.getUsedBits());
            assert(_target.contains(_source1));
        }
        if (_source2.valid()) {
            assert(_source2.getUsedBits() > _target.getUsedBits());
            assert(_target.contains(_source2));
        }
    }
    os << _source1;
    os << _source2;
    os << _target;
}


void
JoinBucketsOperation::deserialize(vespalib::nbostream &is,
                                  const DocumentTypeRepo &)
{
    is >> _source1;
    is >> _source2;
    is >> _target;
}

vespalib::string JoinBucketsOperation::toString() const {
    return make_string("JoinBuckets(source1=%s, source2=%s, target=%s, "
                       "serialNum=%" PRIu64 ")",
                       _source1.toString().c_str(),
                       _source2.toString().c_str(),
                       _target.toString().c_str(), getSerialNum());
}

} // namespace proton
