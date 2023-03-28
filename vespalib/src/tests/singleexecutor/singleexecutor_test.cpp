// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/vespalib/util/singleexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/alloc.h>
#include <vespa/vespalib/util/gate.h>
#include <atomic>

using namespace vespalib;

VESPA_THREAD_STACK_TAG(sequenced_executor)

TEST("test that all tasks are executed") {

    std::atomic<uint64_t> counter(0);
    SingleExecutor executor(sequenced_executor, 10, true, 1, 100ms);

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

TEST("test that executor can overflow") {
    constexpr size_t NUM_TASKS = 1000;
    std::atomic<uint64_t> counter(0);
    vespalib::Gate gate;
    SingleExecutor executor(sequenced_executor, 10, false, 1, 1ms);
    executor.execute(makeLambdaTask([&gate] { gate.await();}));

    for(size_t i(0); i < NUM_TASKS; i++) {
        executor.execute(makeLambdaTask([&counter, i] {
            EXPECT_EQUAL(i, counter);
            counter++;
        }));
    }
    EXPECT_EQUAL(0u, counter);
    ExecutorStats stats = executor.getStats();
    EXPECT_EQUAL(NUM_TASKS + 1, stats.acceptedTasks);
    EXPECT_EQUAL(NUM_TASKS, stats.queueSize.max());
    gate.countDown();
    executor.sync();
    EXPECT_EQUAL(NUM_TASKS, counter);
}

void verifyResizeTaskLimit(bool up) {
    std::mutex lock;
    std::condition_variable cond;
    std::atomic<uint64_t> started(0);
    std::atomic<uint64_t> allowed(0);
    constexpr uint32_t INITIAL = 20;
    const uint32_t INITIAL_2inN = roundUp2inN(INITIAL);
    double waterMarkRatio = 0.5;
    SingleExecutor executor(sequenced_executor, INITIAL, true, INITIAL*waterMarkRatio, 10ms);
    EXPECT_EQUAL(INITIAL_2inN, executor.getTaskLimit());
    EXPECT_EQUAL(uint32_t(INITIAL_2inN*waterMarkRatio), executor.get_watermark());

    uint32_t targetTaskLimit = up ? 40 : 5;
    uint32_t roundedTaskLimit = roundUp2inN(targetTaskLimit);
    EXPECT_NOT_EQUAL(INITIAL_2inN, roundedTaskLimit);

    for (uint64_t i(0); i < INITIAL; i++) {
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
    EXPECT_EQUAL(INITIAL_2inN, executor.getTaskLimit());
    EXPECT_EQUAL(INITIAL_2inN*waterMarkRatio, executor.get_watermark());
    allowed = 5;
    while (started < 6);
    EXPECT_EQUAL(6u, started);
    EXPECT_EQUAL(INITIAL_2inN, executor.getTaskLimit());
    allowed = INITIAL;
    while (started < INITIAL);
    EXPECT_EQUAL(INITIAL, started);
    EXPECT_EQUAL(INITIAL_2inN, executor.getTaskLimit());
    executor.execute(makeLambdaTask([&lock, &cond, &started, &allowed] {
        started++;
        std::unique_lock guard(lock);
        while (allowed < started) {
            cond.wait_for(guard, 1ms);
        }
    }));
    while (started < INITIAL + 1);
    EXPECT_EQUAL(INITIAL + 1, started);
    EXPECT_EQUAL(roundedTaskLimit, executor.getTaskLimit());
    EXPECT_EQUAL(roundedTaskLimit*waterMarkRatio, executor.get_watermark());
    allowed = INITIAL + 1;
}

TEST("test that resizing up and down works") {
    TEST_DO(verifyResizeTaskLimit(true));
    TEST_DO(verifyResizeTaskLimit(false));


}

TEST_MAIN() { TEST_RUN_ALL(); }
