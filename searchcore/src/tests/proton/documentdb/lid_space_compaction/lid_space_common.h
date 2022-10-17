// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/i_document_scan_iterator.h>
#include <vespa/searchcore/proton/server/imaintenancejobrunner.h>
#include <vespa/searchcore/proton/server/lid_space_compaction_handler.h>
#include <vespa/searchcore/proton/server/remove_operations_rate_tracker.h>
#include <vespa/searchcore/proton/server/maintenancedocumentsubdb.h>
#include <vespa/searchcore/proton/server/i_operation_storer.h>
#include <vespa/searchcore/proton/common/pendinglidtracker.h>
#include <vespa/searchcore/proton/documentmetastore/operation_listener.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/feedoperation/compact_lid_space_operation.h>
#include <vespa/searchcore/proton/test/clusterstatehandler.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/searchcore/proton/test/dummy_document_store.h>
#include <vespa/vespalib/util/idestructorcallback.h>

using document::BucketId;
using document::GlobalId;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using namespace proton;
using search::test::DocBuilder;
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
const vespalib::string DOC_ID = "id:test:searchdocument::";
const BucketId BUCKET_ID_1(1);
const BucketId BUCKET_ID_2(2);
const Timestamp TIMESTAMP_1(1);
const GlobalId GID_1;

using LidVector = std::vector<uint32_t>;
using LidPair = std::pair<uint32_t, uint32_t>;
using LidPairVector = std::vector<LidPair>;
struct MyHandler;

namespace proton::test { struct DummyDocumentSubDb; }
struct MyScanIterator : public IDocumentScanIterator {
    const MyHandler & _handler;
    LidVector _lids;
    LidVector::const_iterator _itr;
    bool _validItr;
    explicit MyScanIterator(const MyHandler & handler, const LidVector &lids);
    ~MyScanIterator() override;
    bool valid() const override;
    search::DocumentMetaData next(uint32_t compactLidLimit) override;
};

struct MyHandler : public ILidSpaceCompactionHandler {
    DocBuilder _builder;
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
    std::vector<std::pair<search::DocumentMetaData, std::shared_ptr<Document>>> _docs;

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
    search::DocumentMetaData getMetaData(uint32_t lid) const override;
    MoveOperation::UP createMoveOperation(const search::DocumentMetaData &document,
                                          uint32_t moveToLid) const override;
    void handleMove(const MoveOperation &, IDestructorCallback::SP moveDoneCtx) override;
    void handleCompactLidSpace(const CompactLidSpaceOperation &op, std::shared_ptr<IDestructorCallback>) override;
    document::BucketId createBucketId(uint32_t lid) const;
};

struct MyStorer : public IOperationStorer {
    uint32_t _moveCnt;
    uint32_t _compactCnt;
    MyStorer()
        : _moveCnt(0),
          _compactCnt(0)
    {}
    void appendOperation(const FeedOperation &op, DoneCallback) override;
    CommitResult startCommit(DoneCallback) override;
};

struct MyFeedView : public proton::test::DummyFeedView {
    explicit MyFeedView(std::shared_ptr<const DocumentTypeRepo> repo)
        : proton::test::DummyFeedView(std::move(repo))
    {
    }
};

struct MyDocumentStore : public proton::test::DummyDocumentStore {
    Document::SP _readDoc;
    mutable uint32_t _readLid;
    MyDocumentStore();
    ~MyDocumentStore() override;
    document::Document::UP read(search::DocumentIdT lid, const document::DocumentTypeRepo &) const override;
};

struct MyDocumentRetriever : public DocumentRetrieverBaseForTest {
    std::shared_ptr<const DocumentTypeRepo> repo;
    const MyDocumentStore& store;
    MyDocumentRetriever(std::shared_ptr<const DocumentTypeRepo> repo_in, const MyDocumentStore& store_in) noexcept;
    ~MyDocumentRetriever();
    const document::DocumentTypeRepo& getDocumentTypeRepo() const override;
    void getBucketMetaData(const storage::spi::Bucket&, DocumentMetaData::Vector&) const override;
    DocumentMetaData getDocumentMetaData(const DocumentId&) const override;
    Document::UP getFullDocument(DocumentIdT lid) const override;
    CachedSelect::SP parseSelect(const vespalib::string&) const override;
};

struct MySubDb {
    std::unique_ptr<proton::test::DummyDocumentSubDb> sub_db;
    MaintenanceDocumentSubDB maintenance_sub_db;
    PendingLidTracker _pendingLidsForCommit;
    MySubDb(std::shared_ptr<bucketdb::BucketDBOwner> bucket_db, const MyDocumentStore& store, const std::shared_ptr<const DocumentTypeRepo> & repo);
    ~MySubDb();
};
