// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/backtrace.h>
#include <vespa/vespalib/util/size_literals.h>
#include <atomic>
#include <thread>
#include <cassert>

using namespace vespalib;
using vespalib::test::Nexus;

using Task = Executor::Task;

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
    MyState() : gate(), latch(10), executor(NUM_THREADS, 20), checked(false)
    {
        MyTask::resetStats();
    }
    ~MyState();
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
        EXPECT_FALSE(checked);
        checked = true;
        ExecutorStats stats = executor.getStats();
        EXPECT_EQ(expect_running + expect_deleted, MyTask::runCnt);
        EXPECT_EQ(expect_rejected + expect_deleted, MyTask::deleteCnt);
        EXPECT_EQ(expect_queue + expect_running + expect_deleted,stats.acceptedTasks);
        EXPECT_EQ(expect_rejected, stats.rejectedTasks);
        EXPECT_TRUE(stats.wakeupCount <= (NUM_THREADS + stats.acceptedTasks));
        EXPECT_TRUE(!(gate.getCount() == 1) || (expect_deleted == 0));
        if (expect_deleted == 0) {
            EXPECT_EQ(expect_queue + expect_running, stats.queueSize.max());
        }
        stats = executor.getStats();
        EXPECT_EQ(expect_queue + expect_running, stats.queueSize.max());
        EXPECT_EQ(0u, stats.acceptedTasks);
        EXPECT_EQ(0u, stats.rejectedTasks);
        EXPECT_EQ(0u, stats.wakeupCount);
        return *this;
    }
};
MyState::~MyState() = default;


TEST(ThreadStackExecutorTest, requireThatTasksAreRunAndDeleted) {
    MyState f1;
    GTEST_DO(f1.open().execute(5).sync().check(0, 0, 0, 5));
}

TEST(ThreadStackExecutorTest, requireThatTasksRunConcurrently) {
    MyState f1;
    GTEST_DO(f1.execute(10).wait().check(0, 0, 10, 0).open());
}

TEST(ThreadStackExecutorTest, requireThatThreadCountIsRespected) {
    MyState f1;
    GTEST_DO(f1.execute(20).wait().check(0, 10, 10, 0).open());
}

TEST(ThreadStackExecutorTest, requireThatExtraTasksAreDropped) {
    MyState f1;
    GTEST_DO(f1.execute(40).wait().check(20, 10, 10, 0).open());
}

TEST(ThreadStackExecutorTest, requireThatActiveWorkersDrainInputQueue) {
    MyState f1;
    GTEST_DO(f1.execute(20).wait().open().sync().check(0, 0, 0, 20));
}

TEST(ThreadStackExecutorTest, requireThatPendingTasksAreRunAfterShutdown) {
    MyState f1;
    GTEST_DO(f1.execute(20).wait().shutdown().open().sync().check(0, 0, 0, 20));
}

TEST(ThreadStackExecutorTest, requireThatNewTasksAreDroppedAfterShutdown) {
    MyState f1;
    GTEST_DO(f1.open().shutdown().execute(5).sync().check(5, 0, 0, 0));
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
        : executor(num_threads / 2), block_task(num_threads - 2), wait_done(num_threads - 1)
    {
        for (auto &gate: block_task) {
            auto result = executor.execute(std::make_unique<WaitTask>(gate));
            assert(result.get() == nullptr);
        }
    }
    ~WaitState();
    void wait(size_t count) {
        executor.wait_for_task_count(count);
        wait_done[count].countDown();
    }
};
WaitState::~WaitState() = default;

TEST(ThreadStackExecutorTest, require_that_threads_can_wait_for_a_specific_task_count) {
    size_t num_threads = 7;
    WaitState f1(num_threads);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
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
                };
    Nexus::run(num_threads, task);
}

