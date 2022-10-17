// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "lid_space_jobtest.h"
#include <vespa/searchcore/proton/server/lid_space_compaction_job.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/persistence/dummyimpl/dummy_bucket_executor.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using vespalib::RetainGuard;

using BlockedReason = IBlockableMaintenanceJob::BlockedReason;

struct MyDirectJobRunner : public IMaintenanceJobRunner {
    IMaintenanceJob &_job;
    explicit MyDirectJobRunner(IMaintenanceJob &job)
        : _job(job)
    {
        _job.registerRunner(this);
    }
    void run() override { _job.run(); }
};

struct MyCountJobRunner : public IMaintenanceJobRunner {
    uint32_t runCnt;
    explicit MyCountJobRunner(IMaintenanceJob &job) : runCnt(0) {
        job.registerRunner(this);
    }
    void run() override { ++runCnt; }
};

JobTestBase::JobTestBase()
    : _refCount(),
      _clusterStateHandler(),
      _diskMemUsageNotifier(),
      _handler(),
      _storer(),
      _job()
{
    init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, RESOURCE_LIMIT_FACTOR, JOB_DELAY, false, MAX_OUTSTANDING_MOVE_OPS);
}

void
JobTestBase::init(uint32_t allowedLidBloat,
          double allowedLidBloatFactor,
          double resourceLimitFactor,
          vespalib::duration interval,
          bool nodeRetired,
          uint32_t maxOutstandingMoveOps)
{
    _handler = std::make_shared<MyHandler>(maxOutstandingMoveOps != MAX_OUTSTANDING_MOVE_OPS, true);
    DocumentDBLidSpaceCompactionConfig compactCfg(interval, allowedLidBloat, allowedLidBloatFactor,
                                                  REMOVE_BATCH_BLOCK_RATE, REMOVE_BLOCK_RATE, false);
    BlockableMaintenanceJobConfig blockableCfg(resourceLimitFactor, maxOutstandingMoveOps);

    _job.reset();
    _singleExecutor = std::make_unique<vespalib::ThreadStackExecutor>(1, 0x10000);
    _master = std::make_unique<proton::SyncableExecutorThreadService> (*_singleExecutor);
    _bucketExecutor = std::make_unique<storage::spi::dummy::DummyBucketExecutor>(4);
    _job = lidspace::CompactionJob::create(compactCfg, RetainGuard(_refCount), _handler, _storer, *_master, *_bucketExecutor,
                                           _diskMemUsageNotifier, blockableCfg, _clusterStateHandler, nodeRetired,
                                           document::BucketSpace::placeHolder());
}

void
JobTestBase::sync() const {
    if (_bucketExecutor) {
        _bucketExecutor->sync();
        _master->sync();
    }
}

JobTestBase &
JobTestBase::addStats(uint32_t docIdLimit, const LidVector &usedLids, const LidPairVector &usedFreePairs)
{
    return addMultiStats(docIdLimit, {usedLids}, usedFreePairs);
}

JobTestBase &
JobTestBase::addMultiStats(uint32_t docIdLimit,
                           const std::vector<LidVector> &usedLidsVector,
                           const LidPairVector &usedFreePairs)
{
    uint32_t usedLids = usedLidsVector[0].size();
    for (auto pair : usedFreePairs) {
        uint32_t highestUsedLid = pair.first;
        uint32_t lowestFreeLid = pair.second;
        _handler->_stats.emplace_back(docIdLimit, usedLids, lowestFreeLid, highestUsedLid);
    }
    _handler->_lids = usedLidsVector;
    return *this;
}

JobTestBase &
JobTestBase::addStats(uint32_t docIdLimit, uint32_t numDocs, uint32_t lowestFreeLid, uint32_t highestUsedLid) {
    _handler->_stats.emplace_back(docIdLimit, numDocs, lowestFreeLid, highestUsedLid);
    return *this;
}

bool
JobTestBase::run() const {
    return _job->run();
}

JobTestBase &
JobTestBase::endScan() {
    EXPECT_FALSE(run());
    return *this;
}

JobTestBase &
JobTestBase::compact() {
    EXPECT_FALSE(run());
    EXPECT_TRUE(run());
    return *this;
}

void
JobTestBase::notifyNodeRetired(bool nodeRetired) {
    proton::test::BucketStateCalculator::SP calc = std::make_shared<proton::test::BucketStateCalculator>();
    calc->setNodeRetired(nodeRetired);
    _clusterStateHandler.notifyClusterStateChanged(calc);
}

void
JobTestBase::assertJobContext(uint32_t moveToLid,
                      uint32_t moveFromLid,
                      uint32_t handleMoveCnt,
                      uint32_t wantedLidLimit,
                      uint32_t compactStoreCnt) const
{
    sync();
    EXPECT_EQ(moveToLid, _handler->_moveToLid);
    EXPECT_EQ(moveFromLid, _handler->_moveFromLid);
    EXPECT_EQ(handleMoveCnt, _handler->_handleMoveCnt);
    EXPECT_EQ(handleMoveCnt, _storer._moveCnt);
    EXPECT_EQ(wantedLidLimit, _handler->_wantedLidLimit);
    EXPECT_EQ(compactStoreCnt, _storer._compactCnt);
}

