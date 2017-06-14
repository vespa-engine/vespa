// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "frozenbuckets.h"
#include "ibucketfreezelistener.h"
#include <vespa/searchcorespi/index/i_thread_service.h>
#include <vespa/vespalib/util/closuretask.h>

using document::BucketId;
using vespalib::makeClosure;
using vespalib::makeTask;
using vespalib::MonitorGuard;

namespace proton
{

FrozenBucketsMap::FrozenBucketsMap() :
    _lock(),
    _map()
{ }

FrozenBucketsMap::~FrozenBucketsMap() {
    assert(_map.empty());
}

void
FrozenBucketsMap::freezeBucket(BucketId bucket) {

    MonitorGuard guard(_lock);
    std::pair<BucketId, FrozenBucket> tryVal(std::make_pair(bucket, FrozenBucket(FrozenBucket::Reader)));

    std::pair<Map::iterator, bool> res;
    for (res = _map.insert(tryVal); !res.second && (res.first->second.isExclusive()); res = _map.insert(tryVal)) {
        guard.wait();
    }

    if (!res.second) {
        res.first->second.addReader();
    }
}


bool
FrozenBucketsMap::thawBucket(BucketId bucket)
{
    MonitorGuard guard(_lock);
    Map::iterator it(_map.find(bucket));
    assert(it != _map.end());
    assert(it->second.hasReaders());
    bool isLastAndContended(false);
    if (it->second.isLast()) {
        if (it->second.getNotifyWriter()) {
            isLastAndContended = true;
        }
        _map.erase(it);
        guard.broadcast();
    } else {
        it->second.removeReader();
    }
    return isLastAndContended;
}


IFrozenBucketHandler::ExclusiveBucketGuard::UP
FrozenBucketsMap::acquireExclusiveBucket(document::BucketId bucket)
{
    MonitorGuard guard(_lock);
    Map::iterator it(_map.find(bucket));
    if (it != _map.end()) {
        assert(it->second.hasReaders());
        it->second.setNotifyWriter();
        return ExclusiveBucketGuard::UP();
    }
    _map[bucket] = FrozenBucket(FrozenBucket::Writer);
    return std::make_unique<ExclusiveBucketGuard>(*this, bucket);
}

void
FrozenBucketsMap::releaseExclusiveBucket(document::BucketId bucket)
{
    MonitorGuard guard(_lock);
    Map::const_iterator it(_map.find(bucket));
    assert ((it != _map.end()) && (it->second.isExclusive()));
    _map.erase(it);
    guard.broadcast();
}

FrozenBuckets::FrozenBuckets(IThreadService &masterThread) :
    _frozen(),
    _masterThread(masterThread),
    _listeners()
{
}

FrozenBuckets::~FrozenBuckets()
{
    assert(_listeners.empty());
}

IFrozenBucketHandler::ExclusiveBucketGuard::UP
FrozenBuckets::acquireExclusiveBucket(document::BucketId bucket) {
    return _frozen.acquireExclusiveBucket(bucket);
}

void
FrozenBuckets::notifyThawed(document::BucketId bucket) {
    assert(_masterThread.isCurrentThread());
    for (auto &listener : _listeners) {
        listener->notifyThawedBucket(bucket);
    }
}

void
FrozenBuckets::freezeBucket(BucketId bucket)
{
    _frozen.freezeBucket(bucket);
}

void
FrozenBuckets::thawBucket(BucketId bucket)
{
    if (_frozen.thawBucket(bucket)) {
        _masterThread.execute(makeTask(makeClosure(this, &FrozenBuckets::notifyThawed, bucket)));
    }
}

void
FrozenBuckets::addListener(IBucketFreezeListener *listener)
{
    // assert(_masterThread.isCurrentThread());
    _listeners.push_back(listener);
}

void
FrozenBuckets::removeListener(IBucketFreezeListener *listener)
{
    // assert(_masterThread.isCurrentThread());
    auto it = std::find(_listeners.begin(), _listeners.end(), listener);
    if (it != _listeners.end()) {
        _listeners.erase(it);
    }
}

} // namespace proton
