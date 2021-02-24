// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "document_meta_store_adapter.h"
#include "documentmetastoreattribute.h"
#include "lid_allocator.h"
#include "lid_gid_key_comparator.h"
#include "lid_hold_list.h"
#include "raw_document_meta_data.h"
#include <vespa/searchcore/proton/common/subdbtype.h>
#include <vespa/searchlib/attribute/singlesmallnumericattribute.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/docstore/ibucketizer.h>
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
    typedef std::shared_ptr<DocumentMetaStore> SP;
    typedef documentmetastore::IStore::Result Result;
    typedef documentmetastore::IStore::DocId DocId;
    typedef documentmetastore::IStore::GlobalId GlobalId;
    typedef documentmetastore::IStore::BucketId BucketId;
    typedef documentmetastore::IStore::Timestamp Timestamp;

    // If using proton::DocumentMetaStore directly, the
    // DocumentMetaStoreAttribute functions here are used instead of
    // the ones with the same signature in proton::IDocumentMetaStore.
    using DocumentMetaStoreAttribute::commit;
    using DocumentMetaStoreAttribute::getCommittedDocIdLimit;
    using DocumentMetaStoreAttribute::removeAllOldGenerations;
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

    MetaDataStore       _metaDataStore;
    TreeType            _gidToLidMap;
    Iterator            _gid_to_lid_map_write_itr; // Iterator used for all updates of _gidToLidMap
    SerialNum           _gid_to_lid_map_write_itr_prepare_serial_num;
    documentmetastore::LidAllocator _lidAlloc;
    BucketDBOwnerSP     _bucketDB;
    uint32_t            _shrinkLidSpaceBlockers;
    const SubDbType     _subDbType;
    bool                _trackDocumentSizes;
    OperationListenerSP _op_listener;

    DocId getFreeLid();
    DocId peekFreeLid();
    VESPA_DLL_LOCAL void ensureSpace(DocId lid);
    void insert(documentmetastore::GidToLidMapKey key, const RawDocumentMetaData &metaData);

    const GlobalId & getRawGid(DocId lid) const { return getRawMetaData(lid).getGid(); }

    void onUpdateStat() override;

    // Implements AttributeVector
    void onGenerationChange(generation_t generation) override;
    void removeOldGenerations(generation_t firstUsed) override;
    std::unique_ptr<search::AttributeSaver> onInitSave(vespalib::stringref fileName) override;
    bool onLoad() override;

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
        removeAllOldGenerations();
    }
    uint64_t doGetCurrentGeneration() const override {
        return getCurrentGeneration();
    }

    VESPA_DLL_LOCAL DocId readNextDoc(documentmetastore::Reader & reader, TreeType::Builder & treeBuilder);

    void remove(DocId lid, uint64_t cached_iterator_sequence_id, bucketdb::Guard &bucketGuard);

public:
    typedef TreeType::Iterator Iterator;
    typedef TreeType::ConstIterator ConstIterator;
    static constexpr size_t minHeaderLen = 0x1000;
    static constexpr size_t entrySize =
        sizeof(uint32_t) + GlobalId::LENGTH + sizeof(uint8_t) +
        sizeof(Timestamp::Type);

    DocumentMetaStore(BucketDBOwnerSP bucketDB,
                      const vespalib::string & name=getFixedName(),
                      const search::GrowStrategy & grow=search::GrowStrategy(),
                      SubDbType subDbType = SubDbType::READY);
    ~DocumentMetaStore();

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
    Result put(const GlobalId &gid, const BucketId &bucketId,
               const Timestamp &timestamp, uint32_t docSize, DocId lid, uint64_t prepare_serial_num) override;
    bool updateMetaData(DocId lid, const BucketId &bucketId, const Timestamp &timestamp) override;
    bool remove(DocId lid, uint64_t prepare_serial_num) override;

    BucketId getBucketOf(const vespalib::GenerationHandler::Guard & guard, uint32_t lid) const override;
    vespalib::GenerationHandler::Guard getGuard() const override;

    /**
     * Put lid on a hold list, for later reuse.  Typically called
     * after remove() has been called and related structures for
     * document has been torn down (memory index, attribute vectors,
     * document store).
     */
    void removeComplete(DocId lid) override;
    void move(DocId fromLid, DocId toLid, uint64_t prepare_serial_num) override;
    bool validButMaybeUnusedLid(DocId lid) const { return _lidAlloc.validButMaybeUnusedLid(lid); }
    bool validLidFast(DocId lid) const { return _lidAlloc.validLid(lid); }
    bool validLid(DocId lid) const override { return validLidFast(lid); }
    void removeBatch(const std::vector<DocId> &lidsToRemove, const DocId docIdLimit) override;
    /**
     * Put lids on a hold list, for laster reuse.
     */
    void removeBatchComplete(const std::vector<DocId> &lidsToRemove) override;
    const RawDocumentMetaData & getRawMetaData(DocId lid) const override { return _metaDataStore[lid]; }

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
    SearchContext::UP
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
    void populateActiveBuckets(const BucketId::List &buckets) override;
    ConstIterator beginFrozen() const;

    const vespalib::GenerationHandler & getGenerationHandler() const {
        return AttributeVector::getGenerationHandler();
    }

    vespalib::GenerationHandler & getGenerationHandler() {
        return AttributeVector::getGenerationHandler();
    }

    const search::GrowableBitVector &getActiveLids() const { return _lidAlloc.getActiveLids(); }

    void clearDocs(DocId lidLow, DocId lidLimit) override;

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
};

}

namespace vespalib::btree {

extern template class BTreeIteratorBase<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, BTreeDefaultTraits::INTERNAL_SLOTS, BTreeDefaultTraits::LEAF_SLOTS, BTreeDefaultTraits::PATH_SIZE>;

extern template class BTreeConstIterator<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, const proton::DocumentMetaStore::KeyComp &>;

extern template class BTreeIterator<proton::documentmetastore::GidToLidMapKey, BTreeNoLeafData, NoAggregated, const proton::DocumentMetaStore::KeyComp &>;

}
