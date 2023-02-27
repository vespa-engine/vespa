// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fnet/transport.h>
#include <vespa/searchcore/proton/common/scheduled_forward_executor.h>
#include <vespa/searchcore/proton/common/scheduledexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <thread>

using vespalib::Executor;
using namespace proton;
using Task = Executor::Task;
using vespalib::makeLambdaTask;

namespace {

class TestTask : public Task {
private:
    vespalib::CountDownLatch &_latch;
public:
    TestTask(vespalib::CountDownLatch & latch) : _latch(latch) { }
    void run() override { _latch.countDown(); }
};

}

template <typename T>
std::unique_ptr<T> make_scheduled_executor(FNET_Transport& transport, vespalib::Executor& executor);

template <>
std::unique_ptr<ScheduledExecutor>
make_scheduled_executor<ScheduledExecutor>(FNET_Transport& transport, vespalib::Executor&) {
    return std::make_unique<ScheduledExecutor>(transport);
}

template <>
std::unique_ptr<ScheduledForwardExecutor>
make_scheduled_executor<ScheduledForwardExecutor>(FNET_Transport& transport, vespalib::Executor& executor) {
    return std::make_unique<ScheduledForwardExecutor>(transport, executor);
}

template <typename ScheduledT>
class ScheduledExecutorTest : public testing::Test {
public:
    FNET_Transport transport;
    vespalib::ThreadStackExecutor executor;
    std::unique_ptr<ScheduledT> timer;

    ScheduledExecutorTest()
        : transport(),
          executor(1)
    {
        transport.Start();
        timer = make_scheduled_executor<ScheduledT>(transport, executor);
    }
    ~ScheduledExecutorTest() {
        transport.ShutDown(true);
    }
};

using ScheduledTypes = ::testing::Types<ScheduledExecutor, ScheduledForwardExecutor>;

TYPED_TEST_SUITE(ScheduledExecutorTest, ScheduledTypes);

TYPED_TEST(ScheduledExecutorTest, test_scheduling) {
    vespalib::CountDownLatch latch1(3);
    vespalib::CountDownLatch latch2(2);
    auto handleA = this->timer->scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 100ms, 200ms);
    auto handleB = this->timer->scheduleAtFixedRate(std::make_unique<TestTask>(latch2), 500ms, 500ms);
    EXPECT_TRUE(latch1.await(60s));
    EXPECT_TRUE(latch2.await(60s));
}

TYPED_TEST(ScheduledExecutorTest, test_drop_handle) {
    vespalib::CountDownLatch latch1(2);
    auto handleA = this->timer->scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 2s, 3s);
    handleA.reset();
    EXPECT_TRUE(!latch1.await(3s));
    auto handleB = this->timer->scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 200ms, 300ms);
    EXPECT_TRUE(latch1.await(60s));
}

TYPED_TEST(ScheduledExecutorTest, test_only_one_instance_running) {
    vespalib::Gate latch;
    std::atomic<uint64_t> counter = 0;
    auto handleA = this->timer->scheduleAtFixedRate(makeLambdaTask([&]() { counter++; latch.await();}), 0ms, 1ms);
    std::this_thread::sleep_for(2s);
    EXPECT_EQ(1, counter);
    latch.countDown();
    std::this_thread::sleep_for(2s);
    EXPECT_GT(counter, 10);
}

TYPED_TEST(ScheduledExecutorTest, test_sync_delete) {
    vespalib::Gate latch;
    std::atomic<uint64_t> counter = 0;
    std::atomic<uint64_t> reset_counter = 0;
    auto handleA = this->timer->scheduleAtFixedRate(makeLambdaTask([&]() { counter++; latch.await();}), 0ms, 1ms);
    auto handleB = this->timer->scheduleAtFixedRate(makeLambdaTask([&]() { handleA.reset(); reset_counter++; }), 0ms, 1ms);
    std::this_thread::sleep_for(2s);
    EXPECT_EQ(1, counter);
    EXPECT_EQ(0, reset_counter);
    latch.countDown();
    std::this_thread::sleep_for(2s);
    EXPECT_EQ(1, counter);
    EXPECT_GT(reset_counter, 10);
    EXPECT_EQ(nullptr, handleA.get());
    EXPECT_FALSE(nullptr == handleB.get());
}

GTEST_MAIN_RUN_ALL_TESTS()
