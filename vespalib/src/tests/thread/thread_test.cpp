// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/thread.h>
#include <thread>

using namespace vespalib;

VESPA_THREAD_STACK_TAG(test_agent_thread);

struct Agent : public Runnable {
    bool started;
    int loopCnt;
    Agent() : started(false), loopCnt(0) {}
    void run() override {
        started = true;
        Thread &thread = Thread::currentThread();
        while (thread.slumber(60.0)) {
            ++loopCnt;
        }
    }
};

TEST("thread never started") {
    Agent agent;
    {
        Thread thread(agent, test_agent_thread);
    }
    EXPECT_TRUE(!agent.started);
    EXPECT_EQUAL(0, agent.loopCnt);
}

TEST("normal operation") {
    Agent agent;
    {
        Thread thread(agent, test_agent_thread);
        thread.start();
        std::this_thread::sleep_for(20ms);
        thread.stop().join();
    }
    EXPECT_TRUE(agent.started);
    EXPECT_EQUAL(0, agent.loopCnt);
}

TEST("stop before start") {
    Agent agent;
    {
        Thread thread(agent, test_agent_thread);
        thread.stop();
        thread.start();
        thread.join();
    }
    EXPECT_TRUE(agent.started);
    EXPECT_EQUAL(0, agent.loopCnt);
}

TEST_MAIN() { TEST_RUN_ALL(); }
