// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_bucket_create_notifier.h"
#include <vector>

namespace proton::bucketdb {

class IBucketCreateListener;

/**
 * Class used to (un)register a listener to get notifications about
 * non-empty buckets created due to split/join operations.
 */
class BucketCreateNotifier : public IBucketCreateNotifier
{
    std::vector<IBucketCreateListener *> _listeners;

public:
    BucketCreateNotifier();
    virtual ~BucketCreateNotifier() override;

    virtual void notifyCreateBucket(const document::BucketId &bucket) override;
    virtual void addListener(IBucketCreateListener *listener) override;
    virtual void removeListener(IBucketCreateListener *listener) override;
};

}
