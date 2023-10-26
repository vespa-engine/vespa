// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucketsessionbase.h"

namespace proton::bucketdb {

class BucketDeltaPair;

/**
 * The SplitBucketSession class bundles some temporary variables used
 * during a split operation, allowing for a cleaner API when calling
 * methods to perform some of the steps in the split operation.
 *
 * It sets up variables in the constructor, contains a few methods that
 * are forwarded to BucketDB with appropriate argument shuffling, and
 * also removes empty source bucket after split.
 *
 */
class SplitBucketSession : public BucketSessionBase
{
private:
    BucketState _target1Delta;
    BucketState _target2Delta;
    bool _sourceActive;
    bool _adjustTarget1ActiveLids;
    bool _adjustTarget2ActiveLids;
    BucketId _source;
    BucketId _target1;
    BucketId _target2;

    void applyDelta(const BucketState &delta, BucketState *src, BucketId &dstBucket);

public:
    SplitBucketSession(BucketDBOwner &bucketDB,
                       IBucketCreateNotifier &bucketCreateNotifier,
                       const BucketId &source,
                       const BucketId &target1,
                       const BucketId &target2);

    /*
     * Reflect move of documents to target1 and target2 in bucket states
     */
    void applyDeltas(const BucketDeltaPair &deltas);
    bool getSourceActive() const { return _sourceActive; }

    /*
     * Return true if bitvector for active lids need to be adjusted in
     * document meta store due to old documents in target1 and active
     * state change.
     */
    bool mustFixupTarget1ActiveLids() const { return _adjustTarget1ActiveLids; }

    /*
     * Return true if bitvector for active lids need to be adjusted in
     * document meta store due to old documents in target2 and active
     * state change.
     */
    bool mustFixupTarget2ActiveLids() const { return _adjustTarget2ActiveLids; }

    void setup();
    void finish();
    const BucketId &getSource() const { return _source; }
    const BucketId &getTarget1() const { return _target1; }
    const BucketId &getTarget2() const { return _target2; }
};

}
