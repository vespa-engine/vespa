// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bucket_db_owner.h"

namespace proton::bucketdb {

class IBucketCreateNotifier;

/**
 * Base class for split/join handling utility classes that bundles temporary
 * variables used during the operation.
 */
class BucketSessionBase
{
public:
    using GlobalId = document::GlobalId;
    using BucketId = document::BucketId;
    using Timestamp = storage::spi::Timestamp;

protected:
    Guard _bucketDB;
    IBucketCreateNotifier &_bucketCreateNotifier;

public:
    BucketSessionBase(BucketDBOwner &bucketDB, IBucketCreateNotifier &bucketCreateNotifier);
    BucketSessionBase(const BucketSessionBase &) = delete;
    BucketSessionBase & operator =(const BucketSessionBase &) = delete;
    ~BucketSessionBase();
    bool extractInfo(const BucketId &bucket, BucketState *&info);

    static bool calcFixupNeed(BucketState *state, bool wantActive, bool fixup);
};

}
