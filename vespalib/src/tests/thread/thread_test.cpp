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

Runnable::init_fun_t wrap(Runnable::init_fun_t init, bool *init_called) {
    return [=](Runnable &target)
           {
               *init_called = true;
               return init(target);
           };
}

TEST("run vespalib::Runnable with init function") {
    Agent agent;
    bool init_called = false;
    auto thread = thread::start(agent, wrap(test_agent_thread, &init_called));
    thread.join();
    EXPECT_TRUE(init_called);
    EXPECT_TRUE(agent.was_run);
}

TEST("use thread pool to run multiple things") {
    Agent agent;
    bool init_called = false;
    bool was_run = false;
    ThreadPool pool;
    pool.start(my_fun, &was_run);
    pool.start(agent, wrap(test_agent_thread, &init_called));
    pool.join();
    EXPECT_TRUE(init_called);
    EXPECT_TRUE(agent.was_run);
    EXPECT_TRUE(was_run);
}

TEST_MAIN() { TEST_RUN_ALL(); }
