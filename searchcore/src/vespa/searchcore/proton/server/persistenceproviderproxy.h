// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>

namespace proton {

using storage::spi::Bucket;
using storage::spi::BucketIdListResult;
using storage::spi::BucketInfoResult;
using storage::spi::ClusterState;
using storage::spi::Context;
using storage::spi::CreateIteratorResult;
using storage::spi::GetResult;
using storage::spi::IncludedVersions;
using storage::spi::IterateResult;
using storage::spi::IteratorId;
using storage::spi::PartitionId;
using storage::spi::PartitionStateListResult;
using storage::spi::RemoveResult;
using storage::spi::Result;
using storage::spi::Selection;
using storage::spi::Timestamp;
using storage::spi::UpdateResult;

class PersistenceProviderProxy : public storage::spi::PersistenceProvider
{
private:
    storage::spi::PersistenceProvider &_pp;

public:
    PersistenceProviderProxy(storage::spi::PersistenceProvider &pp);

    virtual ~PersistenceProviderProxy() {}

    virtual Result initialize() {
        return _pp.initialize();
    }

    // Implements PersistenceProvider
    virtual PartitionStateListResult getPartitionStates() const {
        return _pp.getPartitionStates();
    }

    virtual BucketIdListResult listBuckets(PartitionId partId) const {
        return _pp.listBuckets(partId);
    }

    virtual Result setClusterState(const ClusterState &state) {
        return _pp.setClusterState(state);
    }

    virtual Result setActiveState(const Bucket &bucket,
                                  storage::spi::BucketInfo::ActiveState newState) {
        return _pp.setActiveState(bucket, newState);
    }

    virtual BucketInfoResult getBucketInfo(const Bucket &bucket) const {
        return _pp.getBucketInfo(bucket);
    }

    virtual Result put(const Bucket &bucket,
                       Timestamp timestamp,
                       const storage::spi::DocumentSP& doc,
                       Context& context) {
        return _pp.put(bucket, timestamp, doc, context);
    }

    virtual RemoveResult remove(const Bucket &bucket,
                                Timestamp timestamp,
                                const document::DocumentId &docId,
                                Context& context) {
        return _pp.remove(bucket, timestamp, docId, context);
    }

    virtual RemoveResult removeIfFound(const Bucket &bucket,
                                       Timestamp timestamp,
                                       const document::DocumentId &docId,
                                       Context& context) {
        return _pp.removeIfFound(bucket, timestamp, docId, context);
    }

    virtual UpdateResult update(const Bucket &bucket,
                                Timestamp timestamp,
                                const storage::spi::DocumentUpdateSP& docUpd,
                                Context& context) {
        return _pp.update(bucket, timestamp, docUpd, context);
    }

    virtual Result flush(const Bucket &bucket, Context& context) {
        return _pp.flush(bucket, context);
    }

    virtual GetResult get(const Bucket &bucket,
                          const document::FieldSet& fieldSet,
                          const document::DocumentId &docId,
                          Context& context) const {
        return _pp.get(bucket, fieldSet, docId, context);
    }

    virtual CreateIteratorResult createIterator(const Bucket &bucket,
                                                const document::FieldSet& fieldSet,
                                                const Selection &selection,
                                                IncludedVersions versions,
                                                Context& context) {
        return _pp.createIterator(bucket, fieldSet, selection, versions,
                                  context);
    }

    virtual IterateResult iterate(IteratorId itrId,
                                  uint64_t maxByteSize,
                                  Context& context) const {
        return _pp.iterate(itrId, maxByteSize, context);
    }

    virtual Result destroyIterator(IteratorId itrId, Context& context) {
        return _pp.destroyIterator(itrId, context);
    }

    virtual Result createBucket(const Bucket &bucket, Context& context) {
        return _pp.createBucket(bucket, context);
    }

    virtual Result deleteBucket(const Bucket &bucket, Context& context) {
        return _pp.deleteBucket(bucket, context);
    }

    virtual BucketIdListResult getModifiedBuckets() const {
        return _pp.getModifiedBuckets();
    }

    virtual Result maintain(const Bucket &bucket,
                            storage::spi::MaintenanceLevel level) {
        return _pp.maintain(bucket, level);
    }

    virtual Result split(const Bucket &source,
                         const Bucket &target1,
                         const Bucket &target2,
                         Context& context) {
        return _pp.split(source, target1, target2, context);
    }

    virtual Result join(const Bucket &source1,
                        const Bucket &source2,
                        const Bucket &target,
                        Context& context) {
        return _pp.join(source1, source2, target, context);
    }

    virtual Result move(const Bucket &source,
                        storage::spi::PartitionId target,
                        Context& context) {
        return _pp.move(source, target, context);
    }

    virtual Result removeEntry(const Bucket &bucket,
                               Timestamp timestamp,
                               Context& context) {
        return _pp.removeEntry(bucket, timestamp, context);
    }
};

} // namespace proton

