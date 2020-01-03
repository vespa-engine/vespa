// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("lid_space_compaction_test");

#include <vespa/searchcore/proton/server/i_disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/server/i_lid_space_compaction_handler.h>
#include <vespa/searchcore/proton/server/ifrozenbuckethandler.h>
#include <vespa/searchcore/proton/server/imaintenancejobrunner.h>
#include <vespa/searchcore/proton/server/lid_space_compaction_handler.h>
#include <vespa/searchcore/proton/server/lid_space_compaction_job.h>
#include <vespa/searchcore/proton/test/clusterstatehandler.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace document;
using namespace proton;
using namespace search::index;
using namespace search;
using namespace vespalib;
using search::IDestructorCallback;
using storage::spi::Timestamp;
using BlockedReason = IBlockableMaintenanceJob::BlockedReason;
using TimePoint = LidUsageStats::TimePoint;

constexpr uint32_t SUBDB_ID = 2;
constexpr vespalib::duration JOB_DELAY = 1s;
constexpr uint32_t ALLOWED_LID_BLOAT = 1;
constexpr double ALLOWED_LID_BLOAT_FACTOR = 0.3;
constexpr vespalib::duration REMOVE_BATCH_BLOCK_DELAY = 20s;
constexpr uint32_t MAX_DOCS_TO_SCAN = 100;
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
    MyScanIterator(const LidVector &lids) : _lids(lids), _itr(_lids.begin()), _validItr(true) {}
    virtual bool valid() const override {
        return _validItr;
    }
    virtual search::DocumentMetaData next(uint32_t compactLidLimit,
                                          uint32_t maxDocsToScan,
                                          bool retry) override {
        if (!retry && _itr != _lids.begin()) {
            ++_itr;
        }
        for (uint32_t i = 0; i < maxDocsToScan && _itr != _lids.end() && (*_itr) <= compactLidLimit; ++i, ++_itr) {}
        if (_itr != _lids.end()) {
            uint32_t lid = *_itr;
            if (lid > compactLidLimit) {
                return search::DocumentMetaData(lid, TIMESTAMP_1, BUCKET_ID_1, GID_1);
            }
        } else {
            _validItr = false;
        }
        return search::DocumentMetaData();
    }
};

struct MyHandler : public ILidSpaceCompactionHandler {
    std::vector<LidUsageStats> _stats;
    std::vector<LidVector> _lids;
    mutable uint32_t _moveFromLid;
    mutable uint32_t _moveToLid;
    uint32_t _handleMoveCnt;
    uint32_t _wantedSubDbId;
    uint32_t _wantedLidLimit;
    mutable uint32_t _iteratorCnt;
    bool _storeMoveDoneContexts;
    std::vector<IDestructorCallback::SP> _moveDoneContexts;

    MyHandler(bool storeMoveDoneContexts = false);
    ~MyHandler();
    void clearMoveDoneContexts() { _moveDoneContexts.clear(); }
    void set_last_remove_batch(TimePoint last_remove_batch) {
        for (auto& s : _stats) {
            s = LidUsageStats(s.getLidLimit(), s.getUsedLids(),
                              s.getLowestFreeLid(), s.getHighestUsedLid(), last_remove_batch);
        }
    }
    virtual vespalib::string getName() const override {
        return "myhandler";
    }
    virtual uint32_t getSubDbId() const override { return 2; }
    virtual LidUsageStats getLidStatus() const override {
        assert(_handleMoveCnt < _stats.size());
        return _stats[_handleMoveCnt];
    }
    virtual IDocumentScanIterator::UP getIterator() const override {
        assert(_iteratorCnt < _lids.size());
        return IDocumentScanIterator::UP(new MyScanIterator(_lids[_iteratorCnt++]));
    }
    virtual MoveOperation::UP createMoveOperation(const search::DocumentMetaData &document,
                                                  uint32_t moveToLid) const override {
        assert(document.lid > moveToLid);
        _moveFromLid = document.lid;
        _moveToLid = moveToLid;
        return MoveOperation::UP(new MoveOperation());
    }
    virtual void handleMove(const MoveOperation &, IDestructorCallback::SP moveDoneCtx) override {
        ++_handleMoveCnt;
        if (_storeMoveDoneContexts) {
            _moveDoneContexts.push_back(std::move(moveDoneCtx));
        }
    }
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &op) override {
        _wantedSubDbId = op.getSubDbId();
        _wantedLidLimit = op.getLidLimit();
    }
};

