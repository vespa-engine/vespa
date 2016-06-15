// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::dummy::DummyPersistence
 * \ingroup dummy
 *
 * \brief Simple implementation of the persistence SPI.
 */

#pragma once

#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/stllike/hash_map.h>

namespace document {

class FieldSet;

namespace select {

class Node;

}
}

namespace storage {
namespace spi {
namespace dummy {

struct BucketEntry
{
    DocEntry::LP entry;
    GlobalId gid;

    BucketEntry(const DocEntry::LP& e, const GlobalId& g)
        : entry(e),
          gid(g)
    {
    }
};

struct BucketContent {
    typedef vespalib::hash_map<
        document::GlobalId,
        DocEntry::LP,
        document::GlobalId::hash
    > GidMapType;

    typedef vespalib::LinkedPtr<BucketContent> LP;

    std::vector<BucketEntry> _entries;
    GidMapType _gidMap;
    mutable BucketInfo _info;
    mutable uint32_t _inUse;
    mutable bool _outdatedInfo;
    bool _active;

    BucketContent()
        : _entries(),
          _gidMap(),
          _info(),
          _inUse(false),
          _outdatedInfo(true),
          _active(false)
    {
    }

    uint32_t computeEntryChecksum(const BucketEntry&) const;
    BucketChecksum updateRollingChecksum(uint32_t entryChecksum);

    /**
     * Get bucket info, potentially recomputing it if it's outdated. In the
     * latter case, the cached bucket info will be updated.
     */
    const BucketInfo& getBucketInfo() const;
    BucketInfo& getMutableBucketInfo() { return _info; }
    bool hasTimestamp(Timestamp) const;
    void insert(DocEntry::LP);
    DocEntry::LP getEntry(const DocumentId&) const;
    DocEntry::LP getEntry(Timestamp) const;
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
    typedef vespalib::LinkedPtr<Iterator> LP;
    Bucket _bucket;
    std::vector<Timestamp> _leftToIterate;
    vespalib::LinkedPtr<document::FieldSet> _fieldSet;
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
    DummyPersistence(const document::DocumentTypeRepo::SP& repo,
                     uint16_t partitionCount = 1);

    PartitionStateListResult getPartitionStates() const;
    BucketIdListResult listBuckets(PartitionId) const;

    void setModifiedBuckets(const BucketIdListResult::List& result);

    /**
     * Returns the list set by setModifiedBuckets(), then clears
     * the list.
     */
    BucketIdListResult getModifiedBuckets() const;

    Result setClusterState(const ClusterState& newState);

    Result setActiveState(const Bucket& bucket,
                           BucketInfo::ActiveState newState);

    BucketInfoResult getBucketInfo(const Bucket&) const;

    Result put(const Bucket&, Timestamp, const Document::SP&, Context&);
    GetResult get(const Bucket&,
                  const document::FieldSet& fieldSet,
                  const DocumentId&,
                  Context&) const;

    RemoveResult remove(const Bucket& b,
                        Timestamp t,
                        const DocumentId& did,
                        Context&);

    CreateIteratorResult createIterator(const Bucket&,
                                        const document::FieldSet& fs,
                                        const Selection&,
                                        IncludedVersions,
                                        Context&);

    IterateResult iterate(IteratorId, uint64_t maxByteSize, Context&) const;
    Result destroyIterator(IteratorId, Context&);

    Result createBucket(const Bucket&, Context&);
    Result deleteBucket(const Bucket&, Context&);

    Result split(const Bucket& source,
                 const Bucket& target1,
                 const Bucket& target2,
                 Context&);

    Result join(const Bucket& source1,
                const Bucket& source2,
                const Bucket& target,
                Context&);

    Result revert(const Bucket&, Timestamp, Context&);

    Result maintain(const Bucket& bucket,
                    MaintenanceLevel level);

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
    document::DocumentTypeRepo::SP _repo;
    PartitionStateList _partitions;
    typedef vespalib::hash_map<Bucket, BucketContent::LP, document::BucketId::hash>
    PartitionContent;

    std::vector<PartitionContent> _content;
    IteratorId _nextIterator;
    mutable std::map<IteratorId, Iterator::LP> _iterators;
    vespalib::Monitor _monitor;

    std::unique_ptr<ClusterState> _clusterState;

    bool _simulateMaintainFailure;

    document::select::Node::UP parseDocumentSelection(
            const string& documentSelection,
            bool allowLeaf);

    mutable BucketIdListResult::List _modifiedBuckets;
};

} // dummy
} // spi
} // storage

