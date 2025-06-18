// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config/common/configholder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <thread>

using namespace config;
using vespalib::test::Nexus;

namespace {

constexpr vespalib::duration ONE_SEC = 1s;
constexpr vespalib::duration ONE_MINUTE = 60s;

}

TEST(ConfigHolderTest, Require_that_element_order_is_correct)
{
    ConfigValue value(StringVector(), "foo");
    ConfigValue value2(StringVector(), "bar");

    ConfigHolder holder;
    holder.handle(std::make_unique<ConfigUpdate>(value, true, 0));
    std::unique_ptr<ConfigUpdate> update = holder.provide();
    ASSERT_TRUE(value == update->getValue());

    holder.handle(std::make_unique<ConfigUpdate>(value, false, 1));
    holder.handle(std::make_unique<ConfigUpdate>(value2, false, 2));
    update = holder.provide();
    ASSERT_TRUE(value2 == update->getValue());
}

TEST(ConfigHolderTest, Require_that_waiting_is_done)
{
    ConfigValue value;

    ConfigHolder holder;
    vespalib::Timer timer;
    holder.wait_for(1000ms);
    EXPECT_GE(timer.elapsed(), ONE_SEC);
    EXPECT_LT(timer.elapsed(), ONE_MINUTE);

    holder.handle(std::make_unique<ConfigUpdate>(value, true, 0));
    ASSERT_TRUE(holder.wait_for(100ms));
}

TEST(ConfigHolderTest, Require_that_polling_for_elements_work)
{
    ConfigValue value;

    ConfigHolder holder;
    ASSERT_FALSE(holder.poll());
    holder.handle(std::make_unique<ConfigUpdate>(value, true, 0));
    ASSERT_TRUE(holder.poll());
    holder.provide();
    ASSERT_FALSE(holder.poll());
}

TEST(ConfigHolderTest, Require_that_negative_time_does_not_mean_forever)
{
    ConfigHolder holder;
    vespalib::Timer timer;
    ASSERT_FALSE(holder.poll());
    ASSERT_FALSE(holder.wait_for(10ms));
    ASSERT_FALSE(holder.wait_for(0ms));
    ASSERT_FALSE(holder.wait_for(-1ms));
    ASSERT_FALSE(holder.wait_for(-7ms));
    EXPECT_LT(timer.elapsed(), ONE_MINUTE);
}

TEST(ConfigHolderTest, Require_that_wait_is_interrupted_on_close) {
    constexpr size_t num_threads = 2;
    ConfigHolder f;
    auto task = [&f](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            vespalib::Timer timer;
            ctx.barrier();
            f.wait_for(1000ms);
            EXPECT_LT(timer.elapsed(), ONE_MINUTE);
            EXPECT_GT(timer.elapsed(), 400ms);
            ctx.barrier();
        } else {
            ctx.barrier();
            std::this_thread::sleep_for(500ms);
            f.close();
            ctx.barrier();
        }
    };
    Nexus::run(num_threads, task);
}

GTEST_MAIN_RUN_ALL_TESTS()
