// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "types.h"
#include <vespa/storageapi/message/bucketsplitting.h>

namespace storage {

namespace spi { struct PersistenceProvider; }
class PersistenceUtil;
class BucketOwnershipNotifier;
class RecheckBucketInfoCommand;

/**
 * Handle operations that might changes bucket ownership.
 * It is stateless and thread safe.
 */
class SplitJoinHandler : public Types {
public:
    SplitJoinHandler(PersistenceUtil &, spi::PersistenceProvider &,
                     BucketOwnershipNotifier &, bool enableMultibitSplitOptimalization);
    MessageTrackerUP handleSplitBucket(api::SplitBucketCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleSetBucketState(api::SetBucketStateCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleRecheckBucketInfo(RecheckBucketInfoCommand& cmd, MessageTrackerUP tracker) const;
    MessageTrackerUP handleJoinBuckets(api::JoinBucketsCommand& cmd, MessageTrackerUP tracker) const;
private:
    /**
     * Sanity-checking of join command parameters. Invokes tracker.fail() with
     * an appropriate error and returns false iff the command does not validate
     * OK. Returns true and does not touch the tracker otherwise.
     */
    static bool validateJoinCommand(const api::JoinBucketsCommand& cmd, MessageTracker& tracker);
    PersistenceUtil          &_env;
    spi::PersistenceProvider &_spi;
    BucketOwnershipNotifier  &_bucketOwnershipNotifier;
    bool                      _enableMultibitSplitOptimalization;
};

} // storage

