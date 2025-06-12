// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>
#include <vespa/vespalib/util/time.h>

using namespace vbench;

struct MyHandler : Handler<int> {
    std::vector<int> values;
    ~MyHandler() override;
    void handle(std::unique_ptr<int> value) override {
        values.push_back(*value);
        std::this_thread::sleep_for(10ms);
    }
};

MyHandler::~MyHandler() = default;

VESPA_THREAD_STACK_TAG(test_thread);

TEST(HandlerThreadTest, handler_thread) {
    MyHandler handler;
    HandlerThread<int> th(handler, test_thread);
    th.handle(std::unique_ptr<int>(new int(1)));
    th.handle(std::unique_ptr<int>(new int(2)));
    th.handle(std::unique_ptr<int>(new int(3)));
    th.join();
    th.handle(std::unique_ptr<int>(new int(4)));
    th.handle(std::unique_ptr<int>(new int(5)));
    ASSERT_EQ(3u, handler.values.size());
    EXPECT_EQ(1, handler.values[0]);
    EXPECT_EQ(2, handler.values[1]);
    EXPECT_EQ(3, handler.values[2]);
}

GTEST_MAIN_RUN_ALL_TESTS()
