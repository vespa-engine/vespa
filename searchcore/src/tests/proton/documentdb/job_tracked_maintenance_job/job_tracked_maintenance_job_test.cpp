// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/log/log.h>
LOG_SETUP("job_tracked_maintenance_test");

#include <vespa/searchcore/proton/server/job_tracked_maintenance_job.h>
#include <vespa/searchcore/proton/test/simple_job_tracker.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/sync.h>

using namespace proton;
using namespace vespalib;
using test::SimpleJobTracker;
typedef std::unique_ptr<Gate> GateUP;
typedef std::vector<GateUP> GateVector;

GateVector
getGateVector(size_t size)
{
    GateVector retval;
    for (size_t i = 0; i < size; ++i) {
        retval.push_back(std::move(GateUP(new Gate())));
    }
    return retval;
}

struct MyMaintenanceJob : public IMaintenanceJob
{
    GateVector _runGates;
    size_t     _runIdx;
    MyMaintenanceJob(size_t numRuns)
        : IMaintenanceJob("myjob", 10, 20),
          _runGates(getGateVector(numRuns)),
          _runIdx(0)
    {}
    void block() {
        setBlocked(true);
    }
    virtual bool run() override {
        _runGates[_runIdx++]->await(5000);
        return _runIdx == _runGates.size();
    }
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
    Fixture(size_t numRuns = 1)
        : _tracker(new SimpleJobTracker(1)),
          _job(new MyMaintenanceJob(numRuns)),
          _myJob(static_cast<MyMaintenanceJob *>(_job.get())),
          _trackedJob(new JobTrackedMaintenanceJob(_tracker, std::move(_job))),
          _runRetval(false),
          _runGates(getGateVector(numRuns)),
          _runIdx(0),
          _exec(1, 64000)
    {
    }
    void runJob() {
        _runRetval = _trackedJob->run();
        _runGates[_runIdx++]->countDown();
    }
    void assertTracker(size_t startedGateCount, size_t endedGateCount) {
        EXPECT_EQUAL(startedGateCount, _tracker->_started.getCount());
        EXPECT_EQUAL(endedGateCount, _tracker->_ended.getCount());
    }
    void runJobAndWait(size_t runIdx, size_t startedGateCount, size_t endedGateCount) {
        _exec.execute(makeTask(makeClosure(this, &Fixture::runJob)));
        _tracker->_started.await(5000);
        assertTracker(startedGateCount, endedGateCount);
        _myJob->_runGates[runIdx]->countDown();
        _runGates[runIdx]->await(5000);
    }
};

TEST_F("require that maintenance job name, delay and interval are preserved", Fixture)
{
    EXPECT_EQUAL("myjob", f._trackedJob->getName());
    EXPECT_EQUAL(10, f._trackedJob->getDelay());
    EXPECT_EQUAL(20, f._trackedJob->getInterval());
}

TEST_F("require that maintenance job that needs 1 run is tracked", Fixture)
{
    f.assertTracker(1, 1);
    f.runJobAndWait(0, 0, 1);
    f.assertTracker(0, 0);
    EXPECT_TRUE(f._runRetval);
}

TEST_F("require that maintenance job that needs several runs is tracked", Fixture(2))
{
    f.assertTracker(1, 1);
    f.runJobAndWait(0, 0, 1);
    f.assertTracker(0, 1);
    EXPECT_FALSE(f._runRetval);

    f.runJobAndWait(1, 0, 1);
    f.assertTracker(0, 0);
    EXPECT_TRUE(f._runRetval);
}

TEST_F("require that maintenance job that is destroyed is tracked", Fixture(2))
{
    f.assertTracker(1, 1);
    f.runJobAndWait(0, 0, 1);
    f.assertTracker(0, 1);
    EXPECT_FALSE(f._runRetval);

    f._trackedJob.reset();
    f.assertTracker(0, 0);
}

TEST_F("require that block calls are sent to underlying job", Fixture)
{
    EXPECT_FALSE(f._trackedJob->isBlocked());
    f._myJob->block();
    EXPECT_TRUE(f._myJob->isBlocked());
    EXPECT_TRUE(f._trackedJob->isBlocked());
    f._trackedJob->unBlock();
    EXPECT_FALSE(f._myJob->isBlocked());
    EXPECT_FALSE(f._trackedJob->isBlocked());
}

TEST_MAIN() { TEST_RUN_ALL(); }
