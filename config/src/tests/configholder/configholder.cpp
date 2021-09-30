// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configholder.h>
#include <thread>

using namespace config;

namespace {

constexpr vespalib::duration ONE_SEC = 1s;
constexpr vespalib::duration ONE_MINUTE = 60s;

}

TEST("Require that element order is correct")
{
    ConfigValue value(std::vector<vespalib::string>(), "foo");
    ConfigValue value2(std::vector<vespalib::string>(), "bar");

    ConfigHolder holder;
    holder.handle(std::make_unique<ConfigUpdate>(value, true, 0));
    std::unique_ptr<ConfigUpdate> update = holder.provide();
    ASSERT_TRUE(value == update->getValue());

    holder.handle(std::make_unique<ConfigUpdate>(value, false, 1));
    holder.handle(std::make_unique<ConfigUpdate>(value2, false, 2));
    update = holder.provide();
    ASSERT_TRUE(value2 == update->getValue());
}

TEST("Require that waiting is done")
{
    ConfigValue value;

    ConfigHolder holder;
    vespalib::Timer timer;
    holder.wait(1000ms);
    EXPECT_GREATER_EQUAL(timer.elapsed(), ONE_SEC);
    EXPECT_LESS(timer.elapsed(), ONE_MINUTE);

    holder.handle(std::make_unique<ConfigUpdate>(value, true, 0));
    ASSERT_TRUE(holder.wait(100ms));
}

TEST("Require that polling for elements work")
{
    ConfigValue value;

    ConfigHolder holder;
    ASSERT_FALSE(holder.poll());
    holder.handle(std::make_unique<ConfigUpdate>(value, true, 0));
    ASSERT_TRUE(holder.poll());
    holder.provide();
    ASSERT_FALSE(holder.poll());
}

TEST("Require that negative time does not mean forever.") {
    ConfigHolder holder;
    vespalib::Timer timer;
    ASSERT_FALSE(holder.poll());
    ASSERT_FALSE(holder.wait(10ms));
    ASSERT_FALSE(holder.wait(0ms));
    ASSERT_FALSE(holder.wait(-1ms));
    ASSERT_FALSE(holder.wait(-7ms));
    EXPECT_LESS(timer.elapsed(), ONE_MINUTE);
}

TEST_MT_F("Require that wait is interrupted", 2, ConfigHolder)
{
    if (thread_id == 0) {
        vespalib::Timer timer;
        TEST_BARRIER();
        f.wait(1000ms);
        EXPECT_LESS(timer.elapsed(), ONE_MINUTE);
        EXPECT_GREATER(timer.elapsed(), 400ms);
        TEST_BARRIER();
    } else {
        TEST_BARRIER();
        std::this_thread::sleep_for(500ms);
        f.interrupt();
        TEST_BARRIER();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