std::string get_worker_stack_trace(ThreadStackExecutor &executor) {
    struct StackTraceTask : public Executor::Task {
        std::string &trace;
        explicit StackTraceTask(std::string &t) : trace(t) {}
        void run() override { trace = getStackTrace(0); }
    };
    std::string trace;
    executor.execute(std::make_unique<StackTraceTask>(trace));
    executor.sync();
    return trace;
}

VESPA_THREAD_STACK_TAG(my_stack_tag);

TEST(ThreadStackExecutorTest, require_that_executor_has_appropriate_default_thread_stack_tag) {
    ThreadStackExecutor f1(1);
    std::string trace = get_worker_stack_trace(f1);
    EXPECT_TRUE(trace.find("unnamed_nonblocking_executor") != std::string::npos) << trace;
}

TEST(ThreadStackExecutorTest, require_that_executor_thread_stack_tag_can_be_set) {
    ThreadStackExecutor f1(1, my_stack_tag);
    std::string trace = get_worker_stack_trace(f1);
    EXPECT_TRUE(trace.find("my_stack_tag") != std::string::npos) << trace;
}

TEST(ThreadStackExecutorTest, require_that_stats_can_be_accumulated) {
    EXPECT_TRUE(std::atomic<duration>::is_always_lock_free);
    ExecutorStats stats(ExecutorStats::QueueSizeT(1) ,2,3,7);
    stats.setUtil(3, 0.8);
    EXPECT_EQ(1u, stats.queueSize.max());
    EXPECT_EQ(2u, stats.acceptedTasks);
    EXPECT_EQ(3u, stats.rejectedTasks);
    EXPECT_EQ(7u, stats.wakeupCount);
    EXPECT_EQ(3u, stats.getThreadCount());
    EXPECT_NEAR(0.2, stats.getUtil(), 1e-9);
    stats.aggregate(ExecutorStats(ExecutorStats::QueueSizeT(7),8,9,11).setUtil(7,0.5));
    EXPECT_EQ(2u, stats.queueSize.count());
    EXPECT_EQ(8u, stats.queueSize.total());
    EXPECT_EQ(8u, stats.queueSize.max());
    EXPECT_EQ(8u, stats.queueSize.min());
    EXPECT_EQ(8u, stats.queueSize.max());
    EXPECT_NEAR(4.0, stats.queueSize.average(), 1e-9);

    EXPECT_EQ(10u, stats.getThreadCount());
    EXPECT_EQ(10u, stats.acceptedTasks);
    EXPECT_EQ(12u, stats.rejectedTasks);
    EXPECT_EQ(18u, stats.wakeupCount);
    EXPECT_NEAR(0.41, stats.getUtil(), 1e-9);
}

ExecutorStats make_stats(uint32_t thread_count, double idle) {
    ExecutorStats stats;
    stats.setUtil(thread_count, idle);
    return stats;
}

TEST(ThreadStackExecutorTest, executor_stats_saturation_is_the_max_of_the_utilization_of_aggregated_executor_stats) {
    ExecutorStats aggr;
    auto s1 = make_stats(1, 0.9);
    EXPECT_NEAR(0.1, s1.getUtil(), 1e-9);
    EXPECT_NEAR(0.1, s1.get_saturation(), 1e-9);

    EXPECT_EQ(0.0, aggr.get_saturation());
    aggr.aggregate(s1);
    EXPECT_NEAR(0.1, aggr.get_saturation(), 1e-9);
    aggr.aggregate(make_stats(1, 0.7));
    EXPECT_NEAR(0.3, aggr.get_saturation(), 1e-9);
    aggr.aggregate(make_stats(1, 0.8));
    EXPECT_NEAR(0.3, aggr.get_saturation(), 1e-9);
}

TEST(ThreadStackExecutorTest, Test_that_utilization_is_computed) {
    ThreadStackExecutor executor(1);
    std::this_thread::sleep_for(1s);
    auto stats = executor.getStats();
    EXPECT_GT(0.50, stats.getUtil());
}

GTEST_MAIN_RUN_ALL_TESTS()
