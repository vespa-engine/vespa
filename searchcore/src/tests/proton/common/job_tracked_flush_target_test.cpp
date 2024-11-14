// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/metrics/job_tracked_flush_target.h>
#include <vespa/searchcore/proton/metrics/job_tracked_flush_task.h>
#include <vespa/searchcore/proton/test/dummy_flush_target.h>
#include <vespa/searchcore/proton/test/simple_job_tracker.h>
#include <vespa/searchlib/common/flush_token.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/gate.h>

using namespace proton;
using namespace searchcorespi;
using search::SerialNum;
using test::SimpleJobTracker;
using vespalib::makeLambdaTask;
using vespalib::Gate;
using vespalib::ThreadStackExecutor;

namespace job_tracked_flush_test {

struct MyFlushTask : public searchcorespi::FlushTask
{
    Gate &_execGate;
    MyFlushTask(Gate &execGate) : _execGate(execGate) {}

    // Implements searchcorespi::FlushTask
    void run() override {
        _execGate.await(5s);
    }
    virtual search::SerialNum getFlushSerial() const override { return 5; }
};

struct MyFlushTarget : public test::DummyFlushTarget
{
    using SP = std::shared_ptr<MyFlushTarget>;
    SerialNum _initFlushSerial;
    Gate _execGate;
    Gate _initGate;
    MyFlushTarget() noexcept
        : test::DummyFlushTarget("mytarget", Type::FLUSH, Component::OTHER),
          _initFlushSerial(0),
          _execGate(),
          _initGate()
    {}

    // Implements searchcorespi::IFlushTarget
    FlushTask::UP initFlush(SerialNum currentSerial, std::shared_ptr<search::IFlushToken>) override {
        if (currentSerial > 0) {
            _initFlushSerial = currentSerial;
            _initGate.await(5s);
            return std::make_unique<MyFlushTask>(_execGate);
        }
        return FlushTask::UP();
    }
};

struct Fixture
{
    SimpleJobTracker::SP _tracker;
    MyFlushTarget::SP _target;
    JobTrackedFlushTarget _trackedFlush;
    FlushTask::UP _task;
    Gate _taskGate;
    ThreadStackExecutor _exec;
    Fixture(uint32_t numJobTrackings = 1);
    ~Fixture();
    void initFlush(SerialNum currentSerial) {
        _task = _trackedFlush.initFlush(currentSerial, std::make_shared<search::FlushToken>());
        _taskGate.countDown();
    }
};

Fixture::Fixture(uint32_t numJobTrackings)
    : _tracker(std::make_shared<SimpleJobTracker>(numJobTrackings)),
      _target(std::make_shared<MyFlushTarget>()),
      _trackedFlush(_tracker, _target),
      _task(),
      _taskGate(),
      _exec(1)
{
}

Fixture::~Fixture() = default;

constexpr SerialNum FLUSH_SERIAL = 10;

TEST(JobTrackedFlushTargetTest, require_that_flush_target_name_type_and_component_is_preserved)
{
    Fixture f;
    EXPECT_EQ("mytarget", f._trackedFlush.getName());
    EXPECT_TRUE(IFlushTarget::Type::FLUSH == f._trackedFlush.getType());
    EXPECT_TRUE(IFlushTarget::Component::OTHER == f._trackedFlush.getComponent());
}

TEST(JobTrackedFlushTargetTest, require_that_flush_task_init_is_tracked)
{
    Fixture f;
    EXPECT_EQ(1u, f._tracker->_started.getCount());
    EXPECT_EQ(1u, f._tracker->_ended.getCount());

    f._exec.execute(makeLambdaTask([&]() {f.initFlush(FLUSH_SERIAL); }));
    f._tracker->_started.await(5s);
    EXPECT_EQ(0u, f._tracker->_started.getCount());
    EXPECT_EQ(1u, f._tracker->_ended.getCount());

    f._target->_initGate.countDown();
    f._taskGate.await(5s);
    EXPECT_EQ(0u, f._tracker->_ended.getCount());
    {
        JobTrackedFlushTask *trackedTask = dynamic_cast<JobTrackedFlushTask *>(f._task.get());
        EXPECT_TRUE(trackedTask != nullptr);
        EXPECT_EQ(5u, trackedTask->getFlushSerial());
    }
    EXPECT_EQ(FLUSH_SERIAL, f._target->_initFlushSerial);
}

TEST(JobTrackedFlushTargetTest, require_that_flush_task_execution_is_tracked)
{
    Fixture f(2);
    f._exec.execute(makeLambdaTask([&]() { f.initFlush(FLUSH_SERIAL); }));
    f._target->_initGate.countDown();
    f._taskGate.await(5s);

    EXPECT_EQ(1u, f._tracker->_started.getCount());
    EXPECT_EQ(1u, f._tracker->_ended.getCount());

    f._exec.execute(std::move(f._task));
    f._tracker->_started.await(5s);
    EXPECT_EQ(0u, f._tracker->_started.getCount());
    EXPECT_EQ(1u, f._tracker->_ended.getCount());

    f._target->_execGate.countDown();
    f._tracker->_ended.await(5s);
    EXPECT_EQ(0u, f._tracker->_ended.getCount());
}

TEST(JobTrackedFlushTargetTest, require_that_nullptr_flush_task_is_not_tracked)
{
    Fixture f;
    FlushTask::UP task = f._trackedFlush.initFlush(0, std::make_shared<search::FlushToken>());
    EXPECT_TRUE(task.get() == nullptr);
}

}