MyHandler::MyHandler(bool storeMoveDoneContexts)
    : _stats(),
      _moveFromLid(0),
      _moveToLid(0),
      _handleMoveCnt(0),
      _wantedSubDbId(0),
      _wantedLidLimit(0),
      _iteratorCnt(0),
      _storeMoveDoneContexts(storeMoveDoneContexts),
      _moveDoneContexts()
{}

MyHandler::~MyHandler() {}

struct MyStorer : public IOperationStorer {
    uint32_t _moveCnt;
    uint32_t _compactCnt;
    MyStorer()
        : _moveCnt(0),
          _compactCnt(0)
    {}
    void storeOperation(const FeedOperation &op, DoneCallback) override {
        if (op.getType() == FeedOperation::MOVE) {
            ++ _moveCnt;
        } else if (op.getType() == FeedOperation::COMPACT_LID_SPACE) {
            ++_compactCnt;
        }
    }
};

struct MyFrozenBucketHandler : public IFrozenBucketHandler {
    BucketId _bucket;
    MyFrozenBucketHandler() : _bucket() {}
    virtual ExclusiveBucketGuard::UP acquireExclusiveBucket(BucketId bucket) override {
        return (_bucket == bucket)
               ? ExclusiveBucketGuard::UP()
               : std::make_unique<ExclusiveBucketGuard>(bucket);
    }
    virtual void addListener(IBucketFreezeListener *) override { }
    virtual void removeListener(IBucketFreezeListener *) override { }
};

struct MyFeedView : public test::DummyFeedView {
    MyFeedView(std::shared_ptr<const DocumentTypeRepo> repo)
        : test::DummyFeedView(std::move(repo))
    {
    }
};

struct MyDocumentStore : public test::DummyDocumentStore {
    Document::SP _readDoc;
    mutable uint32_t _readLid;
    MyDocumentStore() : _readDoc(), _readLid(0) {}
    ~MyDocumentStore();
    document::Document::UP read(search::DocumentIdT lid, const document::DocumentTypeRepo &) const override {
        _readLid = lid;
        return Document::UP(_readDoc->clone());
    }
};

MyDocumentStore::~MyDocumentStore() = default;

struct MyDocumentRetriever : public DocumentRetrieverBaseForTest {
    std::shared_ptr<const DocumentTypeRepo> repo;
    const MyDocumentStore& store;
    MyDocumentRetriever(std::shared_ptr<const DocumentTypeRepo> repo_in, const MyDocumentStore& store_in)
        : repo(repo_in),
          store(store_in)
    {
    }
    const document::DocumentTypeRepo& getDocumentTypeRepo() const override { return *repo; }
    void getBucketMetaData(const storage::spi::Bucket&, DocumentMetaData::Vector&) const override { abort(); }
    DocumentMetaData getDocumentMetaData(const DocumentId&) const override { abort(); }
    Document::UP getDocument(DocumentIdT lid) const override {
        return store.read(lid, *repo);
    }
    CachedSelect::SP parseSelect(const vespalib::string&) const override { abort(); }
};

struct MySubDb {
    test::DummyDocumentSubDb sub_db;
    MaintenanceDocumentSubDB maintenance_sub_db;
    MySubDb(std::shared_ptr<BucketDBOwner> bucket_db, const MyDocumentStore& store, std::shared_ptr<const DocumentTypeRepo> repo);
    ~MySubDb();
};

MySubDb::MySubDb(std::shared_ptr<BucketDBOwner> bucket_db, const MyDocumentStore& store, std::shared_ptr<const DocumentTypeRepo> repo)
    : sub_db(std::move(bucket_db), SUBDB_ID),
      maintenance_sub_db(sub_db.getName(), sub_db.getSubDbId(), sub_db.getDocumentMetaStoreContext().getSP(),
                         std::make_shared<MyDocumentRetriever>(repo, store),
                         std::make_shared<MyFeedView>(repo))
{
}

MySubDb::~MySubDb() = default;

