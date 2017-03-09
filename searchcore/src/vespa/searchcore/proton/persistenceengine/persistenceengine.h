// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "document_iterator.h"
#include "i_resource_write_filter.h"
#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/searchcore/proton/common/handlermap.hpp>
#include <vespa/searchcore/proton/persistenceengine/ipersistencehandler.h>
#include <vespa/vespalib/util/sync.h>
#include <mutex>
#include <shared_mutex>

namespace proton {

using document::DocumentUpdate;
using storage::spi::Bucket;
using storage::spi::BucketIdListResult;
using storage::spi::BucketInfo;
using storage::spi::BucketInfoResult;
using storage::spi::ClusterState;
using storage::spi::Context;
using storage::spi::CreateIteratorResult;
using storage::spi::GetResult;
using storage::spi::IncludedVersions;
using storage::spi::IterateResult;
using storage::spi::IteratorId;
using storage::spi::MaintenanceLevel;
using storage::spi::PartitionId;
using storage::spi::PartitionStateListResult;
using storage::spi::RemoveResult;
using storage::spi::Result;
using storage::spi::Selection;
using storage::spi::Timestamp;
using storage::spi::TimestampList;
using storage::spi::UpdateResult;

class IPersistenceEngineOwner;

class PersistenceEngine : public storage::spi::AbstractPersistenceProvider {
private:
    typedef vespalib::Sequence<IPersistenceHandler *> PersistenceHandlerSequence;

    class HandlerSnapshot {
    private:
        PersistenceHandlerSequence::UP _handlers;
        size_t                         _size;
    public:
        typedef std::unique_ptr<HandlerSnapshot> UP;
        HandlerSnapshot(PersistenceHandlerSequence::UP handlers_, size_t size_) :
            _handlers(handlers_.release()),
            _size(size_)
        {}
        HandlerSnapshot(const HandlerSnapshot &) = delete;
        HandlerSnapshot & operator = (const HandlerSnapshot &) = delete;

        size_t size() const { return _size; }
        PersistenceHandlerSequence &handlers() { return *_handlers; }
        static PersistenceHandlerSequence::UP release(HandlerSnapshot && rhs) { return std::move(rhs._handlers); }
    };

    struct IteratorEntry {
        PersistenceHandlerSequence::UP handler_sequence;
        DocumentIterator it;
        bool in_use;
        std::vector<BucketGuard::UP> bucket_guards;
        IteratorEntry(storage::spi::ReadConsistency readConsistency,
                      const Bucket &b,
                      const document::FieldSet& f,
                      const Selection &s,
                      IncludedVersions v,
                      ssize_t defaultSerializedSize,
                      bool ignoreMaxBytes)
            : handler_sequence(),
              it(b, f, s, v, defaultSerializedSize, ignoreMaxBytes, readConsistency),
              in_use(false),
              bucket_guards() {}
    };
    typedef std::map<IteratorId, IteratorEntry *> Iterators;
    typedef std::vector<std::shared_ptr<BucketIdListResult> > BucketIdListResultV;

    const ssize_t                           _defaultSerializedSize;
    const bool                              _ignoreMaxBytes;
    mutable HandlerMap<IPersistenceHandler> _handlers;
    vespalib::Lock                          _lock;
    Iterators                               _iterators;
    vespalib::Lock                          _iterators_lock;
    IPersistenceEngineOwner                &_owner;
    const IResourceWriteFilter             &_writeFilter;
    ClusterState::SP                        _clusterState;
    mutable BucketIdListResultV             _extraModifiedBuckets;
    mutable std::shared_timed_mutex         _rwMutex;

    HandlerSnapshot::UP getHandlerSnapshot() const;
    HandlerSnapshot::UP getHandlerSnapshot(const document::DocumentId &) const;

    void saveClusterState(const ClusterState &calc);
    ClusterState::SP savedClusterState(void) const;

public:
    typedef std::unique_ptr<PersistenceEngine> UP;

    PersistenceEngine(IPersistenceEngineOwner &owner,
                      const IResourceWriteFilter &writeFilter,
                      ssize_t defaultSerializedSize, bool ignoreMaxBytes);
    ~PersistenceEngine();

    IPersistenceHandler::SP putHandler(const DocTypeName &docType, const IPersistenceHandler::SP &handler);
    IPersistenceHandler::SP getHandler(const DocTypeName &docType) const;
    IPersistenceHandler::SP removeHandler(const DocTypeName &docType);

    // Implements PersistenceProvider
    virtual Result initialize() override;
    virtual PartitionStateListResult getPartitionStates() const override;
    virtual BucketIdListResult listBuckets(PartitionId) const override;
    virtual Result setClusterState(const ClusterState& calc) override;
    virtual Result setActiveState(const Bucket& bucket, BucketInfo::ActiveState newState) override;
    virtual BucketInfoResult getBucketInfo(const Bucket&) const;
    virtual Result put(const Bucket&, Timestamp, const document::Document::SP&, Context&) override;
    virtual RemoveResult remove(const Bucket&, Timestamp, const document::DocumentId&, Context&) override;
    virtual UpdateResult update(const Bucket&, Timestamp, const document::DocumentUpdate::SP&, Context&) override;
    virtual GetResult get(const Bucket&, const document::FieldSet&, const document::DocumentId&, Context&) const override;
    virtual CreateIteratorResult createIterator(const Bucket&, const document::FieldSet&, const Selection&,
                                                IncludedVersions, Context&) override;
    virtual IterateResult iterate(IteratorId, uint64_t maxByteSize, Context&) const override;
    virtual Result destroyIterator(IteratorId, Context&) override;

    virtual Result createBucket(const Bucket &bucketId, Context &) override ;
    virtual Result deleteBucket(const Bucket&, Context&) override;
    virtual BucketIdListResult getModifiedBuckets() const override;
    virtual Result split(const Bucket& source, const Bucket& target1, const Bucket& target2, Context&) override;
    virtual Result join(const Bucket& source1, const Bucket& source2, const Bucket& target, Context&) override;

    virtual Result maintain(const Bucket&, MaintenanceLevel) override;

    void destroyIterators();
    void propagateSavedClusterState(IPersistenceHandler &handler);
    void grabExtraModifiedBuckets(IPersistenceHandler &handler);
    void populateInitialBucketDB(IPersistenceHandler &targetHandler);
    std::unique_lock<std::shared_timed_mutex> getWLock(void) const;
};

}
