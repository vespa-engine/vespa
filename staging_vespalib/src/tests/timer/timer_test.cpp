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
    timer.scheduleAtFixedRate(Task::UP(new TestTask(latch1)), 0.1, 0.2);
    timer.scheduleAtFixedRate(Task::UP(new TestTask(latch2)), 0.5, 0.5);
    EXPECT_TRUE(latch1.await(60000));
    EXPECT_TRUE(latch2.await(60000));
}

void Test::testReset()
{
    vespalib::CountDownLatch latch1(2);
    ScheduledExecutor timer;
    timer.scheduleAtFixedRate(Task::UP(new TestTask(latch1)), 2.0, 3.0);
    timer.reset();
    EXPECT_TRUE(!latch1.await(3000));
    timer.scheduleAtFixedRate(Task::UP(new TestTask(latch1)), 0.2, 0.3);
    EXPECT_TRUE(latch1.await(60000));
}

TEST_APPHOOK(Test)
