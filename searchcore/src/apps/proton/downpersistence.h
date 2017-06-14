// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vespa/persistence/spi/persistenceprovider.h>

namespace storage::spi {

/*
 * Persistence provider that returns error code for all operations
 * except initialize(), getPartitionStates() and setClusterState().
 *
 * getPartitionStates() reports one partition, which is down with
 * reason "proton state string is " + stateString.
 *
 * This class is used when proton is supposed to be down except for
 * reporting state to cluster controller.
 */
class DownPersistence : public PersistenceProvider
{
    const vespalib::string _downReason;

public:
    DownPersistence(const vespalib::string &downReason);

    typedef std::unique_ptr<PersistenceProvider> UP;

    ~DownPersistence() override;

    Result initialize() override;
    PartitionStateListResult getPartitionStates() const override;
    BucketIdListResult listBuckets(PartitionId) const override;
    Result setClusterState(const ClusterState&) override;
    Result setActiveState(const Bucket&, BucketInfo::ActiveState) override;
    BucketInfoResult getBucketInfo(const Bucket&) const override;
    Result put(const Bucket&, Timestamp, const DocumentSP&, Context&) override;
    RemoveResult remove(const Bucket&, Timestamp timestamp, const DocumentId& id, Context&) override;
    RemoveResult removeIfFound(const Bucket&, Timestamp timestamp, const DocumentId& id, Context&) override;
    Result removeEntry(const Bucket&, Timestamp, Context&) override;
    UpdateResult update(const Bucket&, Timestamp timestamp, const DocumentUpdateSP& update, Context&) override;
    Result flush(const Bucket&, Context&) override;
    GetResult get(const Bucket&, const document::FieldSet& fieldSet, const DocumentId& id, Context&) const override;

    CreateIteratorResult createIterator(const Bucket&, const document::FieldSet& fieldSet,
                                        const Selection& selection, IncludedVersions versions, Context&) override;

    IterateResult iterate(IteratorId id, uint64_t maxByteSize, Context&) const override;
    Result destroyIterator(IteratorId id, Context&) override;
    Result createBucket(const Bucket&, Context&) override;
    Result deleteBucket(const Bucket&, Context&) override;
    BucketIdListResult getModifiedBuckets() const override;
    Result maintain(const Bucket&, MaintenanceLevel level) override;
    Result split(const Bucket& source, const Bucket& target1, const Bucket& target2, Context&) override;
    Result join(const Bucket& source1, const Bucket& source2, const Bucket& target, Context&) override;
    Result move(const Bucket&, PartitionId target, Context&) override;
};

}
