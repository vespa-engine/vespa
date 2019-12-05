// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdslib/thread/taskscheduler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/time.h>
#include <thread>

namespace vdslib {

namespace {

struct TestWatch : public TaskScheduler::Watch {
    vespalib::Lock _lock;
    uint64_t _time;

    TestWatch(uint64_t startTime = 0) : _time(startTime) {}
    ~TestWatch() {}

    TaskScheduler::Time getTime() const override {
        vespalib::LockGuard guard(_lock);
        return _time;
    }

    void increment(uint64_t ms) {
        vespalib::LockGuard guard(_lock);
        _time += ms;
    }

    void set(uint64_t ms) {
        vespalib::LockGuard guard(_lock);
        _time = ms;
    }
};

struct TestTask : public TaskScheduler::Task
{
    TestWatch& _watch;
    uint64_t _executionTime;
    uint64_t _maxRuns;
    uint64_t _maxTime;
    int64_t _result;
    uint64_t _currentRuns;
    std::string _name;
    std::vector<std::string>* _register;

    TestTask(TestWatch& watch, uint64_t executionTime, uint64_t maxRuns,
             uint64_t maxTime, int64_t result)
        : _watch(watch), _executionTime(executionTime), _maxRuns(maxRuns),
          _maxTime(maxTime), _result(result), _currentRuns(0),
          _name(), _register(0)
    {
    }

    void registerCallsWithName(const std::string& name,
                               std::vector<std::string>& myregister)
    {
        _name = name;
        _register = &myregister;
    }

    int64_t run(TaskScheduler::Time currentTime) override {
            // Emulate that we use time to run
        _watch.increment(_executionTime);
        if (_register != 0) {
            std::ostringstream ost;
            ost << currentTime;
            if (_name.size() > 0) {
                ost << " " << _name;
            }
            _register->push_back(ost.str());
        }
            // If max runs, dont run anymore
        if (++_currentRuns >= _maxRuns) {
            //std::cerr << "Max runs run, returning 0\n";
            return 0;
        }
            // If we will go beyond max time, dont run anymore
        if (_result > 0 && currentTime + _result > _maxTime) {
            //std::cerr << "Max time spent, returning 0\n";
            return 0;
        }
        //std::cerr << "Executed test task. Returning " << _result << "\n";
        return _result;
    }

};

std::string join(std::vector<std::string>& v) {
    std::ostringstream ost;
    for (size_t i=0; i<v.size(); ++i) {
        if (i != 0) ost << ",";
        ost << v[i];
    }
    return ost.str();
}

}

TEST(TaskSchedulerTest, test_simple)
{
    FastOS_ThreadPool threadPool(128 * 1024);
    TestWatch watch(0);
    TaskScheduler scheduler;
    scheduler.setWatch(watch);
    scheduler.start(threadPool);
    std::vector<std::string> calls;

        // Test that one can schedule a single task immediately
    {
        calls.clear();
        watch.set(0);
        uint64_t counter = scheduler.getTaskCounter();
        TestTask* task(new TestTask(watch, 10, 5, 1000, 0));
        task->registerCallsWithName("", calls);
        scheduler.add(TestTask::UP(task));
        scheduler.waitForTaskCounterOfAtLeast(counter + 1);
        EXPECT_EQ(std::string("0"), join(calls));
        scheduler.waitUntilNoTasksRemaining(); // Ensure task is complete
    }
        // Test that task is repeated at intervals if wanted.
    {
        calls.clear();
        watch.set(0);
        uint64_t counter = scheduler.getTaskCounter();
        TestTask* task(new TestTask(watch, 10, 5, 1000, -20));
        task->registerCallsWithName("", calls);
        scheduler.add(TestTask::UP(task));
        for (uint32_t i = 1; i <= 5; ++i) {
            scheduler.waitForTaskCounterOfAtLeast(counter + i);
            watch.increment(100);
        }
        EXPECT_EQ(std::string("0,110,220,330,440"),
                  join(calls));
        scheduler.waitUntilNoTasksRemaining(); // Ensure task is complete
    }
        // Test that task scheduled at specific time works, and that if
        // scheduled at specific time in the past/current, we're rerun at once.
    {
        calls.clear();
        watch.set(0);
        uint64_t counter = scheduler.getTaskCounter();
        TestTask* task(new TestTask(watch, 10, 4, 1000, 100));
        task->registerCallsWithName("", calls);
        scheduler.addAbsolute(TestTask::UP(task), 50);
        watch.increment(49); // Not yet time to run
        std::this_thread::sleep_for(5ms);
            // Check that it has not run yet..
        EXPECT_EQ(counter, scheduler.getTaskCounter());
        watch.increment(10); // Now time is enough for it to run
        scheduler.waitForTaskCounterOfAtLeast(counter + 1);
        watch.increment(10);
        std::this_thread::sleep_for(5ms);
            // Check that it has not run yet..
        EXPECT_EQ(counter + 1, scheduler.getTaskCounter());
        watch.increment(50);
        scheduler.waitForTaskCounterOfAtLeast(counter + 2);
        EXPECT_EQ(std::string("59,129,129,129"),
                  join(calls));
        scheduler.waitUntilNoTasksRemaining(); // Ensure task is complete
    }
}

TEST(TaskSchedulerTest, test_multiple_tasks_at_same_time)
{
    FastOS_ThreadPool threadPool(128 * 1024);
    TestWatch watch(0);
    TaskScheduler scheduler;
    scheduler.setWatch(watch);
    std::vector<std::string> calls;

        // Test that tasks deleted before they are run are automatically
        // cancelled and removed from scheduler
    {
        TestTask* task1(new TestTask(watch, 10, 3, 1000, 10));
        TestTask* task2(new TestTask(watch, 10, 3, 1000, 10));
        task1->registerCallsWithName("task1", calls);
        task2->registerCallsWithName("task2", calls);
        watch.set(10);
        scheduler.add(TestTask::UP(task1));
        scheduler.add(TestTask::UP(task2));
            // Start threadpool after adding both, such that we ensure both
            // are added at the same time interval
        scheduler.start(threadPool);

        scheduler.waitUntilNoTasksRemaining(); // Ensure task is complete
        std::ostringstream ost;
        for (size_t i=0; i<calls.size(); ++i) ost << calls[i] << "\n";

        EXPECT_EQ(std::string(
                          "10 task1\n"
                          "10 task2\n"
                          "10 task1\n"
                          "10 task2\n"
                          "10 task1\n"
                          "10 task2\n"
                  ), ost.str());
    }
}

TEST(TaskSchedulerTest, test_remove_task)
{
    FastOS_ThreadPool threadPool(128 * 1024);
    TestWatch watch(0);
    TaskScheduler scheduler;
    scheduler.setWatch(watch);
    scheduler.start(threadPool);
    std::vector<std::string> calls;

        // Schedule a task, and remove it..
    {
        calls.clear();
        watch.set(0);
        TestTask* task(new TestTask(watch, 10, 5, 1000, 0));
        task->registerCallsWithName("", calls);
        scheduler.addAbsolute(TestTask::UP(task), 50);
            // Remove actual task
        scheduler.remove(task);
        scheduler.waitUntilNoTasksRemaining(); // Ensure task is complete
            // Remove non-existing task
        task = new TestTask(watch, 10, 5, 1000, 0);
        scheduler.remove(task);
        delete task;
            // Time should not be advanced as task didn't get to run
        EXPECT_EQ(0, (int) watch.getTime());
    }
}

}
