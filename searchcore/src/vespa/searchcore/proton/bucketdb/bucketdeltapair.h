// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketstate.h"

namespace proton::bucketdb {

/**
 * Class BucketDeltaPair represent the deltas to bucket states caused by
 * a join or split operation.
 */
class BucketDeltaPair
{
public:
    BucketState _delta1;
    BucketState _delta2;

    BucketDeltaPair()
        : _delta1(),
          _delta2()
    { }
};

}
