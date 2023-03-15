// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/coro/lazy.h>
#include <vespa/vespalib/coro/schedule.h>
#include <vespa/vespalib/coro/completion.h>
#include <vespa/vespalib/coro/active_work.h>
#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cinttypes>

using namespace vespalib;
using namespace vespalib::coro;

Lazy<int> make_expensive_task(Executor &executor, int value) {
    co_await schedule(executor);
    auto cpu_cost = 20ms;
    std::this_thread::sleep_for(cpu_cost);
    co_return value;
}

Lazy<int> make_cheap_task(Executor &, int value) {
    co_return value;
}

Lazy<int> concurrent_sum(Executor &executor, std::vector<int> values,
                         std::function<Lazy<int>(Executor &,int)> make_task)
{
    std::vector<Lazy<int>> work;
    for (int v: values) {
        work.push_back(make_task(executor, v));
    }
    ActiveWork active;
    for (auto &task: work) {
        active.start(task);
    }
    co_await active.join();
    int res = 0;
    for (auto &task: work) {
        res += co_await task; // await_ready == true
    }
    co_return res;
}

TEST(ActiveWorkTest, run_expensive_subtasks_concurrently) {
    vespalib::ThreadStackExecutor executor(8);
    auto t0 = steady_clock::now();
    auto result = sync_wait(concurrent_sum(executor, {1, 2, 3, 4, 5, 6, 7, 8,
                                                      9,10,11,12,13,14,15,16},
            make_expensive_task));
    auto td = steady_clock::now() - t0;
    EXPECT_EQ(result, 136);
    fprintf(stderr, "time spent: %" PRId64 " ms\n", count_ms(td));
}

TEST(ActiveWorkTest, run_cheap_subtasks_concurrently) {
    vespalib::ThreadStackExecutor executor(1);
    auto t0 = steady_clock::now();
    auto result = sync_wait(concurrent_sum(executor, {1, 2, 3, 4, 5, 6, 7, 8,
                                                      9,10,11,12,13,14,15,16},
            make_cheap_task));
    auto td = steady_clock::now() - t0;
    EXPECT_EQ(result, 136);
    fprintf(stderr, "time spent: %" PRId64 " ms\n", count_ms(td));
}

GTEST_MAIN_RUN_ALL_TESTS()
