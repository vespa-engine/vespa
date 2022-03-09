// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketid.h"
#include <vespa/vespalib/stllike/allocator.h>
#include <vector>

namespace document::bucket {

using BucketIdListT = std::vector<BucketId, vespalib::allocator_large<BucketId>>;

class BucketIdList : public BucketIdListT {
public:
    using BucketIdListT::BucketIdListT;
    BucketIdList(BucketIdList && rhs) noexcept = default;
    BucketIdList & operator = (BucketIdList &&) noexcept = default;
    BucketIdList(const BucketIdList & rhs);
    BucketIdList & operator = (const BucketIdList &);
    ~BucketIdList();
};

}
