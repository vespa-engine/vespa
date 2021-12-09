// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

struct MyHandler : Handler<int> {
    std::vector<int> values;
    ~MyHandler() override;
    void handle(std::unique_ptr<int> value) override {
        values.push_back(*value);
        vespalib::Thread::sleep(10); // for improved coverage
    }
};

MyHandler::~MyHandler() = default;

VESPA_THREAD_STACK_TAG(test_thread);

TEST("handler thread") {
    MyHandler handler;
    HandlerThread<int> th(handler, test_thread);
    th.handle(std::unique_ptr<int>(new int(1)));
    th.handle(std::unique_ptr<int>(new int(2)));
    th.handle(std::unique_ptr<int>(new int(3)));
    th.join();
    th.handle(std::unique_ptr<int>(new int(4)));
    th.handle(std::unique_ptr<int>(new int(5)));
    ASSERT_EQUAL(3u, handler.values.size());
    EXPECT_EQUAL(1, handler.values[0]);
    EXPECT_EQUAL(2, handler.values[1]);
    EXPECT_EQUAL(3, handler.values[2]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
