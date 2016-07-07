// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/sync.h>
#include <thread>

using namespace vespalib;

class MyTask : public Executor::Task
{
private:
    Gate &_entryGate;
    CountDownLatch &_exitLatch;

public:
    MyTask(Gate &entryGate, CountDownLatch &exitLatch)
        : _entryGate(entryGate),
          _exitLatch(exitLatch)
    {}

    virtual void run() override {
        _entryGate.await(30000);
        _exitLatch.countDown();
    }

    static Task::UP create(Gate &entryGate, CountDownLatch &exitLatch) {
        return std::make_unique<MyTask>(entryGate, exitLatch);
    }
};

void
addTaskToExecutor(BlockingThreadStackExecutor *executor, Gate *workersEntryGate, CountDownLatch *workersExitLatch, Gate *exitGate)
{
    executor->execute(MyTask::create(*workersEntryGate, *workersExitLatch)); // this should be a blocking call
    exitGate->countDown();
}

using ThreadUP = std::unique_ptr<std::thread>;

struct Fixture
{
    BlockingThreadStackExecutor executor;
    Gate workersEntryGate;
    CountDownLatch workersExitLatch;
    Gate threadExitGate;

    Fixture(uint32_t taskLimit, uint32_t tasksToWaitFor)
        : executor(1, 128000, taskLimit),
          workersEntryGate(),
          workersExitLatch(tasksToWaitFor),
          threadExitGate()
    {}
    void execute(size_t numTasks) {
        for (size_t i = 0; i < numTasks; ++i) {
            executor.execute(MyTask::create(workersEntryGate, workersExitLatch));
        }
    }
    void openForWorkers() {
        workersEntryGate.countDown();
    }
    void waitForWorkers() {
        workersExitLatch.await(30000);
    }
    void assertAddTaskThreadIsBlocked() {
        threadExitGate.await(10);
        EXPECT_EQUAL(1u, threadExitGate.getCount());
    }
    void assertAddTaskThreadIsFinished() {
        threadExitGate.await(30000);
        EXPECT_EQUAL(0u, threadExitGate.getCount());
    }
    ThreadUP createAddTaskThread() {
        return std::make_unique<std::thread>(addTaskToExecutor, &executor, &workersEntryGate, &workersExitLatch, &threadExitGate);
    }
};

TEST_F("require that execute() blocks when task limits is reached", Fixture(3, 4))
{
    f.execute(3);
    ThreadUP thread = f.createAddTaskThread();
    TEST_DO(f.assertAddTaskThreadIsBlocked());
    f.openForWorkers();
    TEST_DO(f.assertAddTaskThreadIsFinished());
    thread->join();
    f.waitForWorkers();
}

TEST_MAIN() { TEST_RUN_ALL(); }
