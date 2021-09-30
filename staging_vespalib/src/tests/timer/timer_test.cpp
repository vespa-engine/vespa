// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/scheduledexecutor.h>

using namespace vespalib;
using vespalib::Executor;
typedef Executor::Task Task;

class Test : public TestApp
{
public:
    int Main() override;
    void testScheduling();
    void testReset();
};

class TestTask : public Task {
private:
    vespalib::CountDownLatch &_latch;
public:
    TestTask(vespalib::CountDownLatch & latch) : _latch(latch) { }
    void run() override { _latch.countDown(); }
};

int
Test::Main()
{
    TEST_INIT("timer_test");
    testScheduling();
    testReset();
    TEST_DONE();
}

void Test::testScheduling()
{
    vespalib::CountDownLatch latch1(3);
    vespalib::CountDownLatch latch2(2);
    ScheduledExecutor timer;
    timer.scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 100ms, 200ms);
    timer.scheduleAtFixedRate(std::make_unique<TestTask>(latch2), 500ms, 500ms);
    EXPECT_TRUE(latch1.await(60s));
    EXPECT_TRUE(latch2.await(60s));
}

void Test::testReset()
{
    vespalib::CountDownLatch latch1(2);
    ScheduledExecutor timer;
    timer.scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 2s, 3s);
    timer.reset();
    EXPECT_TRUE(!latch1.await(3s));
    timer.scheduleAtFixedRate(std::make_unique<TestTask>(latch1), 200ms, 300ms);
    EXPECT_TRUE(latch1.await(60s));
}

TEST_APPHOOK(Test)
