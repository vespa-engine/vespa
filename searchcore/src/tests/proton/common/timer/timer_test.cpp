// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/common/scheduledexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fnet/transport.h>
#include <vespa/fastos/thread.h>

using vespalib::Executor;
typedef Executor::Task Task;

namespace {

class TestTask : public Task {
private:
    vespalib::CountDownLatch &_latch;
public:
    TestTask(vespalib::CountDownLatch & latch) : _latch(latch) { }
    void run() override { _latch.countDown(); }
};

}

TEST("testScheduling") {
    vespalib::CountDownLatch latch1(3);
    vespalib::CountDownLatch latch2(2);
    FastOS_ThreadPool threadPool(64_Ki);
    FNET_Transport transport;
    transport.Start(&threadPool);
    proton::ScheduledExecutor timer(transport);
    timer.scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 100ms, 200ms);
    timer.scheduleAtFixedRate(std::make_unique<TestTask>(latch2), 500ms, 500ms);
    EXPECT_TRUE(latch1.await(60s));
    EXPECT_TRUE(latch2.await(60s));
    timer.reset();
    transport.ShutDown(true);
}

TEST("testReset") {
    vespalib::CountDownLatch latch1(2);
    FastOS_ThreadPool threadPool(64_Ki);
    FNET_Transport transport;
    transport.Start(&threadPool);
    proton::ScheduledExecutor timer(transport);
    timer.scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 2s, 3s);
    timer.reset();
    EXPECT_TRUE(!latch1.await(3s));
    timer.scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 200ms, 300ms);
    EXPECT_TRUE(latch1.await(60s));
    timer.reset();
    transport.ShutDown(true);
}

TEST_MAIN() { TEST_RUN_ALL(); }
