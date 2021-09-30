// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "splitbucketsession.h"
#include "bucketdeltapair.h"
#include "i_bucket_create_notifier.h"
#include <cassert>

namespace proton::bucketdb {


SplitBucketSession::SplitBucketSession(BucketDBOwner &bucketDB,
                                       IBucketCreateNotifier &bucketCreateNotifier,
                                       const BucketId &source,
                                       const BucketId &target1,
                                       const BucketId &target2)
    : BucketSessionBase(bucketDB, bucketCreateNotifier),
      _target1Delta(),
      _target2Delta(),
      _sourceActive(false),
      _adjustTarget1ActiveLids(false),
      _adjustTarget2ActiveLids(false),
      _source(source),
      _target1(target1),
      _target2(target2)
{ }

void
SplitBucketSession::setup()
{
    if (_target1.valid()) {
        _bucketDB->createBucket(_target1);
    }
    if (_target2.valid()) {
        _bucketDB->createBucket(_target2);
    }

    BucketState *sourceState = nullptr;
    _sourceActive = extractInfo(_source, sourceState);

    if (_target1.valid()) {
        BucketState *target1State = _bucketDB->getBucketStatePtr(_target1);
        _adjustTarget1ActiveLids = calcFixupNeed(target1State, _sourceActive, true);
    }
    if (_target2.valid()) {
        BucketState *target2State = _bucketDB->getBucketStatePtr(_target2);
        _adjustTarget2ActiveLids = calcFixupNeed(target2State, _sourceActive, true);
    }
}

void
SplitBucketSession::applyDeltas(const BucketDeltaPair &deltas)
{
    _target1Delta += deltas._delta1;
    _target2Delta += deltas._delta2;
}

void
SplitBucketSession::applyDelta(const BucketState &delta, BucketState *src, BucketId &dstBucket)
{
    if (delta.empty())
        return;
    assert(dstBucket.valid());
    BucketState *dst = _bucketDB->getBucketStatePtr(dstBucket);
    delta.applyDelta(src, dst);
}

void
SplitBucketSession::finish()
{
    BucketState *sourceState = nullptr;
    (void) extractInfo(_source, sourceState);
    if (!sourceState) {
        assert(_target1Delta.empty());
        assert(_target2Delta.empty());
        return;
    }
    applyDelta(_target1Delta, sourceState, _target1);
    applyDelta(_target2Delta, sourceState, _target2);
    if (sourceState && sourceState->empty()) {
        _bucketDB->deleteEmptyBucket(_source);
    }
    if (!_target1Delta.empty()) {
        _bucketCreateNotifier.notifyCreateBucket(_bucketDB, _target1);
    }
    if (!_target2Delta.empty()) {
        _bucketCreateNotifier.notifyCreateBucket(_bucketDB, _target2);
    }
}

}

