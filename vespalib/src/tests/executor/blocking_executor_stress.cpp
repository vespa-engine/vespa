// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/executor.h>
#include <atomic>

using namespace vespalib;
using vespalib::test::Nexus;

std::atomic<size_t> tasks_run;

size_t do_stuff(size_t size) {
    size_t value = 0;
    for (size_t i = 0; i < size; ++i) {
        for (size_t j = 0; j < i; ++j) {
            for (size_t k = 0; k < j; ++k) {
                value += (i * j * k);
                value *= (i + j + k);
            }
        }
    }
    return value;
}

struct MyTask : Executor::Task {
    size_t size;
    size_t data;
    MyTask(size_t size_in) : size(size_in), data(0) {}
    void run() override {
        data += do_stuff(size);
        ++tasks_run;
        data += do_stuff(size);
        data += do_stuff(size);
    }
};

TEST(BlockingExecutorStressTest, stress_test_block_thread_stack_executor)
{
    size_t num_threads = 8;
    BlockingThreadStackExecutor f1(4, 1000);
    auto task = [&](Nexus &ctx){
                    auto thread_id = ctx.thread_id();
                    size_t loop_cnt = 100;
                    for (size_t i = 0; i < loop_cnt; ++i) {
                        auto result = f1.execute(std::make_unique<MyTask>(thread_id));
                        EXPECT_TRUE(result.get() == nullptr);
                    }
                    ctx.barrier();
                    if (thread_id == 0) {
                        f1.shutdown().sync();
                    }
                    ctx.barrier();
                    EXPECT_EQ((loop_cnt * num_threads), tasks_run);
                };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
