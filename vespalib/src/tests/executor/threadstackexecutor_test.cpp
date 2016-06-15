// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/atomic.h>

#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/sync.h>

using namespace vespalib;

typedef Executor::Task Task;

struct MyTask : public Executor::Task {
    Gate &gate;
    CountDownLatch &latch;
    static uint32_t runCnt;
    static uint32_t deleteCnt;
    MyTask(Gate &g, CountDownLatch &l) : gate(g), latch(l) {}
    virtual void run() {
        Atomic::postInc(&runCnt);
        latch.countDown();
        gate.await();
    }
    virtual ~MyTask() {
        Atomic::postInc(&deleteCnt);
    }
    static void resetStats() {
        runCnt = 0;
        deleteCnt = 0;
    }
};
uint32_t MyTask::runCnt = 0;
uint32_t MyTask::deleteCnt = 0;

struct MyState {
    Gate                gate;     // to block workers
    CountDownLatch      latch;    // to wait for workers
    ThreadStackExecutor executor;
    bool                checked;
    MyState() : gate(), latch(10), executor(10, 128000, 20), checked(false)
    {
        MyTask::resetStats();
    }
    MyState &execute(uint32_t cnt) {
        for (uint32_t i = 0; i < cnt; ++i) {
            executor.execute(Task::UP(new MyTask(gate, latch)));
        }
        return *this;
    }
    MyState &sync() {
        executor.sync();
        return *this;
    }
    MyState &shutdown() {
        executor.shutdown();
        return *this;
    }
    MyState &open() {
        gate.countDown();
        return *this;
    }
    MyState &wait() {
        latch.await();
        return *this;
    }
    MyState &check(uint32_t expect_rejected,
                   uint32_t expect_queue,
                   uint32_t expect_running,
                   uint32_t expect_deleted)
    {
        ASSERT_TRUE(!checked);
        checked = true;
        ThreadStackExecutor::Stats stats = executor.getStats();
        EXPECT_EQUAL(expect_running + expect_deleted, MyTask::runCnt);
        EXPECT_EQUAL(expect_rejected + expect_deleted, MyTask::deleteCnt);
        EXPECT_EQUAL(expect_queue + expect_running + expect_deleted,
                     stats.acceptedTasks);
        EXPECT_EQUAL(expect_rejected, stats.rejectedTasks);
        EXPECT_TRUE(!(gate.getCount() == 1) || (expect_deleted == 0));
        if (expect_deleted == 0) {
            EXPECT_EQUAL(expect_queue + expect_running, stats.maxPendingTasks);
        }
        stats = executor.getStats();
        EXPECT_EQUAL(expect_queue + expect_running, stats.maxPendingTasks);
        EXPECT_EQUAL(0u, stats.acceptedTasks);
        EXPECT_EQUAL(0u, stats.rejectedTasks);
        return *this;
    }
};


TEST_F("requireThatTasksAreRunAndDeleted", MyState()) {
    TEST_DO(f1.open().execute(5).sync().check(0, 0, 0, 5));
}

TEST_F("requireThatTasksRunConcurrently", MyState()) {
    TEST_DO(f1.execute(10).wait().check(0, 0, 10, 0).open());
}

TEST_F("requireThatThreadCountIsRespected", MyState()) {
    TEST_DO(f1.execute(20).wait().check(0, 10, 10, 0).open());
}

TEST_F("requireThatExtraTasksAreDropped", MyState()) {
    TEST_DO(f1.execute(40).wait().check(20, 10, 10, 0).open());
}

TEST_F("requireThatActiveWorkersDrainInputQueue", MyState()) {
    TEST_DO(f1.execute(20).wait().open().sync().check(0, 0, 0, 20));
}

TEST_F("requireThatPendingTasksAreRunAfterShutdown", MyState()) {
    TEST_DO(f1.execute(20).wait().shutdown().open().sync().check(0, 0, 0, 20));
}

TEST_F("requireThatNewTasksAreDroppedAfterShutdown", MyState()) {
    TEST_DO(f1.open().shutdown().execute(5).sync().check(5, 0, 0, 0));
}

TEST_MAIN() { TEST_RUN_ALL(); }
