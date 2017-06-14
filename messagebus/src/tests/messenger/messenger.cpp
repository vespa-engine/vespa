// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/messenger.h>
#include <vespa/vespalib/util/barrier.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

TEST_SETUP(Test);

class ThrowException : public Messenger::ITask {
public:
    void run() override {
        throw std::exception();
    }

    uint8_t priority() const override {
        return 0;
    }
};

class BarrierTask : public Messenger::ITask {
private:
    vespalib::Barrier &_barrier;

public:
    BarrierTask(vespalib::Barrier &barrier)
        : _barrier(barrier)
    {
        // empty
    }

    void run() override {
        _barrier.await();
    }

    uint8_t priority() const override {
        return 0;
    }
};

int
Test::Main()
{
    TEST_INIT("messenger_test");

    Messenger msn;
    msn.start();

    vespalib::Barrier barrier(2);
    msn.enqueue(Messenger::ITask::UP(new ThrowException()));
    msn.enqueue(Messenger::ITask::UP(new BarrierTask(barrier)));

    barrier.await();
    ASSERT_TRUE(msn.isEmpty());

    TEST_DONE();
}
