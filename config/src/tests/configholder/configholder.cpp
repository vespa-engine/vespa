// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configholder.h>

using namespace config;
using namespace std::chrono_literals;

namespace {

constexpr double ONE_SEC = 1000.0;
constexpr double ONE_MINUTE = 60 * ONE_SEC;

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
    FastOS_Time timer;
    timer.SetNow();
    holder.wait(1000ms);
    EXPECT_GREATER_EQUAL(timer.MilliSecsToNow(), ONE_SEC);
    EXPECT_LESS(timer.MilliSecsToNow(), ONE_MINUTE);

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
    FastOS_Time timer;
    timer.SetNow();
    ASSERT_FALSE(holder.poll());
    ASSERT_FALSE(holder.wait(10));
    ASSERT_FALSE(holder.wait(0));
    ASSERT_FALSE(holder.wait(-1));
    ASSERT_FALSE(holder.wait(-7));
    EXPECT_LESS(timer.MilliSecsToNow(), ONE_MINUTE);
}

TEST_MT_F("Require that wait is interrupted", 2, ConfigHolder)
{
    if (thread_id == 0) {
        FastOS_Time timer;
        timer.SetNow();
        TEST_BARRIER();
        f.wait(1000ms);
        EXPECT_LESS(timer.MilliSecsToNow(), ONE_MINUTE);
        EXPECT_GREATER(timer.MilliSecsToNow(), 400.0);
        TEST_BARRIER();
    } else {
        TEST_BARRIER();
        std::this_thread::sleep_for(500ms);
        f.interrupt();
        TEST_BARRIER();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