struct MyDirectJobRunner : public IMaintenanceJobRunner {
    IMaintenanceJob &_job;
    MyDirectJobRunner(IMaintenanceJob &job)
        : _job(job)
    {
        _job.registerRunner(this);
    }
    virtual void run() override { _job.run(); }
};

struct MyCountJobRunner : public IMaintenanceJobRunner {
    uint32_t runCnt;
    MyCountJobRunner(IMaintenanceJob &job) : runCnt(0) {
        job.registerRunner(this);
    }
    virtual void run() override { ++runCnt; }
};

struct JobTestBase : public ::testing::Test {
    std::unique_ptr<MyHandler> _handler;
    MyStorer _storer;
    MyFrozenBucketHandler _frozenHandler;
    test::DiskMemUsageNotifier _diskMemUsageNotifier;
    test::ClusterStateHandler _clusterStateHandler;
    std::unique_ptr<LidSpaceCompactionJob> _job;
    JobTestBase()
        : _handler(),
          _storer(),
          _frozenHandler(),
          _diskMemUsageNotifier(),
          _clusterStateHandler(),
          _job()
    {
        init();
    }
    void init(uint32_t allowedLidBloat = ALLOWED_LID_BLOAT,
              double allowedLidBloatFactor = ALLOWED_LID_BLOAT_FACTOR,
              uint32_t maxDocsToScan = MAX_DOCS_TO_SCAN,
              double resourceLimitFactor = RESOURCE_LIMIT_FACTOR,
              vespalib::duration interval = JOB_DELAY,
              bool nodeRetired = false,
              uint32_t maxOutstandingMoveOps = MAX_OUTSTANDING_MOVE_OPS)
    {
        _handler = std::make_unique<MyHandler>(maxOutstandingMoveOps != MAX_OUTSTANDING_MOVE_OPS);
        _job = std::make_unique<LidSpaceCompactionJob>(DocumentDBLidSpaceCompactionConfig(interval, allowedLidBloat,
                                                                                          allowedLidBloatFactor,
                                                                                          REMOVE_BATCH_BLOCK_DELAY,
                                                                                          false, maxDocsToScan),
                                                       *_handler, _storer, _frozenHandler, _diskMemUsageNotifier,
                                                       BlockableMaintenanceJobConfig(resourceLimitFactor, maxOutstandingMoveOps),
                                                       _clusterStateHandler, nodeRetired);
    }
    ~JobTestBase();
    JobTestBase &addStats(uint32_t docIdLimit,
                          const LidVector &usedLids,
                          const LidPairVector &usedFreePairs,
                          TimePoint last_remove_batch = TimePoint()) {
        return addMultiStats(docIdLimit, {usedLids}, usedFreePairs, last_remove_batch);
    }
    JobTestBase &addMultiStats(uint32_t docIdLimit,
                              const std::vector<LidVector> &usedLidsVector,
                              const LidPairVector &usedFreePairs,
                              TimePoint last_remove_batch = TimePoint()) {
        uint32_t usedLids = usedLidsVector[0].size();
        for (auto pair : usedFreePairs) {
            uint32_t highestUsedLid = pair.first;
            uint32_t lowestFreeLid = pair.second;
            _handler->_stats.push_back(LidUsageStats
                    (docIdLimit, usedLids, lowestFreeLid, highestUsedLid, last_remove_batch));
        }
        _handler->_lids = usedLidsVector;
        return *this;
    }
    JobTestBase &addStats(uint32_t docIdLimit,
                          uint32_t numDocs,
                          uint32_t lowestFreeLid,
                          uint32_t highestUsedLid) {
        _handler->_stats.push_back(LidUsageStats
                (docIdLimit, numDocs, lowestFreeLid, highestUsedLid, TimePoint()));
        return *this;
    }
    bool run() {
        return _job->run();
    }
    JobTestBase &endScan() {
        EXPECT_FALSE(run());
        return *this;
    }
    JobTestBase &compact() {
        EXPECT_TRUE(run());
        return *this;
    }
    void notifyNodeRetired(bool nodeRetired) {
        test::BucketStateCalculator::SP calc = std::make_shared<test::BucketStateCalculator>();
        calc->setNodeRetired(nodeRetired);
        _clusterStateHandler.notifyClusterStateChanged(calc);
    }
    void assertJobContext(uint32_t moveToLid,
                          uint32_t moveFromLid,
                          uint32_t handleMoveCnt,
                          uint32_t wantedLidLimit,
                          uint32_t compactStoreCnt)
    {
        EXPECT_EQ(moveToLid, _handler->_moveToLid);
        EXPECT_EQ(moveFromLid, _handler->_moveFromLid);
        EXPECT_EQ(handleMoveCnt, _handler->_handleMoveCnt);
        EXPECT_EQ(handleMoveCnt, _storer._moveCnt);
        EXPECT_EQ(wantedLidLimit, _handler->_wantedLidLimit);
        EXPECT_EQ(compactStoreCnt, _storer._compactCnt);
    }
    void assertNoWorkDone() {
        assertJobContext(0, 0, 0, 0, 0);
    }
    JobTestBase &setupOneDocumentToCompact(TimePoint last_remove_batch = TimePoint()) {
        addStats(10, {1,3,4,5,6,9},
                 {{9,2},   // 30% bloat: move 9 -> 2
                  {6,7}}, // no documents to move
                 last_remove_batch);
        return *this;
    }
    void assertOneDocumentCompacted() {
        assertJobContext(2, 9, 1, 0, 0);
        endScan().compact();
        assertJobContext(2, 9, 1, 7, 1);
    }
    JobTestBase &setupThreeDocumentsToCompact() {
        addStats(10, {1,5,6,9,8,7},
                 {{9,2},   // 30% bloat: move 9 -> 2
                  {8,3},   // move 8 -> 3
                  {7,4},   // move 7 -> 4
                  {6,7}}); // no documents to move
        return *this;
    }
};

