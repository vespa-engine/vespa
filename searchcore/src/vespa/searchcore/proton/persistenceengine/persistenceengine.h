// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_iterator.h"
#include "i_resource_write_filter.h"
#include "persistence_handler_map.h"
#include "ipersistencehandler.h"
#include <vespa/document/bucket/bucketspace.h>
#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/searchcore/proton/common/handlermap.hpp>
#include <mutex>
#include <shared_mutex>
#include <unordered_map>

namespace proton {

class IPersistenceEngineOwner;

class PersistenceEngine : public storage::spi::AbstractPersistenceProvider {
private:
    using PersistenceHandlerSequence = vespalib::Sequence<IPersistenceHandler *>;
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
    using IterateResult = storage::spi::IterateResult;
    using IteratorId = storage::spi::IteratorId;
    using MaintenanceLevel = storage::spi::MaintenanceLevel;
    using PartitionId = storage::spi::PartitionId;
    using PartitionStateListResult = storage::spi::PartitionStateListResult;
    using RemoveResult = storage::spi::RemoveResult;
    using Result = storage::spi::Result;
    using Selection = storage::spi::Selection;
    using Timestamp = storage::spi::Timestamp;
    using TimestampList = storage::spi::TimestampList;
    using UpdateResult = storage::spi::UpdateResult;

    struct IteratorEntry {
        PersistenceHandlerSequence::UP handler_sequence;
        DocumentIterator it;
        bool in_use;
        std::vector<BucketGuard::UP> bucket_guards;
        IteratorEntry(storage::spi::ReadConsistency readConsistency, const Bucket &b, const document::FieldSet& f,
                      const Selection &s, IncludedVersions v, ssize_t defaultSerializedSize, bool ignoreMaxBytes)
            : handler_sequence(),
              it(b, f, s, v, defaultSerializedSize, ignoreMaxBytes, readConsistency),
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
    mutable std::shared_timed_mutex         _rwMutex;

    IPersistenceHandler::SP getHandler(document::BucketSpace bucketSpace, const DocTypeName &docType) const;
    HandlerSnapshot::UP getHandlerSnapshot() const;
    HandlerSnapshot::UP getHandlerSnapshot(document::BucketSpace bucketSpace) const;

    void saveClusterState(BucketSpace bucketSpace, const ClusterState &calc);
    ClusterState::SP savedClusterState(BucketSpace bucketSpace) const;

public:
    typedef std::unique_ptr<PersistenceEngine> UP;

    PersistenceEngine(IPersistenceEngineOwner &owner, const IResourceWriteFilter &writeFilter,
                      ssize_t defaultSerializedSize, bool ignoreMaxBytes);
    ~PersistenceEngine() override;

    IPersistenceHandler::SP putHandler(document::BucketSpace bucketSpace, const DocTypeName &docType,
                                       const IPersistenceHandler::SP &handler);
    IPersistenceHandler::SP removeHandler(document::BucketSpace bucketSpace, const DocTypeName &docType);

    // Implements PersistenceProvider
    Result initialize() override;
    PartitionStateListResult getPartitionStates() const override;
    BucketIdListResult listBuckets(BucketSpace bucketSpace, PartitionId) const override;
    Result setClusterState(BucketSpace bucketSpace, const ClusterState& calc) override;
    Result setActiveState(const Bucket& bucket, BucketInfo::ActiveState newState) override;
    BucketInfoResult getBucketInfo(const Bucket&) const override;
    Result put(const Bucket&, Timestamp, const std::shared_ptr<document::Document>&, Context&) override;
    RemoveResult remove(const Bucket&, Timestamp, const document::DocumentId&, Context&) override;
    UpdateResult update(const Bucket&, Timestamp,
                        const std::shared_ptr<document::DocumentUpdate>&, Context&) override;
    GetResult get(const Bucket&, const document::FieldSet&, const document::DocumentId&, Context&) const override;
    CreateIteratorResult createIterator(const Bucket&, const document::FieldSet&, const Selection&,
                                        IncludedVersions, Context&) override;
    IterateResult iterate(IteratorId, uint64_t maxByteSize, Context&) const override;
    Result destroyIterator(IteratorId, Context&) override;

    Result createBucket(const Bucket &bucketId, Context &) override ;
    Result deleteBucket(const Bucket&, Context&) override;
    BucketIdListResult getModifiedBuckets(BucketSpace bucketSpace) const override;
    Result split(const Bucket& source, const Bucket& target1, const Bucket& target2, Context&) override;
    Result join(const Bucket& source1, const Bucket& source2, const Bucket& target, Context&) override;

    Result maintain(const Bucket&, MaintenanceLevel) override;

    void destroyIterators();
    void propagateSavedClusterState(BucketSpace bucketSpace, IPersistenceHandler &handler);
    void grabExtraModifiedBuckets(BucketSpace bucketSpace, IPersistenceHandler &handler);
    void populateInitialBucketDB(BucketSpace bucketSpace, IPersistenceHandler &targetHandler);
    std::unique_lock<std::shared_timed_mutex> getWLock() const;
};

}