void
JobTestBase::assertNoWorkDone() const {
    assertJobContext(0, 0, 0, 0, 0);
}

JobTestBase &
JobTestBase::setupOneDocumentToCompact() {
    addStats(10, {1,3,4,5,6,9},
             {{9,2},   // 30% bloat: move 9 -> 2
              {6,7}}); // no documents to move
    return *this;
}

void
JobTestBase::assertOneDocumentCompacted() {
    assertJobContext(2, 9, 1, 0, 0);
    endScan().compact();
    assertJobContext(2, 9, 1, 7, 1);
}

JobTestBase &
JobTestBase::setupThreeDocumentsToCompact() {
    addStats(10, {1,5,6,9,8,7},
             {{9,2},   // 30% bloat: move 9 -> 2
              {8,3},   // move 8 -> 3
              {7,4},   // move 7 -> 4
              {6,7}}); // no documents to move
    return *this;
}

JobTestBase::~JobTestBase() {
    _handler->clearMoveDoneContexts();
}

JobTest::JobTest()
    : JobTestBase(),
      _jobRunner(std::make_unique<MyDirectJobRunner>(*_job))
{}

JobTest::~JobTest() = default;

void
JobTest::init(uint32_t allowedLidBloat,
              double allowedLidBloatFactor,
              double resourceLimitFactor,
              vespalib::duration interval,
              bool nodeRetired,
              uint32_t maxOutstandingMoveOps)
{
    JobTestBase::init(allowedLidBloat, allowedLidBloatFactor, resourceLimitFactor, interval, nodeRetired, maxOutstandingMoveOps);
    _jobRunner = std::make_unique<MyDirectJobRunner>(*_job);
}

void
JobTest::init_with_interval(vespalib::duration interval) {
    init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, RESOURCE_LIMIT_FACTOR, interval);
}

void
JobTest::init_with_node_retired(bool retired) {
    init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR, RESOURCE_LIMIT_FACTOR, JOB_DELAY, retired);
}


JobDisabledByRemoveOpsTest::JobDisabledByRemoveOpsTest() : JobTest() {}
JobDisabledByRemoveOpsTest::~JobDisabledByRemoveOpsTest() = default;

void
JobDisabledByRemoveOpsTest::job_is_disabled_while_remove_ops_are_ongoing(bool remove_batch) {
    setupOneDocumentToCompact();
    _handler->run_remove_ops(remove_batch);
    EXPECT_TRUE(run()); // job is disabled
    assertNoWorkDone();
}

void
JobDisabledByRemoveOpsTest::job_becomes_disabled_if_remove_ops_starts(bool remove_batch) {
    setupThreeDocumentsToCompact();
    EXPECT_FALSE(run()); // job executed as normal (with more work to do)
    assertJobContext(2, 9, 1, 0, 0);

    _handler->run_remove_ops(remove_batch);
    EXPECT_TRUE(run()); // job is disabled
    assertJobContext(2, 9, 1, 0, 0);
}

void
JobDisabledByRemoveOpsTest::job_is_re_enabled_when_remove_ops_are_no_longer_ongoing(bool remove_batch) {
    job_becomes_disabled_if_remove_ops_starts(remove_batch);

    _handler->stop_remove_ops(remove_batch);
    EXPECT_FALSE(run()); // job executed as normal (with more work to do)
    assertJobContext(3, 8, 2, 0, 0);
}

MaxOutstandingJobTest::MaxOutstandingJobTest()
    : JobTest(),
      runner()
{}

MaxOutstandingJobTest::~MaxOutstandingJobTest() = default;

void
MaxOutstandingJobTest::init(uint32_t maxOutstandingMoveOps) {
    JobTest::init(ALLOWED_LID_BLOAT, ALLOWED_LID_BLOAT_FACTOR,
                  RESOURCE_LIMIT_FACTOR, JOB_DELAY, false, maxOutstandingMoveOps);
    runner = std::make_unique<MyCountJobRunner>(*_job);
}

void
MaxOutstandingJobTest::assertRunToBlocked() {
    EXPECT_TRUE(run()); // job becomes blocked as max outstanding limit is reached
    EXPECT_TRUE(_job->isBlocked());
    EXPECT_TRUE(_job->isBlocked(BlockedReason::OUTSTANDING_OPS));
}

void
MaxOutstandingJobTest::assertRunToNotBlocked() {
    EXPECT_FALSE(run());
    EXPECT_FALSE(_job->isBlocked());
}

void
MaxOutstandingJobTest::unblockJob(uint32_t expRunnerCnt) {
    _handler->clearMoveDoneContexts(); // unblocks job and try to execute it via runner
    EXPECT_EQ(expRunnerCnt, runner->runCnt);
    EXPECT_FALSE(_job->isBlocked());
}

