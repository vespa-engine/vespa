// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/thread.h>
#include <vespa/fnet/transport.h>
#include <vespa/searchcore/proton/common/scheduled_forward_executor.h>
#include <vespa/searchcore/proton/common/scheduledexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/count_down_latch.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using vespalib::Executor;
using namespace proton;
using Task = Executor::Task;

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
    FastOS_ThreadPool threadPool;
    FNET_Transport transport;
    vespalib::ThreadStackExecutor executor;
    std::unique_ptr<ScheduledT> timer;

    ScheduledExecutorTest()
        : threadPool(64_Ki),
          transport(),
          executor(1, 64_Ki)
    {
        transport.Start(&threadPool);
        timer = make_scheduled_executor<ScheduledT>(transport, executor);
    }
    ~ScheduledExecutorTest() {
        timer->reset();
        transport.ShutDown(true);
    }
};

using ScheduledTypes = ::testing::Types<ScheduledExecutor, ScheduledForwardExecutor>;

TYPED_TEST_SUITE(ScheduledExecutorTest, ScheduledTypes);

TYPED_TEST(ScheduledExecutorTest, test_scheduling) {
    vespalib::CountDownLatch latch1(3);
    vespalib::CountDownLatch latch2(2);
    this->timer->scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 100ms, 200ms);
    this->timer->scheduleAtFixedRate(std::make_unique<TestTask>(latch2), 500ms, 500ms);
    EXPECT_TRUE(latch1.await(60s));
    EXPECT_TRUE(latch2.await(60s));
}

TYPED_TEST(ScheduledExecutorTest, test_reset) {
    vespalib::CountDownLatch latch1(2);
    this->timer->scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 2s, 3s);
    this->timer->reset();
    EXPECT_TRUE(!latch1.await(3s));
    this->timer->scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 200ms, 300ms);
    EXPECT_TRUE(latch1.await(60s));
}

GTEST_MAIN_RUN_ALL_TESTS()
