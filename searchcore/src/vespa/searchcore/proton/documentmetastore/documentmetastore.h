// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "document_meta_store_adapter.h"
#include "documentmetastoreattribute.h"
#include "lid_allocator.h"
#include "lid_gid_key_comparator.h"
#include "raw_document_meta_data.h"
#include <vespa/searchcore/proton/common/subdbtype.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/docstore/ibucketizer.h>
#include <vespa/searchcommon/common/growstrategy.h>
#include <vespa/vespalib/util/rcuvector.h>

namespace proton::bucketdb {
    class SplitBucketSession;
    class JoinBucketsSession;
    class Guard;
}

namespace proton::documentmetastore {
    class OperationListener;
    class Reader;
}

namespace proton {
    
/**
 * This class provides a storage of <lid, meta data> pairs (local
 * document id, meta data (including global document id)) and mapping
 * from lid -> meta data (including gid) and gid -> lid.
 **/
class DocumentMetaStore final : public DocumentMetaStoreAttribute,
                                public DocumentMetaStoreAdapter,
                                public search::IBucketizer
{
public:
    using SP = std::shared_ptr<DocumentMetaStore>;
    using Result = documentmetastore::IStore::Result;
    using DocId = documentmetastore::IStore::DocId;
    using GlobalId = documentmetastore::IStore::GlobalId;
    using BucketId = documentmetastore::IStore::BucketId;
    using Timestamp = documentmetastore::IStore::Timestamp;
    using MetaDataView = vespalib::ConstArrayRef<RawDocumentMetaData>;
    using UnboundMetaDataView = const RawDocumentMetaData *;

    // If using proton::DocumentMetaStore directly, the
    // DocumentMetaStoreAttribute functions here are used instead of
    // the ones with the same signature in proton::IDocumentMetaStore.
    using DocumentMetaStoreAttribute::commit;
    using DocumentMetaStoreAttribute::getCommittedDocIdLimit;
    using DocumentMetaStoreAttribute::reclaim_unused_memory;
    using DocumentMetaStoreAttribute::getCurrentGeneration;

private:
    // maps from lid -> meta data
    using MetaDataStore = vespalib::RcuVectorBase<RawDocumentMetaData>;
    using KeyComp = documentmetastore::LidGidKeyComparator;
    using OperationListenerSP = std::shared_ptr<documentmetastore::OperationListener>;
    using BucketDBOwnerSP = std::shared_ptr<bucketdb::BucketDBOwner>;

    // Lids are stored as keys in the tree, sorted by their gid
    // counterpart.  The LidGidKeyComparator class maps from lids -> metadata by
    // using the metadata store.
    using TreeType =  vespalib::btree::BTree<documentmetastore::GidToLidMapKey, vespalib::btree::BTreeNoLeafData,
                                             vespalib::btree::NoAggregated, const KeyComp &>;
    using LidAndRawDocumentMetaData = std::pair<uint32_t, RawDocumentMetaData>;

    MetaDataStore       _metaDataStore;
    TreeType            _gidToLidMap;
    Iterator            _gid_to_lid_map_write_itr; // Iterator used for all updates of _gidToLidMap
    SerialNum           _gid_to_lid_map_write_itr_prepare_serial_num;
    documentmetastore::LidAllocator _lidAlloc;
    BucketDBOwnerSP     _bucketDB;
    std::atomic<uint32_t> _shrinkLidSpaceBlockers;
    const SubDbType     _subDbType;
    bool                _trackDocumentSizes;
    size_t              _changesSinceCommit;
    OperationListenerSP _op_listener;
    bool                _should_compact_gid_to_lid_map;

    DocId getFreeLid();
    DocId peekFreeLid();
    VESPA_DLL_LOCAL void ensureSpace(DocId lid);
    void insert(documentmetastore::GidToLidMapKey key, const RawDocumentMetaData &metaData);

    const GlobalId & getRawGid(DocId lid) const { return getRawMetaData(lid).getGid(); }

    bool consider_compact_gid_to_lid_map();
    void onCommit() override;
    void onUpdateStat() override;

    // Implements AttributeVector
    void before_inc_generation(generation_t current_gen) override;
    void reclaim_memory(generation_t oldest_used_gen) override;
    std::unique_ptr<search::AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    bool onLoad(vespalib::Executor *executor) override;

    template <typename TreeView>
    typename TreeView::Iterator
    lowerBound(const BucketId &bucketId, const TreeView &treeView) const;

    template <typename TreeView>
    typename TreeView::Iterator
    upperBound(const BucketId &bucketId, const TreeView &treeView) const;

    void updateMetaDataAndBucketDB(const GlobalId &gid, DocId lid,
                                   const RawDocumentMetaData &newMetaData);

    void unload();
    void updateActiveLids(const BucketId &bucketId, bool active) override;

    /**
     * Implements DocumentMetaStoreAdapter
     */
    void doCommit(const CommitParam & param) override {
        commit(param);
    }
    DocId doGetCommittedDocIdLimit() const override {
        return getCommittedDocIdLimit();
    }
    void doRemoveAllOldGenerations() override {
        reclaim_unused_memory();
    }
    uint64_t doGetCurrentGeneration() const override {
        return getCurrentGeneration();
    }

    VESPA_DLL_LOCAL DocId readNextDoc(documentmetastore::Reader & reader, TreeType::Builder & treeBuilder);

    RawDocumentMetaData removeInternal(DocId lid, uint64_t cached_iterator_sequence_id);
    void remove_batch_internal_btree(std::vector<LidAndRawDocumentMetaData>& removed);

    MetaDataView make_meta_data_view() { return {&_metaDataStore[0], getCommittedDocIdLimit()}; }
    UnboundMetaDataView acquire_unbound_meta_data_view() const noexcept { return &_metaDataStore.acquire_elem_ref(0); }
    UnboundMetaDataView get_unbound_meta_data_view() const noexcept { return &_metaDataStore.get_elem_ref(0); } // Called from writer only

    uint32_t get_shrink_lid_space_blockers() const noexcept { return _shrinkLidSpaceBlockers.load(std::memory_order_relaxed); }
    void set_shrink_lid_space_blockers(uint32_t value) noexcept { _shrinkLidSpaceBlockers.store(value, std::memory_order_relaxed); }

public:
    using Iterator = TreeType::Iterator;
    using ConstIterator = TreeType::ConstIterator;
    static constexpr size_t minHeaderLen = 0x1000;
    static constexpr size_t entrySize =
        sizeof(uint32_t) + GlobalId::LENGTH + sizeof(uint8_t) +
        sizeof(Timestamp);

    explicit DocumentMetaStore(BucketDBOwnerSP bucketDB);
    DocumentMetaStore(BucketDBOwnerSP bucketDB, const vespalib::string & name);
    DocumentMetaStore(BucketDBOwnerSP bucketDB,
                      const vespalib::string & name,
                      const search::GrowStrategy & grow,
                      SubDbType subDbType = SubDbType::READY);
    ~DocumentMetaStore() override;

    /**
     * Implements documentmetastore::IStore.
     */
    Result inspectExisting(const GlobalId &gid, uint64_t prepare_serial_num) override;
    Result inspect(const GlobalId &gid, uint64_t prepare_serial_num) override;
    /**
     * Puts the given <lid, meta data> pair to this store.
     * This function should only be called before constructFreeList()
     * and typically after a load(). The use case is replaying of a
     * transaction log where the lids are stored in the log. The gid
     * map is then re-built the same way it was originally where add()
     * was used to create the <lid, gid> pairs.
     **/
    Result put(const GlobalId &gid, const BucketId &bucketId, Timestamp timestamp,
               uint32_t docSize, DocId lid, uint64_t prepare_serial_num) override;
    bool updateMetaData(DocId lid, const BucketId &bucketId, Timestamp timestamp) override;
    bool remove(DocId lid, uint64_t prepare_serial_num) override;

    BucketId getBucketOf(const vespalib::GenerationHandler::Guard & guard, uint32_t lid) const override;
    vespalib::GenerationHandler::Guard getGuard() const override;

    /**
     * Put lids on a hold list, for later reuse.  Typically called
     * after remove() or removeBatch() has been called and related structures for
     * documents have been torn down (memory index, attribute vectors,
     * document store).
     */
    void removes_complete(const std::vector<DocId>& lids) override;
    void move(DocId fromLid, DocId toLid, uint64_t prepare_serial_num) override;
    bool validButMaybeUnusedLid(DocId lid) const { return _lidAlloc.validButMaybeUnusedLid(lid); }
    bool validLidFast(DocId lid) const { return _lidAlloc.validLid(lid); }
    bool validLidFast(DocId lid, uint32_t limit) const { return _lidAlloc.validLid(lid, limit); }
    bool validLid(DocId lid) const override { return validLidFast(lid); }
    void removeBatch(const std::vector<DocId> &lidsToRemove, DocId docIdLimit) override;
    const RawDocumentMetaData & getRawMetaData(DocId lid) const override { return _metaDataStore.acquire_elem_ref(lid); }

    /**
     * Implements search::IDocumentMetaStore
     **/
    bool getGid(DocId lid, GlobalId &gid) const override;
    bool getGidEvenIfMoved(DocId lid, GlobalId &gid) const override;
    bool getLid(const GlobalId & gid, DocId &lid) const override;
    search::DocumentMetaData getMetaData(const GlobalId &gid) const override;
    void getMetaData(const BucketId &bucketId, search::DocumentMetaData::Vector &result) const override;
    DocId   getNumUsedLids() const override { return _lidAlloc.getNumUsedLids(); }
    DocId getNumActiveLids() const override { return _lidAlloc.getNumActiveLids(); }
    search::LidUsageStats getLidUsageStats() const override;
    search::queryeval::Blueprint::UP createWhiteListBlueprint() const override;

    /**
     * Implements search::AttributeVector
     */
    std::unique_ptr<search::attribute::SearchContext>
    getSearch(std::unique_ptr<search::QueryTermSimple> qTerm,
              const search::attribute::SearchContextParams &params) const override;

    /**
     * Implements proton::IDocumentMetaStore
     */
    void constructFreeList() override;
    Iterator begin() const override;
    Iterator lowerBound(const BucketId &bucketId) const override;
    Iterator upperBound(const BucketId &bucketId) const override;
    Iterator lowerBound(const GlobalId &gid) const override;
    Iterator upperBound(const GlobalId &gid) const override;

    void getLids(const BucketId &bucketId, std::vector<DocId> &lids) override;

    bool getFreeListActive() const override {
        return _lidAlloc.isFreeListConstructed();
    }

    void compactLidSpace(DocId wantedLidLimit) override;
    void holdUnblockShrinkLidSpace() override;
    bool canShrinkLidSpace() const override;
    void set_operation_listener(std::shared_ptr<documentmetastore::OperationListener> op_listener) override;

    SerialNum getLastSerialNum() const override {
        return getStatus().getLastSyncToken();
    }

    /**
     * Implements documentmetastore::IBucketHandler.
     */
    bucketdb::BucketDBOwner &getBucketDB() const override { return *_bucketDB; }

    bucketdb::BucketDeltaPair handleSplit(const bucketdb::SplitBucketSession &session) override;
    bucketdb::BucketDeltaPair handleJoin(const bucketdb::JoinBucketsSession &session) override;
    void setBucketState(const BucketId &bucketId, bool active) override;
    void populateActiveBuckets(BucketId::List buckets) override;
    ConstIterator beginFrozen() const;

    const vespalib::GenerationHandler & getGenerationHandler() const {
        return AttributeVector::getGenerationHandler();
    }

    vespalib::GenerationHandler & getGenerationHandler() {
        return AttributeVector::getGenerationHandler();
    }

    const search::BitVector &getActiveLids() const { return _lidAlloc.getActiveLids(); }

    void clearDocs(DocId lidLow, DocId lidLimit, bool in_shrink_lid_space) override;

    /*
     * Called by document db executor to unblock shrinking of lid
     * space after all lids held by holdLid() operations have been
     * unheld.
     */
    void unblockShrinkLidSpace();
    void onShrinkLidSpace() override;
    size_t getEstimatedShrinkLidSpaceGain() const override;
    uint64_t getEstimatedSaveByteSize() const override;
    uint32_t getVersion() const override;
    void setTrackDocumentSizes(bool trackDocumentSizes) { _trackDocumentSizes = trackDocumentSizes; }
    void foreach(const search::IGidToLidMapperVisitor &visitor) const override;
    long onSerializeForAscendingSort(DocId, void *, long, const search::common::BlobConverter *) const override;
    long onSerializeForDescendingSort(DocId, void *, long, const search::common::BlobConverter *) const override;
};

}

namespace vespalib::btree {

extern template class BTreeIteratorBase<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, BTreeDefaultTraits::INTERNAL_SLOTS, BTreeDefaultTraits::LEAF_SLOTS, BTreeDefaultTraits::PATH_SIZE>;

extern template class BTreeConstIterator<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, const proton::DocumentMetaStore::KeyComp &>;

extern template class BTreeIterator<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, const proton::DocumentMetaStore::KeyComp &>;

}
