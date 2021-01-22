// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/i_document_scan_iterator.h>
#include <vespa/searchcore/proton/server/ifrozenbuckethandler.h>
#include <vespa/searchcore/proton/server/imaintenancejobrunner.h>
#include <vespa/searchcore/proton/server/lid_space_compaction_handler.h>
#include <vespa/searchcore/proton/server/remove_operations_rate_tracker.h>
#include <vespa/searchcore/proton/server/maintenancedocumentsubdb.h>
#include <vespa/searchcore/proton/server/i_operation_storer.h>
#include <vespa/searchcore/proton/documentmetastore/operation_listener.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/feedoperation/compact_lid_space_operation.h>
#include <vespa/searchcore/proton/test/clusterstatehandler.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/searchlib/index/docbuilder.h>

using namespace document;
using namespace proton;
using namespace search::index;
using namespace search;
using namespace vespalib;
using vespalib::IDestructorCallback;
using storage::spi::Timestamp;
using TimePoint = LidUsageStats::TimePoint;

constexpr uint32_t SUBDB_ID = 2;
constexpr vespalib::duration JOB_DELAY = 1s;
constexpr uint32_t ALLOWED_LID_BLOAT = 1;
constexpr double ALLOWED_LID_BLOAT_FACTOR = 0.3;
constexpr double REMOVE_BATCH_BLOCK_RATE = 1.0 / 21.0;
constexpr double REMOVE_BLOCK_RATE = 1.0 / 20.0;
constexpr double RESOURCE_LIMIT_FACTOR = 1.0;
constexpr uint32_t MAX_OUTSTANDING_MOVE_OPS = 10;
const vespalib::string DOC_ID = "id:test:searchdocument::0";
const BucketId BUCKET_ID_1(1);
const BucketId BUCKET_ID_2(2);
const Timestamp TIMESTAMP_1(1);
const GlobalId GID_1;

using LidVector = std::vector<uint32_t>;
using LidPair = std::pair<uint32_t, uint32_t>;
using LidPairVector = std::vector<LidPair>;

struct MyScanIterator : public IDocumentScanIterator {
    LidVector _lids;
    LidVector::const_iterator _itr;
    bool _validItr;
    bool _bucketIdEqualLid;
    explicit MyScanIterator(const LidVector &lids, bool bucketIdEqualLid);
    ~MyScanIterator() override;
    bool valid() const override;
    search::DocumentMetaData next(uint32_t compactLidLimit, bool retry) override;
    search::DocumentMetaData getMetaData(uint32_t lid) const override;

    document::BucketId createBucketId(uint32_t lid) const;
};

struct MyHandler : public ILidSpaceCompactionHandler {
    std::vector<LidUsageStats> _stats;
    std::vector<LidVector> _lids;
    mutable uint32_t _moveFromLid;
    mutable uint32_t _moveToLid;
    uint32_t _handleMoveCnt;
    uint32_t _wantedLidLimit;
    mutable uint32_t _iteratorCnt;
    bool _storeMoveDoneContexts;
    bool _bucketIdEqualLid;
    std::vector<IDestructorCallback::SP> _moveDoneContexts;
    documentmetastore::OperationListener::SP _op_listener;
    RemoveOperationsRateTracker* _rm_listener;

    explicit MyHandler(bool storeMoveDoneContexts, bool _bucketIdEqualLid);
    ~MyHandler() override;
    void clearMoveDoneContexts();
    void run_remove_ops(bool remove_batch);
    void stop_remove_ops(bool remove_batch) const;
    vespalib::string getName() const override;
    void set_operation_listener(documentmetastore::OperationListener::SP op_listener) override;
    uint32_t getSubDbId() const override { return 2; }
    LidUsageStats getLidStatus() const override;
    IDocumentScanIterator::UP getIterator() const override;
    MoveOperation::UP createMoveOperation(const search::DocumentMetaData &document,
                                          uint32_t moveToLid) const override;
    void handleMove(const MoveOperation &, IDestructorCallback::SP moveDoneCtx) override;
    void handleCompactLidSpace(const CompactLidSpaceOperation &op, std::shared_ptr<IDestructorCallback>) override;
};

struct MyStorer : public IOperationStorer {
    uint32_t _moveCnt;
    uint32_t _compactCnt;
    MyStorer()
        : _moveCnt(0),
          _compactCnt(0)
    {}
    void appendOperation(const FeedOperation &op, DoneCallback) override {
        if (op.getType() == FeedOperation::MOVE) {
            ++ _moveCnt;
        } else if (op.getType() == FeedOperation::COMPACT_LID_SPACE) {
            ++_compactCnt;
        }
    }
    CommitResult startCommit(DoneCallback) override {
        return CommitResult();
    }
};

struct MyFrozenBucketHandler : public IFrozenBucketHandler {
    BucketId _bucket;
    MyFrozenBucketHandler() : _bucket() {}
    ExclusiveBucketGuard::UP acquireExclusiveBucket(BucketId bucket) override {
        return (_bucket == bucket)
               ? ExclusiveBucketGuard::UP()
               : std::make_unique<ExclusiveBucketGuard>(bucket);
    }
    void addListener(IBucketFreezeListener *) override { }
    void removeListener(IBucketFreezeListener *) override { }
};

struct MyFeedView : public test::DummyFeedView {
    explicit MyFeedView(std::shared_ptr<const DocumentTypeRepo> repo)
        : test::DummyFeedView(std::move(repo))
    {
    }
};

struct MyDocumentStore : public test::DummyDocumentStore {
    Document::SP _readDoc;
    mutable uint32_t _readLid;
    MyDocumentStore() : _readDoc(), _readLid(0) {}
    ~MyDocumentStore() override;
    document::Document::UP read(search::DocumentIdT lid, const document::DocumentTypeRepo &) const override {
        _readLid = lid;
        return Document::UP(_readDoc->clone());
    }
};

struct MyDocumentRetriever : public DocumentRetrieverBaseForTest {
    std::shared_ptr<const DocumentTypeRepo> repo;
    const MyDocumentStore& store;
    MyDocumentRetriever(std::shared_ptr<const DocumentTypeRepo> repo_in, const MyDocumentStore& store_in) noexcept
        : repo(std::move(repo_in)),
          store(store_in)
    {}
    const document::DocumentTypeRepo& getDocumentTypeRepo() const override { return *repo; }
    void getBucketMetaData(const storage::spi::Bucket&, DocumentMetaData::Vector&) const override { abort(); }
    DocumentMetaData getDocumentMetaData(const DocumentId&) const override { abort(); }
    Document::UP getFullDocument(DocumentIdT lid) const override {
        return store.read(lid, *repo);
    }
    CachedSelect::SP parseSelect(const vespalib::string&) const override { abort(); }
};

struct MySubDb {
    test::DummyDocumentSubDb sub_db;
    MaintenanceDocumentSubDB maintenance_sub_db;
    PendingLidTracker _pendingLidsForCommit;
    MySubDb(std::shared_ptr<BucketDBOwner> bucket_db, const MyDocumentStore& store, const std::shared_ptr<const DocumentTypeRepo> & repo);
    ~MySubDb();
};
