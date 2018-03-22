// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "idealstatemanager.h"

namespace storage::distributor {

class SynchronizeAndMoveStateChecker : public StateChecker
{
public:
    std::string getStatusText() const override { return "Synchronization and moving"; }
    Result check(Context& c) override;
    const char* getName() const override { return "SynchronizeAndMove"; }
};

class DeleteExtraCopiesStateChecker : public StateChecker
{
public:
    std::string getStatusText() const override { return "Delete extra copies"; }
    Result check(Context& c) override;
    const char* getName() const override { return "DeleteExtraCopies"; }

private:
    bool bucketHasNoData(const StateChecker::Context& c);
    void removeRedundantEmptyOrConsistentCopies(
            StateChecker::Context& c,
            std::vector<uint16_t>& removedCopies,
            vespalib::asciistream& reasons);
    bool copyIsInIdealState(const BucketCopy& cp,
                            const StateChecker::Context& c) const;
    bool enoughCopiesKept(uint32_t keptIdealCopies,
                          uint32_t keptNonIdealCopies,
                          const StateChecker::Context& c) const;
    uint32_t numberOfIdealCopiesPresent(const StateChecker::Context& c) const;
    void addToRemoveSet(const BucketCopy& copyToRemove,
                        const char* reasonForRemoval,
                        std::vector<uint16_t>& removedCopies,
                        vespalib::asciistream& reasons);
                            
};

class JoinBucketsStateChecker : public StateChecker
{
public:
    std::string getStatusText() const override { return "Join buckets"; }
    Result check(Context& c) override;
    const char* getName() const override { return "JoinBuckets"; }
private:
    uint64_t getTotalUsedFileSize(const Context& c) const;
    uint64_t getTotalMetaCount(const Context& c) const;
    bool isFirstSibling(const document::BucketId& bucketId) const;
    bool siblingsAreInSync(const Context& c) const;
    bool shouldJoin(const Context& c) const;
    bool smallEnoughToJoin(const Context& c) const;
    bool singleBucketJoinIsEnabled(const Context&) const;
    bool singleBucketJoinIsConsistent(const Context& c) const;
    document::Bucket computeJoinBucket(const Context& c) const;
};

class SplitBucketStateChecker : public StateChecker
{
public:
    std::string getStatusText() const override { return "Split buckets"; }
    Result check(Context& c) override;
    const char* getName() const override { return "SplitBucket"; }
private:
    Result generateMinimumBucketSplitOperation(Context& c);
    Result generateMaxSizeExceededSplitOperation(Context& c);

    bool validForSplit(StateChecker::Context& c);
    double getBucketSizeRelativeToMax(Context& c);
};

class SplitInconsistentStateChecker : public StateChecker
{
public:
    std::string getStatusText() const override { return "Fix inconsistently split buckets"; }
    Result check(Context& c) override;
    const char* getName() const override { return "SplitInconsistentBuckets"; }

private:
    typedef std::pair<document::BucketId, uint16_t> BucketAndNode;
    bool isLeastSplitBucket(
            const document::BucketId& bucket,
            const std::vector<BucketDatabase::Entry>& entries) const;
    uint32_t getHighestUsedBits(
            const std::vector<BucketDatabase::Entry>& entries) const;
    vespalib::string getReason(
            const document::BucketId& bucketId,
            const std::vector<BucketDatabase::Entry>& entries) const;
    bool isLeastSplit(Context& c, std::vector<BucketAndNode>& others);
};

class ActiveList;

class BucketStateStateChecker : public StateChecker
{
    bool shouldSkipActivationDueToMaintenance(
            const ActiveList& activeList,
            const StateChecker::Context& c) const;
public:
    std::string getStatusText() const override { return "Set bucket copy state"; }
    Result check(Context& c) override;
    const char* getName() const override { return "SetBucketState"; }
};

class GarbageCollectionStateChecker : public StateChecker
{
public:
    std::string getStatusText() const override { return "Garbage collection"; }
    bool needsGarbageCollection(const Context& c) const;
    Result check(Context& c) override;
    const char* getName() const override { return "GarbageCollection"; }
};

}
