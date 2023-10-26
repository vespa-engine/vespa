// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketsessionbase.h"

namespace proton::bucketdb {

class BucketDeltaPair;

/**
 * The JoinBucketsSession class bundles some temporary variables used
 * during a join operation, allowing for a cleaner API when calling
 * methods to perform some of the steps in the join operation.
 *
 * It sets up variables in the constructor, contains a few methods that
 * are forwarded to BucketDB with appropriate argument shuffling, and
 * also removes empty source buckets after join.
 *
 */
class JoinBucketsSession : public BucketSessionBase
{
private:
    BucketState _source1Delta;
    BucketState _source2Delta;
    bool _wantTargetActive;
    bool _adjustSource1ActiveLids;
    bool _adjustSource2ActiveLids;
    bool _adjustTargetActiveLids;
    BucketId _source1;
    BucketId _source2;
    BucketId _target;

    bool
    applyDelta(const BucketState &delta, BucketId &srcBucket, BucketState *dst);

public:
    JoinBucketsSession(BucketDBOwner &bucketDB,
                       IBucketCreateNotifier &bucketCreateNotifier,
                       const BucketId &source1,
                       const BucketId &source2,
                       const BucketId &target);

    void applyDeltas(const BucketDeltaPair &deltas);
    bool getWantTargetActive() const { return _wantTargetActive; }
    bool mustFixupTargetActiveLids(bool movedSource1Docs, bool movedSource2Docs) const;
    void setup();
    void finish();
    const BucketId & getSource1() const { return _source1; }
    const BucketId & getSource2() const { return _source2; }
    const BucketId & getTarget() const { return _target;}
};

}