JobTestBase::~JobTestBase() = default;

struct JobTest : public JobTestBase {
    std::unique_ptr<MyDirectJobRunner> _jobRunner;

    JobTest()
        : JobTestBase(),
          _jobRunner(std::make_unique<MyDirectJobRunner>(*_job))
    {}
    void init(uint32_t allowedLidBloat = ALLOWED_LID_BLOAT,
              double allowedLidBloatFactor = ALLOWED_LID_BLOAT_FACTOR,
              uint32_t maxDocsToScan = MAX_DOCS_TO_SCAN,
              double resourceLimitFactor = RESOURCE_LIMIT_FACTOR,
              vespalib::duration interval = JOB_DELAY,
              bool nodeRetired = false,
              uint32_t maxOutstandingMoveOps = MAX_OUTSTANDING_MOVE_OPS) {
        JobTestBase::init(allowedLidBloat, allowedLidBloatFactor, maxDocsToScan, resourceLimitFactor, interval, nodeRetired, maxOutstandingMoveOps);
        _jobRunner = std::make_unique<MyDirectJobRunner>(*_job);
    }
    void init_with_interval(vespalib::duration interval) {
        init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, MAX_DOCS_TO_SCAN, RESOURCE_LIMIT_FACTOR, interval);
    }
    void init_with_node_retired(bool retired) {
        init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, MAX_DOCS_TO_SCAN, RESOURCE_LIMIT_FACTOR, JOB_DELAY, retired);
    }
};

struct HandlerTest : public ::testing::Test {
    DocBuilder _docBuilder;
    std::shared_ptr<BucketDBOwner> _bucketDB;
    MyDocumentStore _docStore;
    MySubDb _subDb;
    LidSpaceCompactionHandler _handler;
    HandlerTest()
        : _docBuilder(Schema()),
          _bucketDB(std::make_shared<BucketDBOwner>()),
          _docStore(),
          _subDb(_bucketDB, _docStore, _docBuilder.getDocumentTypeRepo()),
          _handler(_subDb.maintenance_sub_db, "test")
    {
        _docStore._readDoc = _docBuilder.startDocument(DOC_ID).endDocument();
    }
};

TEST_F(JobTest, handler_name_is_used_as_part_of_job_name)
{
    EXPECT_EQ("lid_space_compaction.myhandler", _job->getName());
}

TEST_F(JobTest, no_move_operation_is_created_if_lid_bloat_factor_is_below_limit)
{
    // 20% bloat < 30% allowed bloat
    addStats(10, {1,3,4,5,6,7,9}, {{9,2}});
    EXPECT_TRUE(run());
    assertNoWorkDone();
}

