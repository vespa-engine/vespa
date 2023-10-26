// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "splitbucketoperation.h"
#include <vespa/vespalib/util/stringfmt.h>
#include <cassert>

using document::BucketId;
using document::DocumentTypeRepo;
using vespalib::make_string;

namespace proton {

SplitBucketOperation::SplitBucketOperation()
    : FeedOperation(FeedOperation::SPLIT_BUCKET),
      _source(),
      _target1(),
      _target2()
{
}


SplitBucketOperation::SplitBucketOperation(const document::BucketId &source,
                                           const document::BucketId &target1,
                                           const document::BucketId &target2)
    : FeedOperation(FeedOperation::SPLIT_BUCKET),
      _source(source),
      _target1(target1),
      _target2(target2)
{
}


void
SplitBucketOperation::serialize(vespalib::nbostream &os) const
{
    {
        assert(_source.valid());
        assert(_target1.valid() || _target2.valid());
        if (_target1.valid()) {
            assert(_source.getUsedBits() < _target1.getUsedBits());
            assert(_source.contains(_target1));
        }
        if (_target2.valid()) {
            assert(_source.getUsedBits() < _target2.getUsedBits());
            assert(_source.contains(_target2));
        }
        if (_target1.valid() && _target2.valid()) {
            assert(_target1 != _target2);
            assert(!_target1.contains(_target2));
            assert(!_target2.contains(_target1));
        }
    }
    os << _source;
    os << _target1;
    os << _target2;
}


void
SplitBucketOperation::deserialize(vespalib::nbostream &is,
                                  const DocumentTypeRepo &)
{
    is >> _source;
    is >> _target1;
    is >> _target2;
}

vespalib::string SplitBucketOperation::toString() const {
    return make_string("SplitBucket(source=%s, target1=%s, target2=%s, "
                       "serialNum=%" PRIu64 ")",
                       _source.toString().c_str(),
                       _target1.toString().c_str(),
                       _target2.toString().c_str(), getSerialNum());
}

} // namespace proton
