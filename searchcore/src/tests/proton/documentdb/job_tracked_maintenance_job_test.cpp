// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.


#include <vespa/searchcore/proton/server/i_blockable_maintenance_job.h>
#include <vespa/searchcore/proton/server/job_tracked_maintenance_job.h>
#include <vespa/searchcore/proton/test/simple_job_tracker.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using namespace proton;
using namespace vespalib;
using test::SimpleJobTracker;
using GateUP = std::unique_ptr<Gate>;
using GateVector = std::vector<GateUP>;
using BlockedReason = IBlockableMaintenanceJob::BlockedReason;

namespace job_tracked_maintenance_job_test {

GateVector
getGateVector(size_t size)
{
    GateVector retval;
    for (size_t i = 0; i < size; ++i) {
        retval.push_back(std::make_unique<Gate>());
    }
    return retval;
}

struct MyMaintenanceJob : public IBlockableMaintenanceJob
{
    GateVector _runGates;
    size_t     _runIdx;
    bool       _blocked;
    MyMaintenanceJob(size_t numRuns)
        : IBlockableMaintenanceJob("myjob", 10s, 20s),
          _runGates(getGateVector(numRuns)),
          _runIdx(0),
          _blocked(false)
    {}
    void block() { setBlocked(BlockedReason::RESOURCE_LIMITS); }
    void unBlock() { unBlock(BlockedReason::RESOURCE_LIMITS); }
    void setBlocked(BlockedReason) override { _blocked = true; }
    void unBlock(BlockedReason) override { _blocked = false; }
    bool isBlocked() const override { return _blocked; }
    bool run() override {
        _runGates[_runIdx++]->await(5s);
        return _runIdx == _runGates.size();
    }
    void onStop() override { }
};

struct Fixture
{
    SimpleJobTracker::SP _tracker;
    IMaintenanceJob::UP _job;
    MyMaintenanceJob *_myJob;
    IMaintenanceJob::UP _trackedJob;
    bool _runRetval;
    GateVector _runGates;
    size_t _runIdx;
    ThreadStackExecutor _exec;

    Fixture(size_t numRuns = 1);
    ~Fixture();
    void runJob() {
        _runRetval = _trackedJob->run();
        _runGates[_runIdx++]->countDown();
    }
    void assertTracker(size_t startedGateCount, size_t endedGateCount) {
        EXPECT_EQ(startedGateCount, _tracker->_started.getCount());
        EXPECT_EQ(endedGateCount, _tracker->_ended.getCount());
    }
    void runJobAndWait(size_t runIdx, size_t startedGateCount, size_t endedGateCount) {
        _exec.execute(vespalib::makeLambdaTask([this]() { runJob(); }));
        _tracker->_started.await(5s);
        assertTracker(startedGateCount, endedGateCount);
        _myJob->_runGates[runIdx]->countDown();
        _runGates[runIdx]->await(5s);
    }
};

Fixture::Fixture(size_t numRuns)
    : _tracker(std::make_shared<SimpleJobTracker>(1)),
      _job(std::make_unique<MyMaintenanceJob>(numRuns)),
      _myJob(static_cast<MyMaintenanceJob *>(_job.get())),
      _trackedJob(std::make_unique<JobTrackedMaintenanceJob>(_tracker, std::move(_job))),
      _runRetval(false),
      _runGates(getGateVector(numRuns)),
      _runIdx(0),
      _exec(1)
{
}

Fixture::~Fixture() = default;

TEST(JobTrackedMaintenanceJobTest, require_that_maintenance_job_name_delay_and_interval_are_preserved)
{
    Fixture f;
    EXPECT_EQ("myjob", f._trackedJob->getName());
    EXPECT_EQ(10s, f._trackedJob->getDelay());
    EXPECT_EQ(20s, f._trackedJob->getInterval());
}

TEST(JobTrackedMaintenanceJobTest, require_that_maintenance_job_that_needs_1_run_is_tracked)
{
    Fixture f;
    f.assertTracker(1, 1);
    f.runJobAndWait(0, 0, 1);
    f.assertTracker(0, 0);
    EXPECT_TRUE(f._runRetval);
}

TEST(JobTrackedMaintenanceJobTest, require_that_maintenance_job_that_needs_several_runs_is_tracked)
{
    Fixture f(2);
    f.assertTracker(1, 1);
    f.runJobAndWait(0, 0, 1);
    f.assertTracker(0, 1);
    EXPECT_FALSE(f._runRetval);

    f.runJobAndWait(1, 0, 1);
    f.assertTracker(0, 0);
    EXPECT_TRUE(f._runRetval);
}

TEST(JobTrackedMaintenanceJobTest, require_that_maintenance_job_that_is_destroyed_is_tracked)
{
    Fixture f(2);
    f.assertTracker(1, 1);
    f.runJobAndWait(0, 0, 1);
    f.assertTracker(0, 1);
    EXPECT_FALSE(f._runRetval);

    f._trackedJob.reset();
    f.assertTracker(0, 0);
}

TEST(JobTrackedMaintenanceJobTest, require_that_block_calls_are_sent_to_underlying_jobs)
{
    Fixture f;
    EXPECT_FALSE(f._trackedJob->isBlocked());
    EXPECT_TRUE(f._trackedJob->asBlockable() != nullptr);
    f._myJob->block();
    EXPECT_TRUE(f._myJob->isBlocked());
    EXPECT_TRUE(f._trackedJob->isBlocked());
    f._myJob->unBlock();
    EXPECT_FALSE(f._myJob->isBlocked());
    EXPECT_FALSE(f._trackedJob->isBlocked());
}

TEST(JobTrackedMaintenanceJobTest, require_that_stop_calls_are_sent_to_underlying_jobs)
{
    Fixture f;
    EXPECT_FALSE(f._myJob->stopped());
    f._trackedJob->stop();
    EXPECT_TRUE(f._myJob->stopped());
}

}