TEST_F(JobTest, no_move_operation_is_created_if_lid_bloat_is_below_limit)
{
    init(3, 0.1);
    // 20% bloat >= 10% allowed bloat BUT lid bloat (2) < allowed lid bloat (3)
    addStats(10, {1,3,4,5,6,7,9}, {{9,2}});
    EXPECT_TRUE(run());
    assertNoWorkDone();
}

TEST_F(JobTest, no_move_operation_is_created_and_compaction_is_initiated)
{
    // no documents to move: lowestFreeLid(7) > highestUsedLid(6)
    addStats(10, {1,2,3,4,5,6}, {{6,7}});

    // must scan to find that no documents should be moved
    endScan().compact();
    assertJobContext(0, 0, 0, 7, 1);
}

TEST_F(JobTest, one_move_operation_is_created_and_compaction_is_initiated)
{
    setupOneDocumentToCompact();
    EXPECT_FALSE(run()); // scan
    assertOneDocumentCompacted();
}

TEST_F(JobTest, job_returns_false_when_multiple_move_operations_or_compaction_are_needed)
{
    setupThreeDocumentsToCompact();
    EXPECT_FALSE(run());
    assertJobContext(2, 9, 1, 0, 0);
    EXPECT_FALSE(run());
    assertJobContext(3, 8, 2, 0, 0);
    EXPECT_FALSE(run());
    assertJobContext(4, 7, 3, 0, 0);
    endScan().compact();
    assertJobContext(4, 7, 3, 7, 1);
}

TEST_F(JobTest, job_is_blocked_if_trying_to_move_document_for_frozen_bucket)
{
    _frozenHandler._bucket = BUCKET_ID_1;
    EXPECT_FALSE(_job->isBlocked());
    addStats(10, {1,3,4,5,6,9}, {{9,2}}); // 30% bloat: try to move 9 -> 2
    addStats(0, 0, 0, 0);

    EXPECT_TRUE(run()); // bucket frozen
    assertNoWorkDone();
    EXPECT_TRUE(_job->isBlocked());

    _frozenHandler._bucket = BUCKET_ID_2;
    _job->unBlock(BlockedReason::FROZEN_BUCKET);

    EXPECT_FALSE(run()); // unblocked
    assertJobContext(2, 9, 1, 0, 0);
    EXPECT_FALSE(_job->isBlocked());
}

TEST_F(JobTest, job_handles_invalid_document_meta_data_when_max_docs_are_scanned)
{
    init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, 3);
    setupOneDocumentToCompact();
    EXPECT_FALSE(run()); // does not find 9 in first scan
    assertNoWorkDone();
    EXPECT_FALSE(run()); // move 9 -> 2
    assertOneDocumentCompacted();
}

TEST_F(JobTest, job_can_restart_documents_scan_if_lid_bloat_is_still_to_large)
{
    init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, 3);
    addMultiStats(10, {{1,3,4,5,6,9},{1,2,4,5,6,8}},
                  {{9,2},   // 30% bloat: move 9 -> 2
                   {8,3},   // move 8 -> 3 (this should trigger rescan as the set of used docs have changed)
                   {6,7}}); // no documents to move

    EXPECT_FALSE(run()); // does not find 9 in first scan
    EXPECT_EQ(1u, _handler->_iteratorCnt);
    // We simulate that the set of used docs have changed between these 2 runs
    EXPECT_FALSE(run()); // move 9 -> 2
    endScan();
    assertJobContext(2, 9, 1, 0, 0);
    EXPECT_EQ(2u, _handler->_iteratorCnt);
    EXPECT_FALSE(run()); // does not find 8 in first scan
    EXPECT_FALSE(run()); // move 8 -> 3
    assertJobContext(3, 8, 2, 0, 0);
    endScan().compact();
    assertJobContext(3, 8, 2, 7, 1);
}

TEST_F(HandlerTest, handler_uses_doctype_and_subdb_name)
{
    EXPECT_EQ("test.dummysubdb", _handler.getName());
}

