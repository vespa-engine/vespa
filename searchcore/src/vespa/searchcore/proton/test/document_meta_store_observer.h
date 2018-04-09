// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>

namespace proton {
namespace test {

struct DocumentMetaStoreObserver : public IDocumentMetaStore
{
    IDocumentMetaStore &_store;
    uint32_t _removeCompleteCnt;
    DocId _removeCompleteLid;
    DocId _compactLidSpaceLidLimit;
    uint32_t _holdUnblockShrinkLidSpaceCnt;

    DocumentMetaStoreObserver(IDocumentMetaStore &store)
        : _store(store),
          _removeCompleteCnt(0),
          _removeCompleteLid(0),
          _compactLidSpaceLidLimit(0),
          _holdUnblockShrinkLidSpaceCnt(0)
    {
    }

    /**
     * Implements search::IDocumentMetaStore
     **/
    virtual bool getGid(DocId lid, GlobalId &gid) const override {
        return _store.getGid(lid, gid);
    }
    virtual bool getGidEvenIfMoved(DocId lid, GlobalId &gid) const override {
        return _store.getGidEvenIfMoved(lid, gid);
    }
    virtual bool getLid(const GlobalId &gid, DocId &lid) const override {
        return _store.getLid(gid, lid);
    }
    virtual search::DocumentMetaData getMetaData(const GlobalId &gid) const override {
        return _store.getMetaData(gid);
    }
    virtual void getMetaData(const BucketId &bucketId,
                             search::DocumentMetaData::Vector &result) const override {
        _store.getMetaData(bucketId, result);
    }
    virtual search::LidUsageStats getLidUsageStats() const override {
        return _store.getLidUsageStats();
    }
    virtual search::queryeval::Blueprint::UP createWhiteListBlueprint() const override {
        return _store.createWhiteListBlueprint();
    }
    uint64_t getCurrentGeneration() const override {
        return _store.getCurrentGeneration();
    }


    /**
     * Implements documentmetastore::IStore.
     */
    virtual Result inspectExisting(const GlobalId &gid) const override {
        return _store.inspectExisting(gid);
    }
    virtual Result inspect(const GlobalId &gid) override {
        return _store.inspect(gid);
    }
    virtual Result put(const GlobalId &gid,
                       const BucketId &bucketId,
                       const Timestamp &timestamp,
                       uint32_t docSize,
                       DocId lid) override {
        return _store.put(gid, bucketId, timestamp, docSize, lid);
    }
    virtual bool updateMetaData(DocId lid,
                                const BucketId &bucketId,
                                const Timestamp &timestamp) override {
        return _store.updateMetaData(lid, bucketId, timestamp);
    }
    virtual bool remove(DocId lid) override {
        return _store.remove(lid);
    }
    virtual void removeComplete(DocId lid) override {
        ++_removeCompleteCnt;
        _removeCompleteLid = lid;
        _store.removeComplete(lid);
    }
    virtual void move(DocId fromLid, DocId toLid) override {
        _store.move(fromLid, toLid);
    }
    virtual bool validLid(DocId lid) const override {
        return _store.validLid(lid);
    }
    virtual void removeBatch(const std::vector<DocId> &lidsToRemove,
                             const DocId docIdLimit) override {
        _store.removeBatch(lidsToRemove, docIdLimit);
    }
    virtual void removeBatchComplete(const std::vector<DocId> &lidsToRemove) override {
        _store.removeBatchComplete(lidsToRemove);
    }
    virtual const RawDocumentMetaData &getRawMetaData(DocId lid) const override {
        return _store.getRawMetaData(lid);
    }

    /**
     * Implements documentmetastore::IBucketHandler.
     */
    virtual BucketDBOwner &getBucketDB() const override {
        return _store.getBucketDB();
    }
    virtual bucketdb::BucketDeltaPair
    handleSplit(const bucketdb::SplitBucketSession &session) override {
        return _store.handleSplit(session);
    }
    virtual bucketdb::BucketDeltaPair
    handleJoin(const bucketdb::JoinBucketsSession &session) override {
        return _store.handleJoin(session);
    }
    virtual void updateActiveLids(const BucketId &bucketId, bool active) override {
        _store.updateActiveLids(bucketId, active);
    }
    virtual void setBucketState(const BucketId &bucketId, bool active) override {
        _store.setBucketState(bucketId, active);
    }
    virtual void populateActiveBuckets(const BucketId::List &buckets) override {
        _store.populateActiveBuckets(buckets);
    }


    /**
     * Implements proton::IDocumentMetaStore
     */
    virtual void constructFreeList() override {
        _store.constructFreeList();
    }
    virtual Iterator begin() const override {
        return _store.begin();
    }
    virtual Iterator lowerBound(const BucketId &bucketId) const override {
        return _store.lowerBound(bucketId);
    }
    virtual Iterator upperBound(const BucketId &bucketId) const override {
        return _store.upperBound(bucketId);
    }
    virtual Iterator lowerBound(const GlobalId &gid) const override {
        return _store.lowerBound(gid);
    }
    virtual Iterator upperBound(const GlobalId &gid) const override {
        return _store.upperBound(gid);
    }
    virtual void getLids(const BucketId &bucketId, std::vector<DocId> &lids) override {
        _store.getLids(bucketId, lids);
    }
    virtual DocId getNumUsedLids() const override {
        return _store.getNumUsedLids();
    }
    virtual DocId getNumActiveLids() const override {
        return _store.getNumActiveLids();
    }
    virtual bool getFreeListActive() const override {
        return _store.getFreeListActive();
    }
    virtual void compactLidSpace(DocId wantedLidLimit) override {
        _compactLidSpaceLidLimit = wantedLidLimit;
        _store.compactLidSpace(wantedLidLimit);
    }
    virtual void holdUnblockShrinkLidSpace() override {
        ++_holdUnblockShrinkLidSpaceCnt;
        _store.holdUnblockShrinkLidSpace();
    }
    virtual void commit(search::SerialNum firstSerialNum,
                        search::SerialNum lastSerialNum) override {
        _store.commit(firstSerialNum, lastSerialNum);
    }
    virtual DocId getCommittedDocIdLimit() const override {
        return _store.getCommittedDocIdLimit();
    }
    virtual void removeAllOldGenerations() override {
        _store.removeAllOldGenerations();
    }
    virtual bool canShrinkLidSpace() const override {
        return _store.canShrinkLidSpace();
    }
    virtual search::SerialNum getLastSerialNum() const override {
        return _store.getLastSerialNum();
    }
};

}
}

