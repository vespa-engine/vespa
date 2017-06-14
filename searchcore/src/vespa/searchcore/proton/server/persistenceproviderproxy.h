// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>

namespace proton {

class PersistenceProviderProxy : public storage::spi::PersistenceProvider
{
private:
    using Bucket = storage::spi::Bucket;
    using BucketIdListResult = storage::spi::BucketIdListResult;
    using BucketInfoResult = storage::spi::BucketInfoResult;
    using ClusterState = storage::spi::ClusterState;
    using Context = storage::spi::Context;
    using CreateIteratorResult = storage::spi::CreateIteratorResult;
    using GetResult = storage::spi::GetResult;
    using IncludedVersions = storage::spi::IncludedVersions;
    using IterateResult = storage::spi::IterateResult;
    using IteratorId = storage::spi::IteratorId;
    using PartitionId = storage::spi::PartitionId;
    using PartitionStateListResult = storage::spi::PartitionStateListResult;
    using RemoveResult = storage::spi::RemoveResult;
    using Result = storage::spi::Result;
    using Selection = storage::spi::Selection;
    using Timestamp = storage::spi::Timestamp;
    using UpdateResult = storage::spi::UpdateResult;

    storage::spi::PersistenceProvider &_pp;

public:
    PersistenceProviderProxy(storage::spi::PersistenceProvider &pp);

    virtual ~PersistenceProviderProxy() {}

    virtual Result initialize() override {
        return _pp.initialize();
    }

    // Implements PersistenceProvider
    virtual PartitionStateListResult getPartitionStates() const override {
        return _pp.getPartitionStates();
    }

    virtual BucketIdListResult listBuckets(PartitionId partId) const override {
        return _pp.listBuckets(partId);
    }

    virtual Result setClusterState(const ClusterState &state) override {
        return _pp.setClusterState(state);
    }

    virtual Result setActiveState(const Bucket &bucket,
                                  storage::spi::BucketInfo::ActiveState newState) override {
        return _pp.setActiveState(bucket, newState);
    }

    virtual BucketInfoResult getBucketInfo(const Bucket &bucket) const override {
        return _pp.getBucketInfo(bucket);
    }

    virtual Result put(const Bucket &bucket,
                       Timestamp timestamp,
                       const storage::spi::DocumentSP& doc,
                       Context& context) override {
        return _pp.put(bucket, timestamp, doc, context);
    }

    virtual RemoveResult remove(const Bucket &bucket,
                                Timestamp timestamp,
                                const document::DocumentId &docId,
                                Context& context) override {
        return _pp.remove(bucket, timestamp, docId, context);
    }

    virtual RemoveResult removeIfFound(const Bucket &bucket,
                                       Timestamp timestamp,
                                       const document::DocumentId &docId,
                                       Context& context) override {
        return _pp.removeIfFound(bucket, timestamp, docId, context);
    }

    virtual UpdateResult update(const Bucket &bucket,
                                Timestamp timestamp,
                                const storage::spi::DocumentUpdateSP& docUpd,
                                Context& context) override {
        return _pp.update(bucket, timestamp, docUpd, context);
    }

    virtual Result flush(const Bucket &bucket, Context& context) override {
        return _pp.flush(bucket, context);
    }

    virtual GetResult get(const Bucket &bucket,
                          const document::FieldSet& fieldSet,
                          const document::DocumentId &docId,
                          Context& context) const override {
        return _pp.get(bucket, fieldSet, docId, context);
    }

    virtual CreateIteratorResult createIterator(const Bucket &bucket,
                                                const document::FieldSet& fieldSet,
                                                const Selection &selection,
                                                IncludedVersions versions,
                                                Context& context) override {
        return _pp.createIterator(bucket, fieldSet, selection, versions,
                                  context);
    }

    virtual IterateResult iterate(IteratorId itrId,
                                  uint64_t maxByteSize,
                                  Context& context) const override {
        return _pp.iterate(itrId, maxByteSize, context);
    }

    virtual Result destroyIterator(IteratorId itrId, Context& context) override {
        return _pp.destroyIterator(itrId, context);
    }

    virtual Result createBucket(const Bucket &bucket, Context& context) override {
        return _pp.createBucket(bucket, context);
    }

    virtual Result deleteBucket(const Bucket &bucket, Context& context) override {
        return _pp.deleteBucket(bucket, context);
    }

    virtual BucketIdListResult getModifiedBuckets() const override {
        return _pp.getModifiedBuckets();
    }

    virtual Result maintain(const Bucket &bucket,
                            storage::spi::MaintenanceLevel level) override {
        return _pp.maintain(bucket, level);
    }

    virtual Result split(const Bucket &source,
                         const Bucket &target1,
                         const Bucket &target2,
                         Context& context) override {
        return _pp.split(source, target1, target2, context);
    }

    virtual Result join(const Bucket &source1,
                        const Bucket &source2,
                        const Bucket &target,
                        Context& context) override {
        return _pp.join(source1, source2, target, context);
    }

    virtual Result move(const Bucket &source,
                        storage::spi::PartitionId target,
                        Context& context) override {
        return _pp.move(source, target, context);
    }

    virtual Result removeEntry(const Bucket &bucket,
                               Timestamp timestamp,
                               Context& context) override {
        return _pp.removeEntry(bucket, timestamp, context);
    }
};

} // namespace proton