TEST_F(HandlerTest, createMoveOperation_works_as_expected)
{
    const uint32_t moveToLid = 5;
    const uint32_t moveFromLid = 10;
    const BucketId bucketId(100);
    const Timestamp timestamp(200);
    DocumentMetaData document(moveFromLid, timestamp, bucketId, GlobalId());
    MoveOperation::UP op = _handler.createMoveOperation(document, moveToLid);
    EXPECT_EQ(10u, _docStore._readLid);
    EXPECT_EQ(DbDocumentId(SUBDB_ID, moveFromLid).toString(),
              op->getPrevDbDocumentId().toString()); // source
    EXPECT_EQ(DbDocumentId(SUBDB_ID, moveToLid).toString(),
              op->getDbDocumentId().toString()); // target
    EXPECT_EQ(DocumentId(DOC_ID), op->getDocument()->getId());
    EXPECT_EQ(bucketId, op->getBucketId());
    EXPECT_EQ(timestamp, op->getTimestamp());
}

TEST_F(JobTest, held_lid_is_not_considered_free_and_blocks_job)
{
    // Lid 1 on hold or pendingHold, i.e. neither free nor used.
    addMultiStats(3, {{2}}, {{2, 3}});
    EXPECT_TRUE(run());
    assertNoWorkDone();
}

TEST_F(JobTest, held_lid_is_not_considered_free_with_only_compact)
{
    // Lid 1 on hold or pendingHold, i.e. neither free nor used.
    addMultiStats(10, {{2}}, {{2, 3}});
    EXPECT_FALSE(run());
    assertNoWorkDone();
    compact();
    assertJobContext(0, 0, 0, 3, 1);
}

TEST_F(JobTest, held_lids_are_not_considered_free_with_one_move)
{
    // Lids 1,2,3 on hold or pendingHold, i.e. neither free nor used.
    addMultiStats(10, {{5}}, {{5, 4}, {4, 5}});
    EXPECT_FALSE(run());
    assertJobContext(4, 5, 1, 0, 0);
    endScan().compact();
    assertJobContext(4, 5, 1, 5, 1);
}

TEST_F(JobTest, resource_starvation_blocks_lid_space_compaction)
{
    setupOneDocumentToCompact();
    _diskMemUsageNotifier.notify({{100, 0}, {100, 101}});
    EXPECT_TRUE(run()); // scan
    assertNoWorkDone();
}

TEST_F(JobTest, ending_resource_starvation_resumes_lid_space_compaction)
{
    setupOneDocumentToCompact();
    _diskMemUsageNotifier.notify({{100, 0}, {100, 101}});
    EXPECT_TRUE(run()); // scan
    assertNoWorkDone();
    _diskMemUsageNotifier.notify({{100, 0}, {100, 0}});
    assertOneDocumentCompacted();
}

TEST_F(JobTest, resource_limit_factor_adjusts_limit)
{
    init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, MAX_DOCS_TO_SCAN, 1.05);
    setupOneDocumentToCompact();
    _diskMemUsageNotifier.notify({{100, 0}, {100, 101}});
    EXPECT_FALSE(run()); // scan
    assertOneDocumentCompacted();
}

TEST_F(JobTest, delay_is_set_based_on_interval_and_is_max_300_secs)
{
    init_with_interval(301s);
    EXPECT_EQ(300s, _job->getDelay());
    EXPECT_EQ(301s, _job->getInterval());
}

TEST_F(JobTest, delay_is_set_based_on_interval_and_can_be_less_than_300_secs)
{
    init_with_interval(299s);
    EXPECT_EQ(299s, _job->getDelay());
    EXPECT_EQ(299s, _job->getInterval());
}

TEST_F(JobTest, job_is_disabled_when_node_is_retired)
{
    init_with_node_retired(true);
    setupOneDocumentToCompact();
    EXPECT_TRUE(run()); // not runnable, no work to do
    assertNoWorkDone();
}

TEST_F(JobTest, job_is_disabled_when_node_becomes_retired)
{
    init_with_node_retired(false);
    setupOneDocumentToCompact();
    notifyNodeRetired(true);
    EXPECT_TRUE(run()); // not runnable, no work to do
    assertNoWorkDone();
}

TEST_F(JobTest, job_is_re_enabled_when_node_is_no_longer_retired)
{
    init_with_node_retired(true);
    setupOneDocumentToCompact();
    EXPECT_TRUE(run()); // not runnable, no work to do
    assertNoWorkDone();
    notifyNodeRetired(false); // triggers running of job
    assertOneDocumentCompacted();
}

