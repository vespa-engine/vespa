// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/thread.h>

using namespace vespalib;

VESPA_THREAD_STACK_TAG(test_agent_thread);

struct Agent : public Runnable {
    bool was_run;
    Agent() : was_run(false) {}
    void run() override {
        was_run = true;
    }
};

void my_fun(bool *was_run) {
    *was_run = true;
}

TEST("run vespalib::Runnable with init function") {
    Agent agent;
    {
        auto thread = Thread::start(agent, test_agent_thread);
    }
    EXPECT_TRUE(agent.was_run);
}

TEST("run custom function") {
    bool was_run = false;
    {
        auto thread = Thread::start(my_fun, &was_run);
    }
    EXPECT_TRUE(was_run);
}

TEST("join multiple times (including destructor)") {
    bool was_run = false;
    {
        auto thread = Thread::start(my_fun, &was_run);
        thread.join();
        thread.join();
        thread.join();
    }
    EXPECT_TRUE(was_run);
}

TEST_MAIN() { TEST_RUN_ALL(); }
