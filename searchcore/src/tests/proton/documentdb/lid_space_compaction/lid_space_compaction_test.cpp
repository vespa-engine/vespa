// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("lid_space_compaction_test");

#include <vespa/searchcore/proton/server/i_lid_space_compaction_handler.h>
#include <vespa/searchcore/proton/server/lid_space_compaction_handler.h>
#include <vespa/searchcore/proton/server/lid_space_compaction_job.h>
#include <vespa/searchcore/proton/test/clusterstatehandler.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/server/ifrozenbuckethandler.h>
#include <vespa/searchcore/proton/server/i_disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/server/imaintenancejobrunner.h>

using namespace document;
using namespace proton;
using namespace search;
using namespace search::index;
using namespace vespalib;
using storage::spi::Timestamp;

const uint32_t SUBDB_ID = 2;
const double JOB_DELAY = 1.0;
const uint32_t ALLOWED_LID_BLOAT = 1;
const double ALLOWED_LID_BLOAT_FACTOR = 0.3;
const uint32_t MAX_DOCS_TO_SCAN = 100;
constexpr double RESOURCE_LIMIT_FACTOR = 1.0;
const vespalib::string DOC_ID = "id:test:searchdocument::0";
const BucketId BUCKET_ID_1(1);
const BucketId BUCKET_ID_2(2);
const Timestamp TIMESTAMP_1(1);
const GlobalId GID_1;

typedef std::vector<uint32_t> LidVector;
typedef std::pair<uint32_t, uint32_t> LidPair;
typedef std::vector<LidPair> LidPairVector;

struct MyScanIterator : public IDocumentScanIterator
{
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
        for (uint32_t i = 0; i < maxDocsToScan && _itr != _lids.end() && (*_itr) <= compactLidLimit;
                ++i, ++_itr) {}
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

struct MyHandler : public ILidSpaceCompactionHandler
{
    std::vector<LidUsageStats> _stats;
    std::vector<LidVector> _lids;
    mutable uint32_t _moveFromLid;
    mutable uint32_t _moveToLid;
    uint32_t _handleMoveCnt;
    uint32_t _wantedSubDbId;
    uint32_t _wantedLidLimit;
    mutable uint32_t _iteratorCnt;

