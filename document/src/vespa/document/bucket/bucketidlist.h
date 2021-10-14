// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketid.h"
#include <vespa/vespalib/util/array.h>

namespace document::bucket {

using BucketIdListT = vespalib::Array<BucketId>;

class BucketIdList : public BucketIdListT {
public:
    BucketIdList();
    BucketIdList(BucketIdList && rhs) = default;
    BucketIdList & operator = (BucketIdList &&) = default;
    BucketIdList(const BucketIdList & rhs);
    BucketIdList & operator = (const BucketIdList &);
    ~BucketIdList();
};

}
