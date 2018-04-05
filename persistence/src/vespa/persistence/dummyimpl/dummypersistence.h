// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::dummy::DummyPersistence
 * \ingroup dummy
 *
 * \brief Simple implementation of the persistence SPI.
 */

#pragma once

#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/document/base/globalid.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <atomic>
#include <map>

namespace document {
class DocumentTypeRepo;
    class FieldSet;
    namespace select { class Node; }
}

namespace storage::spi::dummy {

struct BucketEntry
{
    DocEntry::SP entry;
    GlobalId gid;

    BucketEntry(DocEntry::SP e, const GlobalId& g)
        : entry(e),
          gid(g)
    { }
};

struct BucketContent {
    typedef vespalib::hash_map<
        document::GlobalId,
        DocEntry::SP,
        document::GlobalId::hash
    > GidMapType;

    using SP = std::shared_ptr<BucketContent>;

    std::vector<BucketEntry> _entries;
    GidMapType _gidMap;
    mutable BucketInfo _info;
    mutable std::atomic<bool> _inUse;
    mutable bool _outdatedInfo;
    bool _active;

    BucketContent();
    ~BucketContent();


    uint32_t computeEntryChecksum(const BucketEntry&) const;
    BucketChecksum updateRollingChecksum(uint32_t entryChecksum);

    /**
     * Get bucket info, potentially recomputing it if it's outdated. In the
     * latter case, the cached bucket info will be updated.
     */
    const BucketInfo& getBucketInfo() const;
    BucketInfo& getMutableBucketInfo() { return _info; }
    bool hasTimestamp(Timestamp) const;
    void insert(DocEntry::SP);
    DocEntry::SP getEntry(const DocumentId&) const;
    DocEntry::SP getEntry(Timestamp) const;
    void eraseEntry(Timestamp t);
    void setActive(bool active = true) {
        _active = active;
        _info = BucketInfo(_info.getChecksum(),
                           _info.getDocumentCount(),
                           _info.getDocumentSize(),
                           _info.getEntryCount(),
                           _info.getUsedSize(),
                           _info.getReady(),
                           active ? BucketInfo::ACTIVE : BucketInfo::NOT_ACTIVE);
    }
    bool isActive() const { return _active; }
    void setOutdatedInfo(bool outdated) { _outdatedInfo = outdated; }
    bool hasOutdatedInfo() const { return _outdatedInfo; }
};

struct Iterator {
    using UP = std::unique_ptr<Iterator>;
    Bucket _bucket;
    std::vector<Timestamp> _leftToIterate;
    std::unique_ptr<document::FieldSet> _fieldSet;
};

class DummyPersistence;

class BucketContentGuard
{
    BucketContentGuard(const BucketContentGuard&);
    BucketContentGuard& operator=(const BucketContentGuard&);
public:
    typedef std::unique_ptr<BucketContentGuard> UP;

    BucketContentGuard(DummyPersistence& persistence,
                       BucketContent& content)
        : _persistence(persistence),
          _content(content)
    {
    }
    ~BucketContentGuard();

    BucketContent& getContent() {
        return _content;
    }

    BucketContent* operator->() {
        return &_content;
    }

    BucketContent& operator*() {
        return _content;
    }
private:
    DummyPersistence& _persistence;
    BucketContent& _content;
};

class DummyPersistence : public AbstractPersistenceProvider
{
public:
    DummyPersistence(const std::shared_ptr<const document::DocumentTypeRepo>& repo,
                     uint16_t partitionCount = 1);
    ~DummyPersistence();

    PartitionStateListResult getPartitionStates() const override;
    BucketIdListResult listBuckets(BucketSpace bucketSpace, PartitionId) const override;

    void setModifiedBuckets(const BucketIdListResult::List& result);

    /**
     * Returns the list set by setModifiedBuckets(), then clears
     * the list.
     */
    BucketIdListResult getModifiedBuckets(BucketSpace bucketSpace) const override;

    Result setClusterState(BucketSpace bucketSpace, const ClusterState& newState) override;
    Result setActiveState(const Bucket& bucket, BucketInfo::ActiveState newState) override;
    BucketInfoResult getBucketInfo(const Bucket&) const override;
    Result put(const Bucket&, Timestamp, const DocumentSP&, Context&) override;
    GetResult get(const Bucket&,
                  const document::FieldSet& fieldSet,
                  const DocumentId&,
                  Context&) const override;

    RemoveResult remove(const Bucket& b,
                        Timestamp t,
                        const DocumentId& did,
                        Context&) override;

    CreateIteratorResult createIterator(const Bucket&,
                                        const document::FieldSet& fs,
                                        const Selection&,
                                        IncludedVersions,
                                        Context&) override;

    IterateResult iterate(IteratorId, uint64_t maxByteSize, Context&) const override;
    Result destroyIterator(IteratorId, Context&) override;

    Result createBucket(const Bucket&, Context&) override;
    Result deleteBucket(const Bucket&, Context&) override;

    Result split(const Bucket& source,
                 const Bucket& target1,
                 const Bucket& target2,
                 Context&) override;

    Result join(const Bucket& source1,
                const Bucket& source2,
                const Bucket& target,
                Context&) override;

    Result revert(const Bucket&, Timestamp, Context&);
    Result maintain(const Bucket& bucket, MaintenanceLevel level) override;

    /**
     * The following methods are used only for unit testing.
     * DummyPersistence is used many places to test the framework around it.
     */

    /*
     * Dumps the contents of a bucket to a string and returns it.
     */
    std::string dumpBucket(const Bucket&) const;

    /**
     * Returns true if the given bucket has been tagged as active.
     */
    bool isActive(const Bucket&) const;

    const ClusterState& getClusterState() const {
        return *_clusterState;
    }

    void simulateMaintenanceFailure() {
        _simulateMaintainFailure = true;
    }

private:
    friend class BucketContentGuard;
    // Const since funcs only alter mutable field in BucketContent
    BucketContentGuard::UP acquireBucketWithLock(const Bucket& b) const;
    void releaseBucketNoLock(const BucketContent& bc) const;

    mutable bool _initialized;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    PartitionStateList _partitions;
    typedef vespalib::hash_map<Bucket, BucketContent::SP, document::BucketId::hash>
    PartitionContent;

    std::vector<PartitionContent> _content;
    IteratorId _nextIterator;
    mutable std::map<IteratorId, Iterator::UP> _iterators;
    vespalib::Monitor _monitor;

    std::unique_ptr<ClusterState> _clusterState;

    bool _simulateMaintainFailure;

    std::unique_ptr<document::select::Node> parseDocumentSelection(
            const string& documentSelection,
            bool allowLeaf);

    mutable BucketIdListResult::List _modifiedBuckets;
};

}
