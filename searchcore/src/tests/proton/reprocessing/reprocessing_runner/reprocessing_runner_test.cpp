// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/reprocessing/i_reprocessing_task.h>
#include <vespa/searchcore/proton/reprocessing/reprocessingrunner.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("reprocessing_runner_test");

using namespace proton;

struct ReprocessingRunnerTest : public  ::testing::Test
{
    ReprocessingRunner _runner;
    ReprocessingRunnerTest();
    ~ReprocessingRunnerTest() override;
};

ReprocessingRunnerTest::ReprocessingRunnerTest()
    : ::testing::Test(),
      _runner()
{
}

ReprocessingRunnerTest::~ReprocessingRunnerTest() = default;

using TaskList = ReprocessingRunner::ReprocessingTasks;

struct MyTask : public IReprocessingTask
{
    ReprocessingRunner &_runner;
    double _initProgress;
    double _middleProgress;
    double _finalProgress;
    double _myProgress;
    double _weight;

    MyTask(ReprocessingRunner &runner,
           double initProgress,
           double middleProgress,
           double finalProgress,
           double weight) noexcept
        : _runner(runner),
          _initProgress(initProgress),
          _middleProgress(middleProgress),
          _finalProgress(finalProgress),
          _myProgress(0.0),
          _weight(weight)
    {
    }

    void run() override {
        ASSERT_EQ(_initProgress, _runner.getProgress());
        _myProgress = 0.5;
        ASSERT_EQ(_middleProgress, _runner.getProgress());
        _myProgress = 1.0;
        ASSERT_EQ(_finalProgress, _runner.getProgress());
    }

    Progress getProgress() const override {
        return Progress(_myProgress, _weight);
    }

    static std::shared_ptr<MyTask>
    create(ReprocessingRunner &runner,
           double initProgress,
           double middleProgress,
           double finalProgress,
           double weight)
    {
        return std::make_shared<MyTask>(runner, initProgress, middleProgress, finalProgress, weight);
    }
};

TEST_F(ReprocessingRunnerTest, require_that_progress_is_calculated_when_tasks_are_executed)
{
    TaskList tasks;
    EXPECT_EQ(0.0, _runner.getProgress());
    tasks.push_back(MyTask::create(_runner, 0.0, 0.1, 0.2, 1.0));
    tasks.push_back(MyTask::create(_runner, 0.2, 0.6, 1.0, 4.0));
    _runner.addTasks(tasks);
    tasks.clear();
    EXPECT_EQ(0.0, _runner.getProgress());
    _runner.run();
    EXPECT_EQ(1.0, _runner.getProgress());
}


TEST_F(ReprocessingRunnerTest, require_that_runner_can_be_reset)
{
    TaskList tasks;
    EXPECT_EQ(0.0, _runner.getProgress());
    tasks.push_back(MyTask::create(_runner, 0.0, 0.5, 1.0, 1.0));
    _runner.addTasks(tasks);
    tasks.clear();
    EXPECT_EQ(0.0, _runner.getProgress());
    _runner.run();
    EXPECT_EQ(1.0, _runner.getProgress());
    _runner.reset();
    EXPECT_EQ(0.0, _runner.getProgress());
    tasks.push_back(MyTask::create(_runner, 0.0, 0.5, 1.0, 1.0));
    _runner.addTasks(tasks);
    tasks.clear();
    EXPECT_EQ(0.0, _runner.getProgress());
    _runner.reset();
    EXPECT_EQ(0.0, _runner.getProgress());
    tasks.push_back(MyTask::create(_runner, 0.0, 0.5, 1.0, 4.0));
    _runner.addTasks(tasks);
    tasks.clear();
    EXPECT_EQ(0.0, _runner.getProgress());
    _runner.run();
    EXPECT_EQ(1.0, _runner.getProgress());
}

GTEST_MAIN_RUN_ALL_TESTS()
