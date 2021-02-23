// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_iterator.h"
#include "i_resource_write_filter.h"
#include "persistence_handler_map.h"
#include "ipersistencehandler.h"
#include "resource_usage_tracker.h"
#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/persistence/spi/bucketexecutor.h>
#include <mutex>
#include <shared_mutex>

namespace proton {

class IPersistenceEngineOwner;
class IDiskMemUsageNotifier;

class PersistenceEngine : public storage::spi::AbstractPersistenceProvider,
                          public storage::spi::BucketExecutor {
private:
    using PersistenceHandlerSequence = PersistenceHandlerMap::PersistenceHandlerSequence;
    using HandlerSnapshot = PersistenceHandlerMap::HandlerSnapshot;
    using DocumentUpdate = document::DocumentUpdate;
    using Bucket = storage::spi::Bucket;
    using BucketIdListResult = storage::spi::BucketIdListResult;
    using BucketInfo = storage::spi::BucketInfo;
    using BucketInfoResult = storage::spi::BucketInfoResult;
    using ClusterState = storage::spi::ClusterState;
    using Context = storage::spi::Context;
    using CreateIteratorResult = storage::spi::CreateIteratorResult;
    using GetResult = storage::spi::GetResult;
    using IncludedVersions = storage::spi::IncludedVersions;
    using IResourceUsageListener = storage::spi::IResourceUsageListener;
    using IterateResult = storage::spi::IterateResult;
    using IteratorId = storage::spi::IteratorId;
    using RemoveResult = storage::spi::RemoveResult;
    using Result = storage::spi::Result;
    using Selection = storage::spi::Selection;
    using Timestamp = storage::spi::Timestamp;
    using TimestampList = storage::spi::TimestampList;
    using UpdateResult = storage::spi::UpdateResult;
    using OperationComplete = storage::spi::OperationComplete;
    using BucketExecutor = storage::spi::BucketExecutor;
    using BucketTask = storage::spi::BucketTask;

    struct IteratorEntry {
        PersistenceHandlerSequence  handler_sequence;
        DocumentIterator it;
        bool in_use;
        std::vector<BucketGuard::UP> bucket_guards;
        IteratorEntry(storage::spi::ReadConsistency readConsistency, const Bucket &b, FieldSetSP f,
                      const Selection &s, IncludedVersions v, ssize_t defaultSerializedSize, bool ignoreMaxBytes)
            : handler_sequence(),
              it(b, std::move(f), s, v, defaultSerializedSize, ignoreMaxBytes, readConsistency),
              in_use(false),
              bucket_guards() {}
    };
    struct BucketSpaceHash {
        std::size_t operator() (const document::BucketSpace &bucketSpace) const { return bucketSpace.getId(); }
    };

    typedef std::map<IteratorId, IteratorEntry *> Iterators;
    typedef std::vector<std::shared_ptr<BucketIdListResult> > BucketIdListResultV;
    using ExtraModifiedBuckets = std::unordered_map<BucketSpace, BucketIdListResultV, BucketSpaceHash>;


    const ssize_t                           _defaultSerializedSize;
    const bool                              _ignoreMaxBytes;
    PersistenceHandlerMap                   _handlers;
    mutable std::mutex                      _lock;
    Iterators                               _iterators;
    mutable std::mutex                      _iterators_lock;
    IPersistenceEngineOwner                &_owner;
    const IResourceWriteFilter             &_writeFilter;
    std::unordered_map<BucketSpace, ClusterState::SP, BucketSpace::hash> _clusterStates;
    mutable ExtraModifiedBuckets            _extraModifiedBuckets;
    mutable std::shared_mutex               _rwMutex;
    std::shared_ptr<ResourceUsageTracker>   _resource_usage_tracker;
    std::weak_ptr<BucketExecutor>           _bucket_executor;

    using ReadGuard = std::shared_lock<std::shared_mutex>;
    using WriteGuard = std::unique_lock<std::shared_mutex>;

    IPersistenceHandler * getHandler(const ReadGuard & guard, document::BucketSpace bucketSpace, const DocTypeName &docType) const;
    HandlerSnapshot getHandlerSnapshot(const WriteGuard & guard) const;
    HandlerSnapshot getHandlerSnapshot(const ReadGuard & guard, document::BucketSpace bucketSpace) const;
    HandlerSnapshot getHandlerSnapshot(const WriteGuard & guard, document::BucketSpace bucketSpace) const;

    void saveClusterState(BucketSpace bucketSpace, const ClusterState &calc);
    ClusterState::SP savedClusterState(BucketSpace bucketSpace) const;
    std::shared_ptr<BucketExecutor> get_bucket_executor() noexcept { return _bucket_executor.lock(); }

public:
    typedef std::unique_ptr<PersistenceEngine> UP;

    PersistenceEngine(IPersistenceEngineOwner &owner, const IResourceWriteFilter &writeFilter, IDiskMemUsageNotifier &disk_mem_usage_notifier,
                      ssize_t defaultSerializedSize, bool ignoreMaxBytes);
    ~PersistenceEngine() override;

    IPersistenceHandler::SP putHandler(const WriteGuard &, document::BucketSpace bucketSpace, const DocTypeName &docType, const IPersistenceHandler::SP &handler);
    IPersistenceHandler::SP removeHandler(const WriteGuard &, document::BucketSpace bucketSpace, const DocTypeName &docType);

    // Implements PersistenceProvider
    Result initialize() override;
    BucketIdListResult listBuckets(BucketSpace bucketSpace) const override;
    Result setClusterState(BucketSpace bucketSpace, const ClusterState& calc) override;
    Result setActiveState(const Bucket& bucket, BucketInfo::ActiveState newState) override;
    BucketInfoResult getBucketInfo(const Bucket&) const override;
    void putAsync(const Bucket &, Timestamp, storage::spi::DocumentSP, Context &context, OperationComplete::UP) override;
    void removeAsync(const Bucket&, Timestamp, const document::DocumentId&, Context&, OperationComplete::UP) override;
    void updateAsync(const Bucket&, Timestamp, storage::spi::DocumentUpdateSP, Context&, OperationComplete::UP) override;
    GetResult get(const Bucket&, const document::FieldSet&, const document::DocumentId&, Context&) const override;
    CreateIteratorResult
    createIterator(const Bucket &bucket, FieldSetSP, const Selection &, IncludedVersions, Context &context) override;
    IterateResult iterate(IteratorId, uint64_t maxByteSize, Context&) const override;
    Result destroyIterator(IteratorId, Context&) override;

    Result createBucket(const Bucket &bucketId, Context &) override ;
    Result deleteBucket(const Bucket&, Context&) override;
    BucketIdListResult getModifiedBuckets(BucketSpace bucketSpace) const override;
    Result split(const Bucket& source, const Bucket& target1, const Bucket& target2, Context&) override;
    Result join(const Bucket& source1, const Bucket& source2, const Bucket& target, Context&) override;
    std::unique_ptr<vespalib::IDestructorCallback> register_resource_usage_listener(IResourceUsageListener& listener) override;
    std::unique_ptr<vespalib::IDestructorCallback> register_executor(std::shared_ptr<BucketExecutor>) override;
    void destroyIterators();
    void propagateSavedClusterState(BucketSpace bucketSpace, IPersistenceHandler &handler);
    void grabExtraModifiedBuckets(BucketSpace bucketSpace, IPersistenceHandler &handler);
    void populateInitialBucketDB(const WriteGuard & guard, BucketSpace bucketSpace, IPersistenceHandler &targetHandler);
    WriteGuard getWLock() const;
    ResourceUsageTracker &get_resource_usage_tracker() noexcept { return *_resource_usage_tracker; }
    void execute(const Bucket &bucket, std::unique_ptr<BucketTask> task) override;
};

}
