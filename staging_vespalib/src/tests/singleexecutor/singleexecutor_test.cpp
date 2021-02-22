// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/vespalib/util/singleexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/alloc.h>
#include <atomic>

using namespace vespalib;

VESPA_THREAD_STACK_TAG(sequenced_executor)

TEST("test that all tasks are executed") {

    std::atomic<uint64_t> counter(0);
    SingleExecutor executor(sequenced_executor, 10);

    for (uint64_t i(0); i < 10; i++) {
        executor.execute(makeLambdaTask([&counter] {counter++;}));
    }
    executor.sync();
    EXPECT_EQUAL(10u, counter);

    counter = 0;
    for (uint64_t i(0); i < 10000; i++) {
        executor.execute(makeLambdaTask([&counter] {counter++;}));
    }
    executor.sync();
    EXPECT_EQUAL(10000u, counter);
}

void verifyResizeTaskLimit(bool up) {
    std::mutex lock;
    std::condition_variable cond;
    std::atomic<uint64_t> started(0);
    std::atomic<uint64_t> allowed(0);
    SingleExecutor executor(sequenced_executor, 10);

    uint32_t targetTaskLimit = up ? 20 : 5;
    uint32_t roundedTaskLimit = roundUp2inN(targetTaskLimit);
    EXPECT_NOT_EQUAL(16u, roundedTaskLimit);

    for (uint64_t i(0); i < 10; i++) {
        executor.execute(makeLambdaTask([&lock, &cond, &started, &allowed] {
            started++;
            std::unique_lock guard(lock);
            while (allowed < started) {
                cond.wait_for(guard, 1ms);
            }
        }));
    }
    while (started < 1);
    EXPECT_EQUAL(1u, started);
    executor.setTaskLimit(targetTaskLimit);
    EXPECT_EQUAL(16u, executor.getTaskLimit());
    allowed = 5;
    while (started < 6);
    EXPECT_EQUAL(6u, started);
    EXPECT_EQUAL(16u, executor.getTaskLimit());
    allowed = 10;
    while (started < 10);
    EXPECT_EQUAL(10u, started);
    EXPECT_EQUAL(16u, executor.getTaskLimit());
    executor.execute(makeLambdaTask([&lock, &cond, &started, &allowed] {
        started++;
        std::unique_lock guard(lock);
        while (allowed < started) {
            cond.wait_for(guard, 1ms);
        }
    }));
    while (started < 11);
    EXPECT_EQUAL(11u, started);
    EXPECT_EQUAL(roundedTaskLimit, executor.getTaskLimit());
    allowed = 11;
}
TEST("test that resizing up and down works") {
    TEST_DO(verifyResizeTaskLimit(true));
    TEST_DO(verifyResizeTaskLimit(false));


}

TEST_MAIN() { TEST_RUN_ALL(); }
