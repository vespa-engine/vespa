// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "gid_compare.h"
#include "document_meta_store_adapter.h"
#include "documentmetastoreattribute.h"
#include "lid_allocator.h"
#include "lid_gid_key_comparator.h"
#include "lid_hold_list.h"
#include "lidstatevector.h"
#include "raw_document_meta_data.h"
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/common/subdbtype.h>
#include <vespa/searchlib/common/rcuvector.h>
#include <vespa/searchlib/attribute/singlesmallnumericattribute.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/docstore/ibucketizer.h>

namespace proton {

namespace bucketdb {

class SplitBucketSession;
class JoinBucketsSession;

}

namespace documentmetastore { class Reader; }

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
    typedef documentmetastore::IGidCompare IGidCompare;
    typedef documentmetastore::DefaultGidCompare DefaultGidCompare;

    // If using proton::DocumentMetaStore directly, the
    // DocumentMetaStoreAttribute functions here are used instead of
    // the ones with the same signature in proton::IDocumentMetaStore.
    using DocumentMetaStoreAttribute::commit;
    using DocumentMetaStoreAttribute::getCommittedDocIdLimit;
    using DocumentMetaStoreAttribute::removeAllOldGenerations;
    using DocumentMetaStoreAttribute::getCurrentGeneration;

private:
    // maps from lid -> meta data
    typedef search::attribute::RcuVectorBase<RawDocumentMetaData> MetaDataStore;
    typedef documentmetastore::LidGidKeyComparator KeyComp;

    // Lids are stored as keys in the tree, sorted by their gid
    // counterpart.  The LidGidKeyComparator class maps from lids -> metadata by
    // using the metadata store.
    typedef search::btree::BTree<DocId,
                                 search::btree::BTreeNoLeafData,
                                 search::btree::NoAggregated,
                                 const KeyComp &> TreeType;

    MetaDataStore       _metaDataStore;
    TreeType            _gidToLidMap;
    documentmetastore::LidAllocator _lidAlloc;
    IGidCompare::SP     _gidCompare;
    BucketDBOwner::SP   _bucketDB;
    uint32_t            _shrinkLidSpaceBlockers;
    const SubDbType     _subDbType;
    bool                _trackDocumentSizes;

    DocId getFreeLid();
    DocId peekFreeLid();
    VESPA_DLL_LOCAL void ensureSpace(DocId lid);
    bool insert(DocId lid, const RawDocumentMetaData &metaData);

    const GlobalId & getRawGid(DocId lid) const { return getRawMetaData(lid).getGid(); }

    void onUpdateStat() override;

    // Implements AttributeVector
    void onGenerationChange(generation_t generation) override;
    void removeOldGenerations(generation_t firstUsed) override;
    std::unique_ptr<search::AttributeSaver> onInitSave() override;
    bool onLoad() override;

    bool
    checkBuckets(const GlobalId &gid,
                 const BucketId &bucketId,
                 const TreeType::Iterator &itr,
                 bool found);

    template <typename TreeView>
    typename TreeView::Iterator
    lowerBound(const BucketId &bucketId,
               const TreeView &treeView) const;

    template <typename TreeView>
    typename TreeView::Iterator
    upperBound(const BucketId &bucketId,
               const TreeView &treeView) const;

    void updateMetaDataAndBucketDB(const GlobalId &gid,
                                   DocId lid,
                                   const RawDocumentMetaData &newMetaData);

    void unload();
    void updateActiveLids(const BucketId &bucketId, bool active) override;

    /**
     * Implements DocumentMetaStoreAdapter
     */
    void doCommit(SerialNum firstSerialNum, SerialNum lastSerialNum) override {
        commit(firstSerialNum, lastSerialNum);
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

    bool remove(DocId lid, BucketDBOwner::Guard &bucketGuard);

public:
    typedef TreeType::Iterator Iterator;
    typedef TreeType::ConstIterator ConstIterator;
    static constexpr size_t minHeaderLen = 0x1000;
    static constexpr size_t entrySize =
        sizeof(uint32_t) + GlobalId::LENGTH + sizeof(uint8_t) +
        sizeof(Timestamp::Type);

    DocumentMetaStore(BucketDBOwner::SP bucketDB,
                      const vespalib::string & name=getFixedName(),
                      const search::GrowStrategy & grow=search::GrowStrategy(),
                      const IGidCompare::SP &gidCompare =
                      IGidCompare::SP(new documentmetastore::DefaultGidCompare),
                      SubDbType subDbType = SubDbType::READY);
    ~DocumentMetaStore();

    /**
     * Implements documentmetastore::IStore.
     */
    Result inspectExisting(const GlobalId &gid) const override;
    Result inspect(const GlobalId &gid) override;
    /**
     * Puts the given <lid, meta data> pair to this store.
     * This function should only be called before constructFreeList()
     * and typically after a load(). The use case is replaying of a
     * transaction log where the lids are stored in the log. The gid
     * map is then re-built the same way it was originally where add()
     * was used to create the <lid, gid> pairs.
     **/
    Result put(const GlobalId &gid,
               const BucketId &bucketId,
               const Timestamp &timestamp,
               uint32_t docSize,
               DocId lid) override;
    bool updateMetaData(DocId lid,
                        const BucketId &bucketId,
                        const Timestamp &timestamp) override;
    bool remove(DocId lid) override;

    BucketId getBucketOf(const vespalib::GenerationHandler::Guard & guard, uint32_t lid) const override;
    vespalib::GenerationHandler::Guard getGuard() const override;

    /**
     * Put lid on a hold list, for later reuse.  Typically called
     * after remove() has been called and related structures for
     * document has been torn down (memory index, attribute vectors,
     * document store).
     */
    void removeComplete(DocId lid) override;
    void move(DocId fromLid, DocId toLid) override;
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
              const search::attribute::SearchContextParams &params)
        const override;

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

    SerialNum getLastSerialNum() const override {
        return getStatus().getLastSyncToken();
    }

    /**
     * Implements documentmetastore::IBucketHandler.
     */
    BucketDBOwner &getBucketDB() const override { return *_bucketDB; }

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
    virtual size_t getEstimatedShrinkLidSpaceGain() const override;
    uint64_t getEstimatedSaveByteSize() const override;
    virtual uint32_t getVersion() const override;
    void setTrackDocumentSizes(bool trackDocumentSizes) { _trackDocumentSizes = trackDocumentSizes; }
};

}

extern template class search::btree::
BTreeIterator<proton::DocumentMetaStore::DocId,
              search::btree::BTreeNoLeafData,
              search::btree::NoAggregated,
              const proton::DocumentMetaStore::KeyComp &>;
