// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/common/subdbtype.h>
#include <vespa/searchlib/attribute/iattributesavetarget.h>
#include <vespa/searchlib/common/rcuvector.h>
#include <vespa/searchlib/attribute/singlesmallnumericattribute.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/docstore/ibucketizer.h>
#include "gid_compare.h"
#include "document_meta_store_adapter.h"
#include "documentmetastoreattribute.h"
#include "lid_allocator.h"
#include "lid_gid_key_comparator.h"
#include "lid_hold_list.h"
#include "lidstatevector.h"
#include "raw_document_meta_data.h"

namespace proton {

namespace bucketdb
{

class SplitBucketSession;
class JoinBucketsSession;

}

namespace documentmetastore {
    class Reader;
};

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
    // Bit attribute vector used to keep track of active & inactive documents.
    // Inactive documents will be black-listed during search.
    typedef search::SingleValueBitNumericAttribute BitAttribute;

    MetaDataStore       _metaDataStore;
    TreeType            _gidToLidMap;
    documentmetastore::LidAllocator _lidAlloc;
    IGidCompare::SP     _gidCompare;
    BucketDBOwner::SP   _bucketDB;
    uint32_t            _shrinkLidSpaceBlockers;
    const SubDbType     _subDbType;

    DocId getFreeLid();
    DocId peekFreeLid();
    VESPA_DLL_LOCAL void ensureSpace(DocId lid);
    bool insert(DocId lid, const RawDocumentMetaData &metaData);

    const GlobalId &
    getRawGid(DocId lid) const
    {
        return getRawMetaData(lid).getGid();
    }

    virtual void onUpdateStat() override;

    // Implements AttributeVector
    virtual void onGenerationChange(generation_t generation) override;
    virtual void removeOldGenerations(generation_t firstUsed) override;
    virtual std::unique_ptr<search::AttributeSaver> onInitSave() override;
    virtual bool onLoad() override;

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

    virtual void
    updateActiveLids(const BucketId &bucketId, bool active) override;

    /**
     * Implements DocumentMetaStoreAdapter
     */
    virtual void doCommit(search::SerialNum firstSerialNum,
                          search::SerialNum lastSerialNum) override {
        commit(firstSerialNum, lastSerialNum);
    }
    virtual DocId doGetCommittedDocIdLimit() const override {
        return getCommittedDocIdLimit();
    }
    virtual void doRemoveAllOldGenerations() override {
        removeAllOldGenerations();
    }

    VESPA_DLL_LOCAL DocId readNextDoc(documentmetastore::Reader & reader, TreeType::Builder & treeBuilder);

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
    virtual Result inspectExisting(const GlobalId &gid) const override;
    virtual Result inspect(const GlobalId &gid) override;
    /**
     * Puts the given <lid, meta data> pair to this store.
     * This function should only be called before constructFreeList()
     * and typically after a load(). The use case is replaying of a
     * transaction log where the lids are stored in the log. The gid
     * map is then re-built the same way it was originally where add()
     * was used to create the <lid, gid> pairs.
     **/
    virtual Result put(const GlobalId &gid,
                       const BucketId &bucketId,
                       const Timestamp &timestamp,
                       DocId lid) override;
    virtual bool updateMetaData(DocId lid,
                                const BucketId &bucketId,
                                const Timestamp &timestamp) override;
    virtual bool remove(DocId lid) override;

    virtual BucketId getBucketOf(const vespalib::GenerationHandler::Guard & guard, uint32_t lid) const override;
    virtual vespalib::GenerationHandler::Guard getGuard() const override;

    /**
     * Put lid on a hold list, for later reuse.  Typically called
     * after remove() has been called and related structures for
     * document has been torn down (memory index, attribute vectors,
     * document store).
     */
    virtual void removeComplete(DocId lid) override;
    virtual void move(DocId fromLid, DocId toLid) override;
    bool validLidFast(DocId lid) const { return _lidAlloc.validLid(lid); }
    virtual bool validLid(DocId lid) const override {
        return validLidFast(lid);
    }
    virtual void removeBatch(const std::vector<DocId> &lidsToRemove,
                             const DocId docIdLimit) override;
    /**
     * Put lids on a hold list, for laster reuse.
     */
    virtual void
    removeBatchComplete(const std::vector<DocId> &lidsToRemove) override;
    virtual const RawDocumentMetaData &
    getRawMetaData(DocId lid) const override { return _metaDataStore[lid]; }



