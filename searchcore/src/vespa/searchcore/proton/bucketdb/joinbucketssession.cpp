// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "joinbucketssession.h"
#include "bucketdeltapair.h"
#include "i_bucket_create_notifier.h"
#include <cassert>

namespace proton::bucketdb {

JoinBucketsSession::JoinBucketsSession(BucketDBOwner &bucketDB,
                                       IBucketCreateNotifier &bucketCreateNotifier,
                                       const BucketId &source1,
                                       const BucketId &source2,
                                       const BucketId &target)
    : BucketSessionBase(bucketDB, bucketCreateNotifier),
      _source1Delta(),
      _source2Delta(),
      _wantTargetActive(false),
      _adjustSource1ActiveLids(false),
      _adjustSource2ActiveLids(false),
      _adjustTargetActiveLids(false),
      _source1(source1),
      _source2(source2),
      _target(target)
{
}


void
JoinBucketsSession::setup()
{
    if (_target.valid()) {
        _bucketDB->createBucket(_target);
    }
    BucketState *source1State = nullptr;
    BucketState *source2State = nullptr;
    bool source1Active = extractInfo(_source1, source1State);
    bool source2Active = extractInfo(_source2, source2State);
    _wantTargetActive = source1Active || source2Active;

    _adjustSource1ActiveLids = calcFixupNeed(source1State, _wantTargetActive, false);
    _adjustSource2ActiveLids = calcFixupNeed(source2State, _wantTargetActive, false);
    BucketState *targetState = nullptr;
    (void) extractInfo(_target, targetState);
    _adjustTargetActiveLids = calcFixupNeed(targetState, _wantTargetActive, true);
}


bool
JoinBucketsSession::mustFixupTargetActiveLids(bool movedSource1Docs, bool movedSource2Docs) const
{
    return _adjustTargetActiveLids ||
        (_adjustSource1ActiveLids && movedSource1Docs) ||
        (_adjustSource2ActiveLids && movedSource2Docs);
}


void
JoinBucketsSession::applyDeltas(const BucketDeltaPair &deltas)
{
    _source1Delta += deltas._delta1;
    _source2Delta += deltas._delta2;
}


bool
JoinBucketsSession::applyDelta(const BucketState &delta, BucketId &srcBucket, BucketState *dst)
{
    if (!srcBucket.valid()) {
        assert(delta.empty());
        return false;
    }
    BucketState *src = _bucketDB->getBucketStatePtr(srcBucket);
    if (delta.empty()) {
        return src && src->empty();
    }
    delta.applyDelta(src, dst);
    return src->empty();
}


void
JoinBucketsSession::finish()
{
    if (!_target.valid()) {
        assert(_source1Delta.empty());
        assert(_source2Delta.empty());
        return;
    }
    BucketState *targetState = _bucketDB->getBucketStatePtr(_target);
    bool source1Empty = applyDelta(_source1Delta, _source1, targetState);
    bool source2Empty = applyDelta(_source2Delta, _source2, targetState);
    if (source1Empty) {
        _bucketDB->deleteEmptyBucket(_source1);
    }
    if (source2Empty) {
        _bucketDB->deleteEmptyBucket(_source2);
    }
    if (!_source1Delta.empty() || !_source2Delta.empty()) {
        _bucketCreateNotifier.notifyCreateBucket(_bucketDB, _target);
    }
}

}
