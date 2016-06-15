// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

namespace storage
{

namespace spi
{

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

    virtual ~DownPersistence();

    virtual Result initialize() override;

    virtual PartitionStateListResult getPartitionStates() const override;

    virtual BucketIdListResult listBuckets(PartitionId) const override;

    virtual Result setClusterState(const ClusterState&) override;

    virtual Result setActiveState(const Bucket&,
                                  BucketInfo::ActiveState) override;

    virtual BucketInfoResult getBucketInfo(const Bucket&) const override;

    virtual Result put(const Bucket&, Timestamp, const Document::SP&, Context&) override;

    virtual RemoveResult remove(const Bucket&,
                                Timestamp timestamp,
                                const DocumentId& id,
                                Context&) override;

    virtual RemoveResult removeIfFound(const Bucket&,
                                       Timestamp timestamp,
                                       const DocumentId& id,
                                       Context&) override;

    virtual Result removeEntry(const Bucket&, Timestamp, Context&) override;

    virtual UpdateResult update(const Bucket&,
                                Timestamp timestamp,
                                const DocumentUpdate::SP& update,
                                Context&) override;

    virtual Result flush(const Bucket&, Context&) override;

    virtual GetResult get(const Bucket&,
                          const document::FieldSet& fieldSet,
                          const DocumentId& id,
                          Context&) const override;

    virtual CreateIteratorResult createIterator(
            const Bucket&,
            const document::FieldSet& fieldSet,
            const Selection& selection, //TODO: Make AST
            IncludedVersions versions,
            Context&) override;

    virtual IterateResult iterate(IteratorId id,
                                  uint64_t maxByteSize,
                                  Context&) const override;

    virtual Result destroyIterator(IteratorId id, Context&) override;

    virtual Result createBucket(const Bucket&, Context&) override;

    virtual Result deleteBucket(const Bucket&, Context&) override;

    virtual BucketIdListResult getModifiedBuckets() const override;

    virtual Result maintain(const Bucket&,
                            MaintenanceLevel level) override;

    virtual Result split(const Bucket& source,
                         const Bucket& target1,
                         const Bucket& target2,
                         Context&) override;

    virtual Result join(const Bucket& source1,
                        const Bucket& source2,
                        const Bucket& target,
                        Context&) override;

    virtual Result move(const Bucket&, PartitionId target, Context&) override;

};

}

}
