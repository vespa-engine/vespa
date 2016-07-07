// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/sync.h>
#include <thread>

using namespace vespalib;

constexpr int msWait = 30000;

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
        _entryGate.await(msWait);
        _exitLatch.countDown();
    }
    static Task::UP create(Gate &entryGate, CountDownLatch &exitLatch) {
        return std::make_unique<MyTask>(entryGate, exitLatch);
    }
};

void
blockedExecute(BlockingThreadStackExecutor *executor, Gate *workersEntryGate, CountDownLatch *workersExitLatch, Gate *exitGate)
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
    Gate blockedExecuteGate;

    Fixture(uint32_t taskLimit, uint32_t tasksToWaitFor)
        : executor(1, 128000, taskLimit),
          workersEntryGate(),
          workersExitLatch(tasksToWaitFor),
          blockedExecuteGate()
    {}
    void execute(size_t numTasks) {
        for (size_t i = 0; i < numTasks; ++i) {
            executor.execute(MyTask::create(workersEntryGate, workersExitLatch));
        }
    }
    void updateTaskLimit(uint32_t taskLimit) {
        executor.setTaskLimit(taskLimit);
    }
    void openForWorkers() {
        workersEntryGate.countDown();
    }
    void waitForWorkers() {
        workersExitLatch.await(msWait);
    }
    void assertExecuteIsBlocked() {
        blockedExecuteGate.await(10);
        EXPECT_EQUAL(1u, blockedExecuteGate.getCount());
    }
    void waitForExecuteIsFinished() {
        blockedExecuteGate.await(msWait);
        EXPECT_EQUAL(0u, blockedExecuteGate.getCount());
    }
    ThreadUP blockedExecuteThread() {
        return std::make_unique<std::thread>(blockedExecute, &executor, &workersEntryGate, &workersExitLatch, &blockedExecuteGate);
    }
    void blockedExecuteAndWaitUntilFinished() {
        ThreadUP thread = blockedExecuteThread();
        TEST_DO(assertExecuteIsBlocked());
        openForWorkers();
        TEST_DO(waitForExecuteIsFinished());
        thread->join();
        waitForWorkers();
    }
};

TEST_F("require that execute() blocks when task limits is reached", Fixture(3, 4))
{
    f.execute(3);
    f.blockedExecuteAndWaitUntilFinished();
}

TEST_F("require that task limit can be increased", Fixture(3, 5))
{
    f.execute(3);
    f.updateTaskLimit(4);
    f.execute(1);
    f.blockedExecuteAndWaitUntilFinished();
}

TEST_F("require that task limit can be decreased", Fixture(3, 3))
{
    f.execute(2);
    f.updateTaskLimit(2);
    f.blockedExecuteAndWaitUntilFinished();
}

TEST_MAIN() { TEST_RUN_ALL(); }
