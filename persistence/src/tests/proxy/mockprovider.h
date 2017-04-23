// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>
#include <vespa/document/fieldvalue/document.h>

namespace storage {
namespace spi {

struct MockProvider : PersistenceProvider {
    enum Function { NONE, INITIALIZE, GET_PARTITION_STATES, LIST_BUCKETS,
                    SET_CLUSTER_STATE,
                    SET_ACTIVE_STATE, GET_BUCKET_INFO, PUT, REMOVE_BY_ID,
                    REMOVE_IF_FOUND, REPLACE_WITH_REMOVE, UPDATE, FLUSH, GET,
                    CREATE_ITERATOR, ITERATE, DESTROY_ITERATOR, CREATE_BUCKET,
                    DELETE_BUCKET, GET_MODIFIED_BUCKETS, SPLIT, JOIN, MOVE, MAINTAIN,
                    REMOVE_ENTRY };

    mutable Function last_called;

    MockProvider() : last_called(NONE) {}

    Result initialize() override {
        last_called = INITIALIZE;
        return Result();
    }

    PartitionStateListResult getPartitionStates() const override {
        last_called = GET_PARTITION_STATES;
        return PartitionStateListResult(PartitionStateList(1u));
    }

    BucketIdListResult listBuckets(PartitionId id) const override {
        last_called = LIST_BUCKETS;
        BucketIdListResult::List result;
        result.push_back(document::BucketId(id));
        return BucketIdListResult(result);
    }

    Result setClusterState(const ClusterState &) override {
        last_called = SET_CLUSTER_STATE;
        return Result();
    }

    Result setActiveState(const Bucket &, BucketInfo::ActiveState) override {
        last_called = SET_ACTIVE_STATE;
        return Result();
    }

    BucketInfoResult getBucketInfo(const Bucket &bucket) const override {
        last_called = GET_BUCKET_INFO;
        return BucketInfoResult(BucketInfo(BucketChecksum(1), 2, 3,
                                           bucket.getBucketId().getRawId(),
                                           bucket.getPartition(),
                                           BucketInfo::READY,
                                           BucketInfo::ACTIVE));
    }

    Result put(const Bucket &, Timestamp, const DocumentSP&, Context&) override {
        last_called = PUT;
        return Result();
    }

    RemoveResult remove(const Bucket &, Timestamp, const DocumentId &, Context&) override {
        last_called = REMOVE_BY_ID;
        return RemoveResult(true);
    }

    RemoveResult removeIfFound(const Bucket &, Timestamp, const DocumentId &, Context&)  override {
        last_called = REMOVE_IF_FOUND;
        return RemoveResult(true);
    }

    virtual RemoveResult replaceWithRemove(const Bucket &, Timestamp,
                                           const DocumentId &, Context&) {
        last_called = REPLACE_WITH_REMOVE;
        return RemoveResult(true);
    }

    UpdateResult update(const Bucket &, Timestamp timestamp, const DocumentUpdateSP&, Context&) override {
        last_called = UPDATE;
        return UpdateResult(Timestamp(timestamp - 10));
    }

    Result flush(const Bucket&, Context&)  override {
        last_called = FLUSH;
        return Result();
    }

    GetResult get(const Bucket &, const document::FieldSet&, const DocumentId&, Context&) const override {
        last_called = GET;
        return GetResult(Document::UP(new Document),
                         Timestamp(6u));
    }

     CreateIteratorResult createIterator(const Bucket& bucket,
                                         const document::FieldSet&,
                                         const Selection&,
                                         IncludedVersions,
                                         Context&) override
    {
        last_called = CREATE_ITERATOR;
        return CreateIteratorResult(IteratorId(bucket.getPartition()));
    }

    IterateResult iterate(IteratorId, uint64_t, Context&) const override {
        last_called = ITERATE;
        IterateResult::List result;
        result.push_back(DocEntry::UP(new DocEntry(Timestamp(1), 0)));
        return IterateResult(std::move(result), true);
    }

    Result destroyIterator(IteratorId, Context&) override {
        last_called = DESTROY_ITERATOR;
        return Result();
    }

    Result createBucket(const Bucket&, Context&) override {
        last_called = CREATE_BUCKET;
        return Result();
    }
    Result deleteBucket(const Bucket&, Context&) override {
        last_called = DELETE_BUCKET;
        return Result();
    }

    BucketIdListResult getModifiedBuckets() const override {
        last_called = GET_MODIFIED_BUCKETS;
        BucketIdListResult::List list;
        list.push_back(document::BucketId(2));
        list.push_back(document::BucketId(3));
        return BucketIdListResult(list);
    }

    Result split(const Bucket &, const Bucket &, const Bucket &, Context&) override {
        last_called = SPLIT;
        return Result();
    }

    Result join(const Bucket &, const Bucket &, const Bucket &, Context&) override {
        last_called = JOIN;
        return Result();
    }

    Result move(const Bucket &, PartitionId, Context&) override {
        last_called = MOVE;
        return Result();
    }


    Result maintain(const Bucket &, MaintenanceLevel) override {
        last_called = MAINTAIN;
        return Result();
    }

    Result removeEntry(const Bucket &, Timestamp, Context&) override {
        last_called = REMOVE_ENTRY;
        return Result();
    }
};

}  // namespace spi
}  // namespace storage
