// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * \class storage::spi::dummy::DummyPersistence
 * \ingroup dummy
 *
 * \brief Simple implementation of the persistence SPI.
 */

#pragma once

#include <vespa/persistence/spi/abstractpersistenceprovider.h>
#include <vespa/persistence/spi/docentry.h>
#include <vespa/document/base/globalid.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/vespalib/stllike/hash_map.h>
#include <atomic>
#include <map>
#include <mutex>
#include <condition_variable>

namespace document {
class DocumentTypeRepo;
    class FieldSet;
    namespace select { class Node; }
}

namespace storage::spi::dummy {

enum class LockMode {
    Exclusive,
    Shared
};

struct BucketEntry
{
    DocEntry::SP entry;
    GlobalId gid;

    BucketEntry(DocEntry::SP e, const GlobalId& g)
        : entry(std::move(e)),
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
    std::shared_ptr<document::FieldSet> _fieldSet;
};

class DummyPersistence;

class BucketContentGuard
{
    BucketContentGuard(const BucketContentGuard&);
    BucketContentGuard& operator=(const BucketContentGuard&);
public:
    using UP = std::unique_ptr<BucketContentGuard>;

    BucketContentGuard(DummyPersistence& persistence,
                       BucketContent& content,
                       LockMode lock_mode)
        : _persistence(persistence),
          _content(content),
          _lock_mode(lock_mode)
    {
    }
    ~BucketContentGuard();

    BucketContent& getContent() noexcept {
        return _content;
    }

    BucketContent* operator->() noexcept {
        return &_content;
    }

    BucketContent& operator*() noexcept {
        return _content;
    }
private:
    DummyPersistence& _persistence;
    BucketContent& _content;
    LockMode _lock_mode;
};

class DummyPersistence : public AbstractPersistenceProvider
{
public:
    DummyPersistence(const std::shared_ptr<const document::DocumentTypeRepo>& repo);
    ~DummyPersistence() override;

    Result initialize() override;
    BucketIdListResult listBuckets(BucketSpace bucketSpace) const override;

    void setModifiedBuckets(BucketIdListResult::List result);

    // Important: any subsequent mutations to the bucket set in fake_info will reset
    // the bucket info due to implicit recalculation of bucket info.
    void set_fake_bucket_set(const std::vector<std::pair<Bucket, BucketInfo>>& fake_info);

    /**
     * Returns the list set by setModifiedBuckets(), then clears
     * the list.
     */
    BucketIdListResult getModifiedBuckets(BucketSpace bucketSpace) const override;

    Result setClusterState(BucketSpace bucketSpace, const ClusterState& newState) override;
    void setActiveStateAsync(const Bucket&, BucketInfo::ActiveState, OperationComplete::UP) override;
    BucketInfoResult getBucketInfo(const Bucket&) const override;
    GetResult get(const Bucket&, const document::FieldSet&, const DocumentId&, Context&) const override;
    void putAsync(const Bucket&, Timestamp, DocumentSP, OperationComplete::UP) override;
    void removeAsync(const Bucket& b, std::vector<spi::IdAndTimestamp> ids, OperationComplete::UP) override;
    void updateAsync(const Bucket&, Timestamp, DocumentUpdateSP, OperationComplete::UP) override;

    CreateIteratorResult
    createIterator(const Bucket &bucket, FieldSetSP fs, const Selection &, IncludedVersions, Context &context) override;

    IterateResult iterate(IteratorId, uint64_t maxByteSize) const override;
    Result destroyIterator(IteratorId) override;

    void createBucketAsync(const Bucket&, OperationComplete::UP) noexcept override;
    void deleteBucketAsync(const Bucket&, OperationComplete::UP) noexcept override;

    Result split(const Bucket& source, const Bucket& target1, const Bucket& target2) override;

    Result join(const Bucket& source1, const Bucket& source2, const Bucket& target) override;

    std::unique_ptr<vespalib::IDestructorCallback> register_resource_usage_listener(IResourceUsageListener& listener) override;
    std::unique_ptr<vespalib::IDestructorCallback> register_executor(std::shared_ptr<BucketExecutor>) override;
    std::shared_ptr<BucketExecutor> get_bucket_executor() noexcept { return _bucket_executor.lock(); }

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

private:
    friend class BucketContentGuard;
    // Const since funcs only alter mutable field in BucketContent
    BucketContentGuard::UP acquireBucketWithLock(const Bucket& b, LockMode lock_mode = LockMode::Exclusive) const;
    void releaseBucketNoLock(const BucketContent& bc, LockMode lock_mode = LockMode::Exclusive) const noexcept;
    void internal_create_bucket(const Bucket &b);

    mutable bool _initialized;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    using Content = vespalib::hash_map<Bucket, BucketContent::SP, document::BucketId::hash>;

    Content _content;
    IteratorId _nextIterator;
    mutable std::map<IteratorId, Iterator::UP> _iterators;
    mutable std::mutex      _monitor;
    std::condition_variable _cond;

    std::unique_ptr<ClusterState> _clusterState;
    std::weak_ptr<BucketExecutor> _bucket_executor;

    std::unique_ptr<document::select::Node> parseDocumentSelection(
            const string& documentSelection,
            bool allowLeaf);

    mutable BucketIdListResult::List _modifiedBuckets;
};

}
