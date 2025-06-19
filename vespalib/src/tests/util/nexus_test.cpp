// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/require.h>
#include <vespa/vespalib/util/exceptions.h>
#include <format>

using vespalib::test::Nexus;
using vespalib::IllegalStateException;

TEST(NexusTest, run_void_tasks) {
    std::atomic<size_t> value = 0;
    auto task = [&value](Nexus &) {
                    value.fetch_add(1, std::memory_order_relaxed);
                };
    Nexus::run(10, task);
    EXPECT_EQ(value, 10);
    Nexus::run(10, task);
    EXPECT_EQ(value, 20);
}

TEST(NexusTest, run_value_tasks_select_thread_0) {
    std::atomic<size_t> value = 0;
    auto task = [&value](Nexus &ctx) {
                    value.fetch_add(1, std::memory_order_relaxed);
                    return ctx.thread_id() + 5;
                };
    EXPECT_EQ(Nexus::run(10, task), 5);
    EXPECT_EQ(value, 10);
}

TEST(NexusTest, run_value_tasks_merge_results) {
    std::atomic<size_t> value = 0;
    auto task = [&value](Nexus &) {
                    return value.fetch_add(1, std::memory_order_relaxed) + 1;
                };
    EXPECT_EQ(Nexus::run(10, task, Nexus::merge_sum()), 55);
    EXPECT_EQ(value, 10);
}

TEST(NexusTest, run_inline_voted_loop) {
    // Each thread wants to run a loop <thread_id> times, but the loop
    // condition is a vote between all threads. After 3 iterations,
    // threads 0,1,2,3 vote to exit while threads 4,5,6,7,8 vote to
    // continue. After 4 iterations, threads 0,1,2,3,4 vote to exit
    // while threads 5,6,7,8 vote to continue. The result is that all
    // threads end up doing the loop exactly 4 times.
    auto res = Nexus::run(9, [](Nexus &ctx) {
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
    auto res = Nexus::run(3, task);
    EXPECT_EQ(res, 3);
    using task_res_t = decltype(task(std::declval<Nexus&>()));
    using run_res_t = decltype(Nexus::run(3, task));
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
    Nexus::run(2, work);
    EXPECT_EQ(a, 5);
    EXPECT_EQ(b, 7);
}

TEST(NexusTest, exception_is_captured_and_propagated) {
    size_t num_threads = 10;
    auto task = [&](Nexus &) {
                    throw(std::runtime_error(std::format("failed")));
                };
    // we use the same exception for all threads because it is very
    // hard to force one of the threads to fail first.
    VESPA_EXPECT_EXCEPTION(Nexus::run(num_threads, task), std::runtime_error, "failed");
}

TEST(NexusTest, return_unwinding_destroys_nexus_barrier) {
    size_t num_threads = 10;
    auto task = [&](Nexus &ctx) {
                    if (ctx.thread_id() == 3) {
                        // unwind like 'ASSERT_EQ(2, 3)' would
                        return;
                    }
                    ctx.barrier();
                };
    VESPA_EXPECT_EXCEPTION(Nexus::run(num_threads, task), IllegalStateException, "trying to use destroyed rendezvous");
}

TEST(NexusTest, exception_unwinding_destroys_nexus_barrier_and_happens_before_barrier_exception) {
    size_t num_threads = 10;
    auto task = [&](Nexus &ctx) {
                    if (ctx.thread_id() == 3) {
                        throw(std::runtime_error("failed"));
                    }
                    ctx.barrier();
                };
    // this time the unwinding exception is always propagated since it
    // is captured before the barrier is destroyed.
    VESPA_EXPECT_EXCEPTION(Nexus::run(num_threads, task), std::runtime_error, "failed");
}
