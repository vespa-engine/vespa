// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/persistence/spi/bucketinfo.h>

namespace document { class BucketId; }

namespace proton {

/**
 * Interface used to notify when bucket state has changed.
 */
class IBucketStateChangedHandler
{
public:
    virtual void notifyBucketStateChanged(const document::BucketId &bucketId,
                                          storage::spi::BucketInfo::ActiveState newState) = 0;

    virtual ~IBucketStateChangedHandler() = default;
};

}
