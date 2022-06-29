// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/messagebus/messenger.h>
#include <vespa/vespalib/util/barrier.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace mbus;

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

TEST("messenger_test") {

    Messenger msn;
    msn.start();

    vespalib::Barrier barrier(2);
    msn.enqueue(std::make_unique<ThrowException>());
    msn.enqueue(std::make_unique<BarrierTask>(barrier));

    barrier.await();
    ASSERT_TRUE(msn.isEmpty());
}

TEST_MAIN() { TEST_RUN_ALL(); }