// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/size_literals.h>
#include <atomic>
#include <thread>

using namespace vespalib;

typedef Executor::Task Task;

struct MyTask : public Executor::Task {
    Gate &gate;
    CountDownLatch &latch;
    static std::atomic<uint32_t> runCnt;
    static std::atomic<uint32_t> deleteCnt;
    MyTask(Gate &g, CountDownLatch &l) : gate(g), latch(l) {}
    void run() override {
        runCnt.fetch_add(1);
        latch.countDown();
        gate.await();
    }
    ~MyTask() {
        deleteCnt.fetch_add(1);
    }
    static void resetStats() {
        runCnt = 0;
        deleteCnt = 0;
    }
};
std::atomic<uint32_t> MyTask::runCnt(0);
std::atomic<uint32_t> MyTask::deleteCnt(0);

struct MyState {
    static constexpr uint32_t NUM_THREADS = 10;
    Gate                gate;     // to block workers
    CountDownLatch      latch;    // to wait for workers
    ThreadStackExecutor executor;
    bool                checked;
    MyState() : gate(), latch(10), executor(NUM_THREADS, 128000, 20), checked(false)
    {
        MyTask::resetStats();
    }
    MyState &execute(uint32_t cnt) {
        for (uint32_t i = 0; i < cnt; ++i) {
            executor.execute(std::make_unique<MyTask>(gate, latch));
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
        ExecutorStats stats = executor.getStats();
        EXPECT_EQUAL(expect_running + expect_deleted, MyTask::runCnt);
        EXPECT_EQUAL(expect_rejected + expect_deleted, MyTask::deleteCnt);
        EXPECT_EQUAL(expect_queue + expect_running + expect_deleted,stats.acceptedTasks);
        EXPECT_EQUAL(expect_rejected, stats.rejectedTasks);
        EXPECT_TRUE(stats.wakeupCount <= (NUM_THREADS + stats.acceptedTasks));
        EXPECT_TRUE(!(gate.getCount() == 1) || (expect_deleted == 0));
        if (expect_deleted == 0) {
            EXPECT_EQUAL(expect_queue + expect_running, stats.queueSize.max());
        }
        stats = executor.getStats();
        EXPECT_EQUAL(expect_queue + expect_running, stats.queueSize.max());
        EXPECT_EQUAL(0u, stats.acceptedTasks);
        EXPECT_EQUAL(0u, stats.rejectedTasks);
        EXPECT_EQUAL(0u, stats.wakeupCount);
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


struct WaitTask : public Executor::Task {
    Gate &gate;
    WaitTask(Gate &g) : gate(g) {}
    void run() override { gate.await(); }
};

struct WaitState {
    ThreadStackExecutor executor;
    std::vector<Gate> block_task;
    std::vector<Gate> wait_done;
    WaitState(size_t num_threads)
        : executor(num_threads / 2, 128000), block_task(num_threads - 2), wait_done(num_threads - 1)
    {
        for (auto &gate: block_task) {
            auto result = executor.execute(std::make_unique<WaitTask>(gate));
            ASSERT_TRUE(result.get() == nullptr);
        }
    }
    void wait(size_t count) {
        executor.wait_for_task_count(count);
        wait_done[count].countDown();
    }
};

TEST_MT_F("require that threads can wait for a specific task count", 7, WaitState(num_threads)) {
    if (thread_id == 0) {
        for (size_t next_done = (num_threads - 2); next_done-- > 0;) {
            if (next_done < f1.block_task.size()) {
                f1.block_task[f1.block_task.size() - 1 - next_done].countDown();
            }
            EXPECT_TRUE(f1.wait_done[next_done].await(25s));
            for (size_t i = 0; i < next_done; ++i) {
                EXPECT_TRUE(!f1.wait_done[i].await(20ms));
            }
        }
    } else {
        f1.wait(thread_id - 1);
    }
}

vespalib::string get_worker_stack_trace(ThreadStackExecutor &executor) {
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

TEST_F("require that executor has appropriate default thread stack tag", ThreadStackExecutor(1, 128_Ki)) {
    vespalib::string trace = get_worker_stack_trace(f1);
    if (!EXPECT_TRUE(trace.find("unnamed_nonblocking_executor") != vespalib::string::npos)) {
        fprintf(stderr, "%s\n", trace.c_str());
    }
}

TEST_F("require that executor thread stack tag can be set", ThreadStackExecutor(1, 128_Ki, my_stack_tag)) {
    vespalib::string trace = get_worker_stack_trace(f1);
    if (!EXPECT_TRUE(trace.find("my_stack_tag") != vespalib::string::npos)) {
        fprintf(stderr, "%s\n", trace.c_str());
    }
}

TEST("require that stats can be accumulated") {
    EXPECT_TRUE(std::atomic<duration>::is_always_lock_free);
    ExecutorStats stats(ExecutorStats::QueueSizeT(1) ,2,3,7);
    stats.setUtil(3, 0.8);
    EXPECT_EQUAL(1u, stats.queueSize.max());
    EXPECT_EQUAL(2u, stats.acceptedTasks);
    EXPECT_EQUAL(3u, stats.rejectedTasks);
    EXPECT_EQUAL(7u, stats.wakeupCount);
    EXPECT_EQUAL(3u, stats.getThreadCount());
    EXPECT_EQUAL(0.2, stats.getUtil());
    stats.aggregate(ExecutorStats(ExecutorStats::QueueSizeT(7),8,9,11).setUtil(7,0.5));
    EXPECT_EQUAL(2u, stats.queueSize.count());
    EXPECT_EQUAL(8u, stats.queueSize.total());
    EXPECT_EQUAL(8u, stats.queueSize.max());
    EXPECT_EQUAL(8u, stats.queueSize.min());
    EXPECT_EQUAL(8u, stats.queueSize.max());
    EXPECT_EQUAL(4.0, stats.queueSize.average());

    EXPECT_EQUAL(10u, stats.getThreadCount());
    EXPECT_EQUAL(10u, stats.acceptedTasks);
    EXPECT_EQUAL(12u, stats.rejectedTasks);
    EXPECT_EQUAL(18u, stats.wakeupCount);
    EXPECT_EQUAL(0.41, stats.getUtil());
}

TEST("Test that utilization is computed") {
    ThreadStackExecutor executor(1, 128_Ki);
    std::this_thread::sleep_for(1s);
    auto stats = executor.getStats();
    EXPECT_GREATER(0.50, stats.getUtil());
}

TEST_MAIN() { TEST_RUN_ALL(); }
