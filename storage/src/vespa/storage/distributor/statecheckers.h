// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idealstatemanager.h"

namespace storage::distributor {

class SynchronizeAndMoveStateChecker : public StateChecker
{
public:
    Result check(Context& c) const override;
    const char* getName() const noexcept override { return "SynchronizeAndMove"; }
};

class DeleteExtraCopiesStateChecker : public StateChecker
{
public:
    Result check(Context& c) const override;
    const char* getName() const noexcept override { return "DeleteExtraCopies"; }

private:
    static bool bucketHasNoData(const Context& c);
    static void removeRedundantEmptyOrConsistentCopies(Context& c, std::vector<uint16_t>& removedCopies, vespalib::asciistream& reasons);
    static bool copyIsInIdealState(const BucketCopy& cp, const Context& c);
    static bool enoughCopiesKept(uint32_t keptIdealCopies, uint32_t keptNonIdealCopies, const Context& c);
    static uint32_t numberOfIdealCopiesPresent(const Context& c);
    static void addToRemoveSet(const BucketCopy& copyToRemove, const char* reasonForRemoval,
                               std::vector<uint16_t>& removedCopies, vespalib::asciistream& reasons);
                            
};

class JoinBucketsStateChecker : public StateChecker
{
public:
    Result check(Context& c) const override;
    const char* getName() const noexcept override { return "JoinBuckets"; }
private:
    static uint64_t getTotalUsedFileSize(const Context& c);
    static uint64_t getTotalMetaCount(const Context& c);
    static bool isFirstSibling(const document::BucketId& bucketId);
    static bool siblingsAreInSync(const Context& c);
    static bool shouldJoin(const Context& c);
    static bool smallEnoughToJoin(const Context& c);
    static bool singleBucketJoinIsEnabled(const Context&);
    static bool singleBucketJoinIsConsistent(const Context& c);
    static document::Bucket computeJoinBucket(const Context& c);
};

class SplitBucketStateChecker : public StateChecker
{
public:
    Result check(Context& c) const override;
    const char* getName() const noexcept override { return "SplitBucket"; }
private:
    static Result generateMinimumBucketSplitOperation(Context& c);
    static Result generateMaxSizeExceededSplitOperation(Context& c);

    static bool validForSplit(Context& c);
    static double getBucketSizeRelativeToMax(Context& c);
};

class SplitInconsistentStateChecker : public StateChecker
{
public:
    Result check(Context& c) const override;
    const char* getName() const noexcept override { return "SplitInconsistentBuckets"; }

private:
    static bool isLeastSplitBucket(const document::BucketId& bucket,const std::vector<BucketDatabase::Entry>& entries);
    static uint32_t getHighestUsedBits(const std::vector<BucketDatabase::Entry>& entries);
    static vespalib::string getReason(const document::BucketId& bucketId, const std::vector<BucketDatabase::Entry>& entries);
};

class ActiveList;

class BucketStateStateChecker : public StateChecker
{
    static bool shouldSkipActivationDueToMaintenance(const ActiveList& activeList, const Context& c);
public:
    Result check(Context& c) const override;
    const char* getName() const noexcept override { return "SetBucketState"; }
};

class GarbageCollectionStateChecker : public StateChecker
{
public:
    Result check(Context& c) const override;
    const char* getName() const noexcept override { return "GarbageCollection"; }
private:
    static bool garbage_collection_disabled(const Context& c) noexcept;
    static bool needs_garbage_collection(const Context& c, vespalib::duration time_since_epoch);
};

}
