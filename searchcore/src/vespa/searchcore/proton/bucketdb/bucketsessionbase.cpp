// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketsessionbase.h"

namespace proton::bucketdb {

BucketSessionBase::BucketSessionBase(BucketDBOwner &bucketDB, IBucketCreateNotifier &bucketCreateNotifier)
    : _bucketDB(bucketDB.takeGuard()),
      _bucketCreateNotifier(bucketCreateNotifier)
{
}

BucketSessionBase::~BucketSessionBase() {
    _bucketDB->restoreIntegrity();
}

bool
BucketSessionBase::extractInfo(const BucketId &bucket, BucketState *&state)
{
    if (bucket.valid()) {
        state = _bucketDB->getBucketStatePtr(bucket);
    }
    return state && state->isActive();
}


bool
BucketSessionBase::calcFixupNeed(BucketState *state, bool wantActive, bool fixup)
{
    if (state && state->isActive() != wantActive) {
        if (fixup) {
            state->setActive(wantActive);
        }
        return state->getReadyCount() != 0;
    }
    return false;
}

}
