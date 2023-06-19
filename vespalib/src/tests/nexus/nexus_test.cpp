// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/require.h>

using namespace vespalib::test;

TEST(NexusTest, run_void_tasks) {
    std::atomic<size_t> value = 0;
    auto task = [&value](Nexus &) {
                    value.fetch_add(1, std::memory_order_relaxed);
                };
    Nexus ctx(10);
    ctx.run(task);
    EXPECT_EQ(value, 10);
    ctx.run(task);
    EXPECT_EQ(value, 20);
}

TEST(NexusTest, run_value_tasks_select_thread_0) {
    std::atomic<size_t> value = 0;
    auto task = [&value](Nexus &ctx) {
                    value.fetch_add(1, std::memory_order_relaxed);
                    return ctx.thread_id() + 5;
                };
    Nexus ctx(10);
    EXPECT_EQ(ctx.run(task), 5);
    EXPECT_EQ(value, 10);
}

TEST(NexusTest, run_value_tasks_merge_results) {
    std::atomic<size_t> value = 0;
    auto task = [&value](Nexus &) {
                    return value.fetch_add(1, std::memory_order_relaxed) + 1;
                };
    Nexus ctx(10);
    EXPECT_EQ(ctx.run(task, Nexus::merge_sum()), 55);
    EXPECT_EQ(value, 10);
}

TEST(NexusTest, run_inline_voted_loop) {
    // Each thread wants to run a loop <thread_id> times, but the loop
    // condition is a vote between all threads. After 3 iterations,
    // threads 0,1,2,3 vote to exit while threads 4,5,6,7,8 vote to
    // continue. After 4 iterations, threads 0,1,2,3,4 vote to exit
    // while threads 5,6,7,8 vote to continue. The result is that all
    // threads end up doing the loop exactly 4 times.
    auto res = Nexus(9).run([](Nexus &ctx) {
                                size_t times = 0;
                                for (size_t i = 0; ctx.vote(i < ctx.thread_id()); ++i) {
                                    ++times;
                                }
                                return times;
                            }, [](auto a, auto b){ EXPECT_EQ(a, b); return a; });
    EXPECT_EQ(res, 4);
}

TEST(NexusTest, run_return_type_decay) {
    int value = 3;
    auto task = [&](Nexus &)->int&{ return value; };
    Nexus ctx(3);
    auto res = ctx.run(task);
    EXPECT_EQ(res, 3);
    EXPECT_EQ(std::addressof(value), std::addressof(task(ctx)));
    using task_res_t = decltype(task(ctx));
    using run_res_t = decltype(ctx.run(task));
    static_assert(std::same_as<task_res_t, int&>);
    static_assert(std::same_as<run_res_t, int>);
}

TEST(NexusTest, example_multi_threaded_unit_test) {
    int a = 0;
    int b = 0;
    auto work = [&](Nexus &ctx) {
        EXPECT_EQ(ctx.num_threads(), 2);
        if (ctx.thread_id() == 0) {
            a = 5;
            ctx.barrier();
            EXPECT_EQ(b, 7);
        } else {
            b = 7;
            ctx.barrier();
            EXPECT_EQ(a, 5);
        }
    };
    Nexus(2).run(work);
    EXPECT_EQ(a, 5);
    EXPECT_EQ(b, 7);
}

GTEST_MAIN_RUN_ALL_TESTS()