    /**
     * Implements search::IDocumentMetaStore
     **/
    virtual bool getGid(DocId lid, GlobalId &gid) const override;
    virtual bool getLid(const GlobalId & gid, DocId &lid) const override;
    virtual search::DocumentMetaData
    getMetaData(const GlobalId &gid) const override;
    virtual void
    getMetaData(const BucketId &bucketId,
                search::DocumentMetaData::Vector &result) const override;
    virtual DocId getNumUsedLids() const override {
        return _lidAlloc.getNumUsedLids();
    }
    virtual DocId getNumActiveLids() const override {
        return _lidAlloc.getNumActiveLids();
    }
    virtual search::LidUsageStats getLidUsageStats() const override;
    virtual search::queryeval::Blueprint::UP
    createBlackListBlueprint() const override;



    /**
     * Implements search::AttributeVector
     */
    SearchContext::UP
    getSearch(search::QueryTermSimple::UP qTerm,
              const search::AttributeVector::SearchContext::Params & params)
        const override;



    /**
     * Implements proton::IDocumentMetaStore
     */
    virtual void constructFreeList() override;

    virtual Iterator begin() const override;

    virtual Iterator lowerBound(const BucketId &bucketId) const override;

    virtual Iterator upperBound(const BucketId &bucketId) const override;

    virtual Iterator lowerBound(const GlobalId &gid) const override;

    virtual Iterator upperBound(const GlobalId &gid) const override;

    virtual void
    getLids(const BucketId &bucketId, std::vector<DocId> &lids) override;

    virtual search::AttributeGuard getActiveLidsGuard() const override {
        return _lidAlloc.getActiveLidsGuard();
    }

    virtual bool getFreeListActive() const override {
        return _lidAlloc.isFreeListConstructed();
    }

    virtual void compactLidSpace(DocId wantedLidLimit) override;

    virtual void holdUnblockShrinkLidSpace() override;

    virtual bool canShrinkLidSpace() const override;

    virtual search::SerialNum getLastSerialNum() const override {
        return getStatus().getLastSyncToken();
    }



    /**
     * Implements documentmetastore::IBucketHandler.
     */
    virtual BucketDBOwner &getBucketDB() const override {
        return *_bucketDB;
    }

    virtual bucketdb::BucketDeltaPair
    handleSplit(const bucketdb::SplitBucketSession &session) override;

    virtual bucketdb::BucketDeltaPair
    handleJoin(const bucketdb::JoinBucketsSession &session) override;

    virtual void
    setBucketState(const BucketId &bucketId, bool active) override;

    virtual void
    populateActiveBuckets(const BucketId::List &buckets) override;



    ConstIterator
    beginFrozen() const;

    const vespalib::GenerationHandler & getGenerationHandler() const {
        return AttributeVector::getGenerationHandler();
    }

    vespalib::GenerationHandler & getGenerationHandler() {
        return AttributeVector::getGenerationHandler();
    }

    const BitAttribute &getActiveLids() const { return _lidAlloc.getActiveLids(); }

    virtual void
    clearDocs(DocId lidLow, DocId lidLimit) override;

    /*
     * Called by document db executor to unblock shrinking of lid
     * space after all lids held by holdLid() operations have been
     * unheld.
     */
    void
    unblockShrinkLidSpace();

    virtual void
    onShrinkLidSpace() override;

    virtual uint64_t getEstimatedSaveByteSize() const override;
};

}

extern template class search::btree::
BTreeIterator<proton::DocumentMetaStore::DocId,
              search::btree::BTreeNoLeafData,
              search::btree::NoAggregated,
              const proton::DocumentMetaStore::KeyComp &>;


