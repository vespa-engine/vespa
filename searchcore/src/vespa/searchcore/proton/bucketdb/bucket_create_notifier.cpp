// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucket_create_notifier.h"
#include "i_bucket_create_listener.h"
#include <algorithm>
#include <cassert>

using document::BucketId;

namespace proton::bucketdb {

BucketCreateNotifier::BucketCreateNotifier()
    : _listeners()
{
}

BucketCreateNotifier::~BucketCreateNotifier()
{
    assert(_listeners.empty());
}

void
BucketCreateNotifier::notifyCreateBucket(const Guard & guard, const BucketId &bucket)
{
    for (const auto &listener : _listeners) {
        listener->notifyCreateBucket(guard, bucket);
    }
}

void
BucketCreateNotifier::addListener(IBucketCreateListener *listener)
{
    _listeners.push_back(listener);
}

void
BucketCreateNotifier::removeListener(IBucketCreateListener *listener)
{
    auto it = std::find(_listeners.begin(), _listeners.end(), listener);
    if (it != _listeners.end()) {
        _listeners.erase(it);
    }
}

}