    MyHandler();
    ~MyHandler();
    virtual vespalib::string getName() const override {
        return "myhandler";
    }
    virtual uint32_t getSubDbId() const override { return 2; }
    virtual LidUsageStats getLidStatus() const override {
        ASSERT_TRUE(_handleMoveCnt < _stats.size());
        return _stats[_handleMoveCnt];
    }
    virtual IDocumentScanIterator::UP getIterator() const override {
        ASSERT_TRUE(_iteratorCnt < _lids.size());
        return IDocumentScanIterator::UP(new MyScanIterator(_lids[_iteratorCnt++]));
    }
    virtual MoveOperation::UP createMoveOperation(const search::DocumentMetaData &document,
                                                  uint32_t moveToLid) const override {
        ASSERT_TRUE(document.lid > moveToLid);
        _moveFromLid = document.lid;
        _moveToLid = moveToLid;
        return MoveOperation::UP(new MoveOperation());
    }
    virtual void handleMove(const MoveOperation &) override {
        ++_handleMoveCnt;
    }
    virtual void handleCompactLidSpace(const CompactLidSpaceOperation &op) override {
        _wantedSubDbId = op.getSubDbId();
        _wantedLidLimit = op.getLidLimit();
    }
};

MyHandler::MyHandler()
    : _stats(),
      _moveFromLid(0),
      _moveToLid(0),
      _handleMoveCnt(0),
      _wantedSubDbId(0),
      _wantedLidLimit(0),
      _iteratorCnt(0)
{}
MyHandler::~MyHandler() {}

struct MyStorer : public IOperationStorer
{
    uint32_t _moveCnt;
    uint32_t _compactCnt;
    MyStorer()
        : _moveCnt(0),
          _compactCnt(0)
    {}
    virtual void storeOperation(FeedOperation &op) override {
        if (op.getType() == FeedOperation::MOVE) {
            ++ _moveCnt;
        } else if (op.getType() == FeedOperation::COMPACT_LID_SPACE) {
            ++_compactCnt;
        }
    }
};

struct MyFrozenBucketHandler : public IFrozenBucketHandler
{
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

struct MyFeedView : public test::DummyFeedView
{
    MyFeedView(const DocumentTypeRepo::SP &repo)
        : test::DummyFeedView(repo)
    {
    }
};

struct MyDocumentStore : public test::DummyDocumentStore
{
    Document::SP _readDoc;
    mutable uint32_t _readLid;
    MyDocumentStore() : _readDoc(), _readLid(0) {}
    virtual document::Document::UP
    read(search::DocumentIdT lid, const document::DocumentTypeRepo &) const override {
        _readLid = lid;
        return Document::UP(_readDoc->clone());
    }
};

struct MySummaryManager : public test::DummySummaryManager
{
    MyDocumentStore _store;
    MySummaryManager() : _store() {}
    virtual search::IDocumentStore &getBackingStore() override { return _store; }
};

struct MySubDb : public test::DummyDocumentSubDb
{
    DocumentTypeRepo::SP _repo;
    MySubDb(const DocumentTypeRepo::SP &repo, std::shared_ptr<BucketDBOwner> bucketDB);
    ~MySubDb();
    virtual IFeedView::SP getFeedView() const override {
        return IFeedView::SP(new MyFeedView(_repo));
    }
};


MySubDb::MySubDb(const DocumentTypeRepo::SP &repo, std::shared_ptr<BucketDBOwner> bucketDB)
    : test::DummyDocumentSubDb(bucketDB, SUBDB_ID),
      _repo(repo)
{
    _summaryManager.reset(new MySummaryManager());
}
MySubDb::~MySubDb() {}

struct MyJobRunner : public IMaintenanceJobRunner
{
    IMaintenanceJob &_job;
    MyJobRunner(IMaintenanceJob &job)
        : _job(job)
    {
        _job.registerRunner(this);
    }
    virtual void run() override { _job.run(); }
};

struct JobFixture
{
    MyHandler _handler;
    MyStorer _storer;
    MyFrozenBucketHandler _frozenHandler;
    test::DiskMemUsageNotifier _diskMemUsageNotifier;
    test::ClusterStateHandler _clusterStateHandler;
    LidSpaceCompactionJob _job;
    MyJobRunner           _jobRunner;
    JobFixture(uint32_t allowedLidBloat = ALLOWED_LID_BLOAT,
               double allowedLidBloatFactor = ALLOWED_LID_BLOAT_FACTOR,
               uint32_t maxDocsToScan = MAX_DOCS_TO_SCAN,
               double resourceLimitFactor = RESOURCE_LIMIT_FACTOR,
               double interval = JOB_DELAY,
               bool nodeRetired = false)
        : _handler(),
          _job(DocumentDBLidSpaceCompactionConfig(interval,
                  allowedLidBloat, allowedLidBloatFactor, false, maxDocsToScan),
               _handler, _storer, _frozenHandler, _diskMemUsageNotifier, resourceLimitFactor,
               _clusterStateHandler, nodeRetired),
          _jobRunner(_job)
    {
    }
    ~JobFixture();
    JobFixture &addStats(uint32_t docIdLimit,
                         const LidVector &usedLids,
                         const LidPairVector &usedFreePairs) {
        return addMultiStats(docIdLimit, {usedLids}, usedFreePairs);
    }
    JobFixture &addMultiStats(uint32_t docIdLimit,
                              const std::vector<LidVector> &usedLidsVector,
                              const LidPairVector &usedFreePairs) {
        uint32_t numDocs = usedLidsVector[0].size();
        for (auto pair : usedFreePairs) {
            uint32_t highestUsedLid = pair.first;
            uint32_t lowestFreeLid = pair.second;
            _handler._stats.push_back(LidUsageStats
                    (docIdLimit, numDocs, lowestFreeLid, highestUsedLid));
        }
        _handler._lids = usedLidsVector;
        return *this;
    }
    JobFixture &addStats(uint32_t docIdLimit,
                         uint32_t numDocs,
                         uint32_t lowestFreeLid,
                         uint32_t highestUsedLid) {
        _handler._stats.push_back(LidUsageStats
                (docIdLimit, numDocs, lowestFreeLid, highestUsedLid));
        return *this;
    }
    JobFixture &setupWithOneDocumentToMove() {
        addStats(10, {1,3,4,5,6,9},
                 {{9,2},   // 30% bloat: move 9 -> 2
                  {6,7}}); // no documents to move
        return *this;
    }
    bool run() {
        return _job.run();
    }
    JobFixture &endScan() {
        EXPECT_FALSE(run());
        return *this;
    }
    JobFixture &compact() {
        EXPECT_TRUE(run());
        return *this;
    }
    void notifyNodeRetired(bool nodeRetired) {
        test::BucketStateCalculator::SP calc = std::make_shared<test::BucketStateCalculator>();
        calc->setNodeRetired(nodeRetired);
        _clusterStateHandler.notifyClusterStateChanged(calc);
    }
};

JobFixture::~JobFixture() {
}

struct HandlerFixture
{
    DocBuilder _docBuilder;
    std::shared_ptr<BucketDBOwner> _bucketDB;
    MySubDb _subDb;
    MySummaryManager &_summaryMgr;
    MyDocumentStore &_docStore;
    LidSpaceCompactionHandler _handler;
    HandlerFixture()
        : _docBuilder(Schema()),
          _bucketDB(std::make_shared<BucketDBOwner>()),
          _subDb(_docBuilder.getDocumentTypeRepo(), _bucketDB),
          _summaryMgr(static_cast<MySummaryManager &>(*_subDb.getSummaryManager())),
          _docStore(_summaryMgr._store),
          _handler(_subDb, "test")
    {
        _docStore._readDoc = _docBuilder.startDocument(DOC_ID).endDocument();
    }
};

bool
assertJobContext(uint32_t moveToLid,
                 uint32_t moveFromLid,
                 uint32_t handleMoveCnt,
                 uint32_t wantedLidLimit,
                 uint32_t compactStoreCnt,
                 const JobFixture &f)
{
    if (!EXPECT_EQUAL(moveToLid, f._handler._moveToLid)) return false;
    if (!EXPECT_EQUAL(moveFromLid, f._handler._moveFromLid)) return false;
    if (!EXPECT_EQUAL(handleMoveCnt, f._handler._handleMoveCnt)) return false;
    if (!EXPECT_EQUAL(handleMoveCnt, f._storer._moveCnt)) return false;
    if (!EXPECT_EQUAL(wantedLidLimit, f._handler._wantedLidLimit)) return false;
    if (!EXPECT_EQUAL(compactStoreCnt, f._storer._compactCnt)) return false;
    return true;
}

TEST_F("require that handler name is used as part of job name", JobFixture)
{
    EXPECT_EQUAL("lid_space_compaction.myhandler", f._job.getName());
}

TEST_F("require that no move operation is created if lid bloat factor is below limit", JobFixture)
{
    // 20% bloat < 30% allowed bloat
    f.addStats(10, {1,3,4,5,6,7,9}, {{9,2}});
    EXPECT_TRUE(f.run());
    EXPECT_TRUE(assertJobContext(0, 0, 0, 0, 0, f));
}

TEST("require that no move operation is created if lid bloat is below limit")
{
    JobFixture f(3, 0.1);
    // 20% bloat >= 10% allowed bloat BUT lid bloat (2) < allowed lid bloat (3)
    f.addStats(10, {1,3,4,5,6,7,9}, {{9,2}});
    EXPECT_TRUE(f.run());
    EXPECT_TRUE(assertJobContext(0, 0, 0, 0, 0, f));
}

TEST_F("require that no move operation is created and compaction is initiated", JobFixture)
{
    // no documents to move: lowestFreeLid(7) > highestUsedLid(6)
    f.addStats(10, {1,2,3,4,5,6}, {{6,7}});

    // must scan to find that no documents should be moved
    f.endScan().compact();
    EXPECT_TRUE(assertJobContext(0, 0, 0, 7, 1, f));
}

TEST_F("require that 1 move operation is created and compaction is initiated", JobFixture)
{
    f.setupWithOneDocumentToMove();
    EXPECT_FALSE(f.run()); // scan
    EXPECT_TRUE(assertJobContext(2, 9, 1, 0, 0, f));
    f.endScan().compact();
    EXPECT_TRUE(assertJobContext(2, 9, 1, 7, 1, f));
}

TEST_F("require that job returns false when multiple move operations or compaction are needed",
        JobFixture)
{
    f.addStats(10, {1,5,6,9,8,7},
            {{9,2},   // 30% bloat: move 9 -> 2
             {8,3},   // move 8 -> 3
             {7,4},   // move 7 -> 4
             {6,7}}); // no documents to move

    EXPECT_FALSE(f.run());
    EXPECT_TRUE(assertJobContext(2, 9, 1, 0, 0, f));
    EXPECT_FALSE(f.run());
    EXPECT_TRUE(assertJobContext(3, 8, 2, 0, 0, f));
    EXPECT_FALSE(f.run());
    EXPECT_TRUE(assertJobContext(4, 7, 3, 0, 0, f));
    f.endScan().compact();
    EXPECT_TRUE(assertJobContext(4, 7, 3, 7, 1, f));
}

TEST_F("require that job is blocked if trying to move document for frozen bucket", JobFixture)
{
    f._frozenHandler._bucket = BUCKET_ID_1;
    EXPECT_FALSE(f._job.isBlocked());
    f.addStats(10, {1,3,4,5,6,9}, {{9,2}}); // 30% bloat: try to move 9 -> 2
    f.addStats(0, 0, 0, 0);

    EXPECT_TRUE(f.run()); // bucket frozen
    EXPECT_TRUE(assertJobContext(0, 0, 0, 0, 0, f));
    EXPECT_TRUE(f._job.isBlocked());

    f._frozenHandler._bucket = BUCKET_ID_2;
    f._job.unBlock();

    EXPECT_FALSE(f.run()); // unblocked
    EXPECT_TRUE(assertJobContext(2, 9, 1, 0, 0, f));
    EXPECT_FALSE(f._job.isBlocked());
}

TEST_F("require that job handles invalid document meta data when max docs are scanned",
        JobFixture(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, 3))
{
    f.setupWithOneDocumentToMove();
    EXPECT_FALSE(f.run()); // does not find 9 in first scan
    EXPECT_TRUE(assertJobContext(0, 0, 0, 0, 0, f));
    EXPECT_FALSE(f.run()); // move 9 -> 2
    EXPECT_TRUE(assertJobContext(2, 9, 1, 0, 0, f));
    f.endScan().compact();
    EXPECT_TRUE(assertJobContext(2, 9, 1, 7, 1, f));
}

TEST_F("require that job can restart documents scan if lid bloat is still to large",
        JobFixture(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, 3))
{
    f.addMultiStats(10, {{1,3,4,5,6,9},{1,2,4,5,6,8}},
            {{9,2},   // 30% bloat: move 9 -> 2
             {8,3},   // move 8 -> 3 (this should trigger rescan as the set of used docs have changed)
             {6,7}}); // no documents to move

    EXPECT_FALSE(f.run()); // does not find 9 in first scan
    EXPECT_EQUAL(1u, f._handler._iteratorCnt);
    // We simulate that the set of used docs have changed between these 2 runs
    EXPECT_FALSE(f.run()); // move 9 -> 2
    f.endScan();
    EXPECT_TRUE(assertJobContext(2, 9, 1, 0, 0, f));
    EXPECT_EQUAL(2u, f._handler._iteratorCnt);
    EXPECT_FALSE(f.run()); // does not find 8 in first scan
    EXPECT_FALSE(f.run()); // move 8 -> 3
    EXPECT_TRUE(assertJobContext(3, 8, 2, 0, 0, f));
    f.endScan().compact();
    EXPECT_TRUE(assertJobContext(3, 8, 2, 7, 1, f));
}

TEST_F("require that handler uses doctype and subdb name", HandlerFixture)
{
    EXPECT_EQUAL("test.dummysubdb", f._handler.getName());
}

TEST_F("require that createMoveOperation() works as expected", HandlerFixture)
{
    const uint32_t moveToLid = 5;
    const uint32_t moveFromLid = 10;
    const BucketId bucketId(100);
    const Timestamp timestamp(200);
    DocumentMetaData document(moveFromLid, timestamp, bucketId, GlobalId());
    MoveOperation::UP op = f._handler.createMoveOperation(document, moveToLid);
    EXPECT_EQUAL(10u, f._docStore._readLid);
    EXPECT_EQUAL(DbDocumentId(SUBDB_ID, moveFromLid).toString(),
            op->getPrevDbDocumentId().toString()); // source
    EXPECT_EQUAL(DbDocumentId(SUBDB_ID, moveToLid).toString(),
            op->getDbDocumentId().toString()); // target
    EXPECT_EQUAL(DocumentId(DOC_ID), op->getDocument()->getId());
    EXPECT_EQUAL(bucketId, op->getBucketId());
    EXPECT_EQUAL(timestamp, op->getTimestamp());
}


TEST_F("require that held lid is not considered free, blocks job", JobFixture)
{
    // Lid 1 on hold or pendingHold, i.e. neither free nor used.
    f.addMultiStats(3, {{2}}, {{2, 3}});
    EXPECT_TRUE(f.run());
    EXPECT_TRUE(assertJobContext(0, 0, 0, 0, 0, f));
}

TEST_F("require that held lid is not considered free, only compact", JobFixture)
{
    // Lid 1 on hold or pendingHold, i.e. neither free nor used.
    f.addMultiStats(10, {{2}}, {{2, 3}});
    EXPECT_FALSE(f.run());
    EXPECT_TRUE(assertJobContext(0, 0, 0, 0, 0, f));
    f.compact();
    EXPECT_TRUE(assertJobContext(0, 0, 0, 3, 1, f));
}

TEST_F("require that held lids are not considered free, one move", JobFixture)
{
    // Lids 1,2,3 on hold or pendingHold, i.e. neither free nor used.
    f.addMultiStats(10, {{5}}, {{5, 4}, {4, 5}});
    EXPECT_FALSE(f.run());
    EXPECT_TRUE(assertJobContext(4, 5, 1, 0, 0, f));
    f.endScan().compact();
    EXPECT_TRUE(assertJobContext(4, 5, 1, 5, 1, f));
}

TEST_F("require that resource starvation blocks lid space compaction", JobFixture)
{
    f.setupWithOneDocumentToMove();
    TEST_DO(f._diskMemUsageNotifier.notify({{100, 0}, {100, 101}}));
    EXPECT_TRUE(f.run()); // scan
    TEST_DO(assertJobContext(0, 0, 0, 0, 0, f));
}

TEST_F("require that ending resource starvation resumes lid space compaction", JobFixture)
{
    f.setupWithOneDocumentToMove();
    TEST_DO(f._diskMemUsageNotifier.notify({{100, 0}, {100, 101}}));
    EXPECT_TRUE(f.run()); // scan
    TEST_DO(assertJobContext(0, 0, 0, 0, 0, f));
    TEST_DO(f._diskMemUsageNotifier.notify({{100, 0}, {100, 0}}));
    TEST_DO(assertJobContext(2, 9, 1, 0, 0, f));
    TEST_DO(f.endScan().compact());
    TEST_DO(assertJobContext(2, 9, 1, 7, 1, f));
}

TEST_F("require that resource limit factor adjusts limit", JobFixture(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, MAX_DOCS_TO_SCAN, 1.05))
{
    f.setupWithOneDocumentToMove();
    TEST_DO(f._diskMemUsageNotifier.notify({{100, 0}, {100, 101}}));
    EXPECT_FALSE(f.run()); // scan
    TEST_DO(assertJobContext(2, 9, 1, 0, 0, f));
    TEST_DO(f.endScan().compact());
    TEST_DO(assertJobContext(2, 9, 1, 7, 1, f));
}

struct JobFixtureWithInterval : public JobFixture {
    JobFixtureWithInterval(double interval)
        : JobFixture(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, MAX_DOCS_TO_SCAN, RESOURCE_LIMIT_FACTOR, interval)
    {}
};

TEST_F("require that delay is set based on interval and is max 300 secs", JobFixtureWithInterval(301))
{
    EXPECT_EQUAL(300, f._job.getDelay());
    EXPECT_EQUAL(301, f._job.getInterval());
}

TEST_F("require that delay is set based on interval and can be less than 300 secs", JobFixtureWithInterval(299))
{
    EXPECT_EQUAL(299, f._job.getDelay());
    EXPECT_EQUAL(299, f._job.getInterval());
}

struct JobFixtureWithNodeRetired : public JobFixture {
    JobFixtureWithNodeRetired(bool nodeRetired)
        : JobFixture(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, MAX_DOCS_TO_SCAN, RESOURCE_LIMIT_FACTOR, JOB_DELAY, nodeRetired)
    {}
};

TEST_F("require that job is disabled when node is retired", JobFixtureWithNodeRetired(true))
{
    f.setupWithOneDocumentToMove();
    EXPECT_TRUE(f.run()); // not runnable, no work to do
    TEST_DO(assertJobContext(0, 0, 0, 0, 0, f));
}

TEST_F("require that job is disabled when node becomes retired", JobFixtureWithNodeRetired(false))
{
    f.setupWithOneDocumentToMove();
    f.notifyNodeRetired(true);
    EXPECT_TRUE(f.run()); // not runnable, no work to do
    TEST_DO(assertJobContext(0, 0, 0, 0, 0, f));
}

TEST_F("require that job is re-enabled when node is no longer retired", JobFixtureWithNodeRetired(true))
{
    f.setupWithOneDocumentToMove();
    EXPECT_TRUE(f.run()); // not runnable, no work to do
    TEST_DO(assertJobContext(0, 0, 0, 0, 0, f));
    f.notifyNodeRetired(false); // triggers running of job
    TEST_DO(assertJobContext(2, 9, 1, 0, 0, f));
    TEST_DO(f.endScan().compact());
    TEST_DO(assertJobContext(2, 9, 1, 7, 1, f));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
