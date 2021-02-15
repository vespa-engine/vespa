// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/time_bomb.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/executor.h>
#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/size_literals.h>
#include <thread>

using namespace vespalib;

constexpr vespalib::duration waitTime = 30s;

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
    void run() override {
        _entryGate.await(waitTime);
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
        workersExitLatch.await(waitTime);
    }
    void assertExecuteIsBlocked() {
        blockedExecuteGate.await(10ms);
        EXPECT_EQUAL(1u, blockedExecuteGate.getCount());
    }
    void waitForExecuteIsFinished() {
        blockedExecuteGate.await(waitTime);
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

vespalib::string get_worker_stack_trace(BlockingThreadStackExecutor &executor) {
    struct StackTraceTask : public Executor::Task {
        vespalib::string &trace;
        explicit StackTraceTask(vespalib::string &t) : trace(t) {}
        void run() override { trace = getStackTrace(0); }
    };
    vespalib::string trace;
    executor.execute(std::make_unique<StackTraceTask>(trace));
    executor.sync();
    return trace;
}

VESPA_THREAD_STACK_TAG(my_stack_tag);

TEST_F("require that executor has appropriate default thread stack tag", BlockingThreadStackExecutor(1, 128_Ki, 10)) {
    vespalib::string trace = get_worker_stack_trace(f1);
    if (!EXPECT_TRUE(trace.find("unnamed_blocking_executor") != vespalib::string::npos)) {
        fprintf(stderr, "%s\n", trace.c_str());
    }
}

TEST_F("require that executor thread stack tag can be set", BlockingThreadStackExecutor(1, 128_Ki, 10, my_stack_tag)) {
    vespalib::string trace = get_worker_stack_trace(f1);
    if (!EXPECT_TRUE(trace.find("my_stack_tag") != vespalib::string::npos)) {
        fprintf(stderr, "%s\n", trace.c_str());
    }
}

TEST_F("require that tasks posted from internal worker thread will not block executor", TimeBomb(60)) {
    size_t cnt = 0;
    Gate fork_done;
    BlockingThreadStackExecutor executor(1, 128_Ki, 10);
    struct IncTask : Executor::Task {
        size_t &cnt;
        IncTask(size_t &cnt_in) : cnt(cnt_in) {}
        void run() override { ++cnt; }
    };
    struct ForkTask : Executor::Task {
        Executor &executor;
        Gate &fork_done;
        size_t &cnt;
        ForkTask(Executor &executor_in, Gate &fork_done_in, size_t &cnt_in)
            : executor(executor_in), fork_done(fork_done_in), cnt(cnt_in) {}
        void run() override {
            for (size_t i = 0; i < 32; ++i) {
                executor.execute(std::make_unique<IncTask>(cnt));
            }
            fork_done.countDown();
        }
    };
    // post 32 internal tasks on a blocking executor with tasklimit 10
    executor.execute(std::make_unique<ForkTask>(executor, fork_done, cnt));
    fork_done.await();
    executor.sync();
    EXPECT_EQUAL(cnt, 32u);
}

TEST_MAIN() { TEST_RUN_ALL(); }
