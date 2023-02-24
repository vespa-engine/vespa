// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/thread.h>
#include <iostream>

using namespace vespalib;

VESPA_THREAD_STACK_TAG(test_agent_thread);

struct Agent : public Runnable {
    bool was_run;
    Agent() : was_run(false) {}
    void run() override {
        fprintf(stderr, "agent run in thread %zu\n", thread::as_zu(std::this_thread::get_id()));
        was_run = true;
    }
};

void my_fun(bool *was_run) {
    *was_run = true;
}

Runnable::init_fun_t wrap(Runnable::init_fun_t init, bool *init_called) {
    return [=](Runnable &target)
           {
               fprintf(stderr, "lambda run in thread %zu\n", thread::as_zu(std::this_thread::get_id()));
               *init_called = true;
               return init(target);
           };
}

TEST("main thread") {
    auto my_id = std::this_thread::get_id();
    std::cerr <<    "main thread(with     <<): " << my_id << "\n";
    fprintf(stderr, "main thread(with printf): %zu\n", thread::as_zu(my_id));
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
    EXPECT_TRUE(pool.empty());
    EXPECT_EQUAL(pool.size(), 0u);
    pool.start(my_fun, &was_run);
    EXPECT_TRUE(!pool.empty());
    EXPECT_EQUAL(pool.size(), 1u);
    pool.start(agent, wrap(test_agent_thread, &init_called));
    EXPECT_TRUE(!pool.empty());
    EXPECT_EQUAL(pool.size(), 2u);
    pool.join();
    EXPECT_TRUE(pool.empty());
    EXPECT_EQUAL(pool.size(), 0u);
    EXPECT_TRUE(init_called);
    EXPECT_TRUE(agent.was_run);
    EXPECT_TRUE(was_run);
}

TEST_MAIN() { TEST_RUN_ALL(); }
