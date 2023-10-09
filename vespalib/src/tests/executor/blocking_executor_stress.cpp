// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/executor.h>
#include <atomic>

using namespace vespalib;

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

TEST_MT_F("stress test block thread stack executor", 8, BlockingThreadStackExecutor(4, 1000))
{
    size_t loop_cnt = 100;
    for (size_t i = 0; i < loop_cnt; ++i) {
        auto result = f1.execute(std::make_unique<MyTask>(thread_id));
        EXPECT_TRUE(result.get() == nullptr);
    }
    TEST_BARRIER();
    if (thread_id == 0) {
        f1.shutdown().sync();
    }
    TEST_BARRIER();
    EXPECT_EQUAL((loop_cnt * num_threads), tasks_run);
}

TEST_MAIN() { TEST_RUN_ALL(); }
