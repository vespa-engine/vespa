// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/documentmetastore/operation_listener.h>

namespace proton::test {

struct DocumentMetaStoreObserver : public IDocumentMetaStore
{
    IDocumentMetaStore &_store;
    uint32_t _removes_complete_cnt;
    std::vector<DocId> _removes_complete_lids;
    DocId _compactLidSpaceLidLimit;
    uint32_t _holdUnblockShrinkLidSpaceCnt;

    DocumentMetaStoreObserver(IDocumentMetaStore &store) noexcept
        : _store(store),
          _removes_complete_cnt(0),
          _removes_complete_lids(0),
          _compactLidSpaceLidLimit(0),
          _holdUnblockShrinkLidSpaceCnt(0)
    {}

    /**
     * Implements search::IDocumentMetaStore
     **/
    bool getGid(DocId lid, GlobalId &gid) const override {
        return _store.getGid(lid, gid);
    }
    bool getGidEvenIfMoved(DocId lid, GlobalId &gid) const override {
        return _store.getGidEvenIfMoved(lid, gid);
    }
    bool getLid(const GlobalId &gid, DocId &lid) const override {
        return _store.getLid(gid, lid);
    }
    search::DocumentMetaData getMetaData(const GlobalId &gid) const override {
        return _store.getMetaData(gid);
    }
    void getMetaData(const BucketId &bucketId, search::DocumentMetaData::Vector &result) const override {
        _store.getMetaData(bucketId, result);
    }
    search::LidUsageStats getLidUsageStats() const override {
        return _store.getLidUsageStats();
    }
    search::queryeval::Blueprint::UP createWhiteListBlueprint() const override {
        return _store.createWhiteListBlueprint();
    }
    uint64_t getCurrentGeneration() const override {
        return _store.getCurrentGeneration();
    }


    /**
     * Implements documentmetastore::IStore.
     */
    Result inspectExisting(const GlobalId &gid, uint64_t prepare_serial_num) override {
        return _store.inspectExisting(gid, prepare_serial_num);
    }
    Result inspect(const GlobalId &gid, uint64_t prepare_serial_num) override {
        return _store.inspect(gid, prepare_serial_num);
    }
    Result put(const GlobalId &gid, const BucketId &bucketId, Timestamp timestamp,
               uint32_t docSize, DocId lid, uint64_t prepare_serial_num) override
    {
        return _store.put(gid, bucketId, timestamp, docSize, lid, prepare_serial_num);
    }
    bool updateMetaData(DocId lid, const BucketId &bucketId, Timestamp timestamp) override {
        return _store.updateMetaData(lid, bucketId, timestamp);
    }
    bool remove(DocId lid, uint64_t prepare_serial_num) override {
        return _store.remove(lid, prepare_serial_num);
    }
    void removes_complete(const std::vector<DocId>& lids) override {
        ++_removes_complete_cnt;
        _removes_complete_lids.insert(_removes_complete_lids.end(), lids.cbegin(), lids.cend());
        _store.removes_complete(lids);
    }
    void move(DocId fromLid, DocId toLid, uint64_t prepare_serial_num) override {
        _store.move(fromLid, toLid, prepare_serial_num);
    }
    bool validLid(DocId lid) const override {
        return _store.validLid(lid);
    }
     void removeBatch(const std::vector<DocId> &lidsToRemove, const DocId docIdLimit) override {
        _store.removeBatch(lidsToRemove, docIdLimit);
    }
    const RawDocumentMetaData &getRawMetaData(DocId lid) const override {
        return _store.getRawMetaData(lid);
    }

    /**
     * Implements documentmetastore::IBucketHandler.
     */
    bucketdb::BucketDBOwner &getBucketDB() const override {
        return _store.getBucketDB();
    }
    bucketdb::BucketDeltaPair
    handleSplit(const bucketdb::SplitBucketSession &session) override {
        return _store.handleSplit(session);
    }
    bucketdb::BucketDeltaPair
    handleJoin(const bucketdb::JoinBucketsSession &session) override {
        return _store.handleJoin(session);
    }
    void updateActiveLids(const BucketId &bucketId, bool active) override {
        _store.updateActiveLids(bucketId, active);
    }
    void setBucketState(const BucketId &bucketId, bool active) override {
        _store.setBucketState(bucketId, active);
    }
    void populateActiveBuckets(document::BucketId::List buckets) override {
        _store.populateActiveBuckets(std::move(buckets));
    }


    /**
     * Implements proton::IDocumentMetaStore
     */
    void constructFreeList() override {
        _store.constructFreeList();
    }
    Iterator begin() const override {
        return _store.begin();
    }
    Iterator lowerBound(const BucketId &bucketId) const override {
        return _store.lowerBound(bucketId);
    }
    Iterator upperBound(const BucketId &bucketId) const override {
        return _store.upperBound(bucketId);
    }
    Iterator lowerBound(const GlobalId &gid) const override {
        return _store.lowerBound(gid);
    }
    Iterator upperBound(const GlobalId &gid) const override {
        return _store.upperBound(gid);
    }
    void getLids(const BucketId &bucketId, std::vector<DocId> &lids) override {
        _store.getLids(bucketId, lids);
    }
    DocId getNumUsedLids() const override {
        return _store.getNumUsedLids();
    }
    DocId getNumActiveLids() const override {
        return _store.getNumActiveLids();
    }
    bool getFreeListActive() const override {
        return _store.getFreeListActive();
    }
    void compactLidSpace(DocId wantedLidLimit) override {
        _compactLidSpaceLidLimit = wantedLidLimit;
        _store.compactLidSpace(wantedLidLimit);
    }
    void holdUnblockShrinkLidSpace() override {
        ++_holdUnblockShrinkLidSpaceCnt;
        _store.holdUnblockShrinkLidSpace();
    }
    void commit(const CommitParam & param) override {
        _store.commit(param);
    }
    DocId getCommittedDocIdLimit() const override {
        return _store.getCommittedDocIdLimit();
    }
    void reclaim_unused_memory() override {
        _store.reclaim_unused_memory();
    }
    bool canShrinkLidSpace() const override {
        return _store.canShrinkLidSpace();
    }
    search::SerialNum getLastSerialNum() const override {
        return _store.getLastSerialNum();
    }
    void foreach(const search::IGidToLidMapperVisitor &visitor) const override {
        _store.foreach(visitor);
    }
    void set_operation_listener(documentmetastore::OperationListener::SP op_listener) override {
        _store.set_operation_listener(std::move(op_listener));
    }
};

}