TEST_F(JobTest, job_is_disabled_while_remove_batch_is_ongoing)
{
    TimePoint last_remove_batch = std::chrono::steady_clock::now();
    setupOneDocumentToCompact(last_remove_batch);
    EXPECT_TRUE(run()); // job is disabled
    assertNoWorkDone();
}

TEST_F(JobTest, job_becomes_disabled_if_remove_batch_starts)
{
    setupThreeDocumentsToCompact();
    EXPECT_FALSE(run()); // job executed as normal (with more work to do)
    assertJobContext(2, 9, 1, 0, 0);

    _handler->set_last_remove_batch(std::chrono::steady_clock::now());
    EXPECT_TRUE(run()); // job is disabled
    assertJobContext(2, 9, 1, 0, 0);
}

TEST_F(JobTest, job_is_re_enabled_when_remove_batch_is_no_longer_ongoing)
{
    setupThreeDocumentsToCompact();
    EXPECT_FALSE(run()); // job executed as normal (with more work to do)
    assertJobContext(2, 9, 1, 0, 0);

    TimePoint last_remove_batch = std::chrono::steady_clock::now();
    _handler->set_last_remove_batch(last_remove_batch);
    EXPECT_TRUE(run()); // job is disabled
    assertJobContext(2, 9, 1, 0, 0);

    _handler->set_last_remove_batch(last_remove_batch - REMOVE_BATCH_BLOCK_DELAY);
    EXPECT_FALSE(run()); // job executed as normal (with more work to do)
    assertJobContext(3, 8, 2, 0, 0);
}

struct MaxOutstandingJobTest : public JobTest {
    std::unique_ptr<MyCountJobRunner> runner;
    MaxOutstandingJobTest()
        : JobTest(),
          runner()
    {}
    void init(uint32_t maxOutstandingMoveOps) {
        JobTest::init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, MAX_DOCS_TO_SCAN,
                      RESOURCE_LIMIT_FACTOR, JOB_DELAY, false, maxOutstandingMoveOps);
        runner = std::make_unique<MyCountJobRunner>(*_job);
    }
    void assertRunToBlocked() {
        EXPECT_TRUE(run()); // job becomes blocked as max outstanding limit is reached
        EXPECT_TRUE(_job->isBlocked());
        EXPECT_TRUE(_job->isBlocked(BlockedReason::OUTSTANDING_OPS));
    }
    void assertRunToNotBlocked() {
        EXPECT_FALSE(run());
        EXPECT_FALSE(_job->isBlocked());
    }
    void unblockJob(uint32_t expRunnerCnt) {
        _handler->clearMoveDoneContexts(); // unblocks job and try to execute it via runner
        EXPECT_EQ(expRunnerCnt, runner->runCnt);
        EXPECT_FALSE(_job->isBlocked());
    }
};

TEST_F(MaxOutstandingJobTest, job_is_blocked_if_it_has_too_many_outstanding_move_operations_with_max_1)
{
    init(1);
    setupThreeDocumentsToCompact();

    assertRunToBlocked();
    assertJobContext(2, 9, 1, 0, 0);
    assertRunToBlocked();
    assertJobContext(2, 9, 1, 0, 0);

    unblockJob(1);
    assertRunToBlocked();
    assertJobContext(3, 8, 2, 0, 0);

    unblockJob(2);
    assertRunToBlocked();
    assertJobContext(4, 7, 3, 0, 0);

    unblockJob(3);
    endScan().compact();
    assertJobContext(4, 7, 3, 7, 1);
}

TEST_F(MaxOutstandingJobTest, job_is_blocked_if_it_has_too_many_outstanding_move_operations_with_max_2)
{
    init(2);
    setupThreeDocumentsToCompact();

    assertRunToNotBlocked();
    assertJobContext(2, 9, 1, 0, 0);
    assertRunToBlocked();
    assertJobContext(3, 8, 2, 0, 0);

    unblockJob(1);
    assertRunToNotBlocked();
    assertJobContext(4, 7, 3, 0, 0);
    endScan().compact();
    assertJobContext(4, 7, 3, 7, 1);
}

GTEST_MAIN_RUN_ALL_TESTS()
