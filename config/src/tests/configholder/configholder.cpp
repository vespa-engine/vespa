// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configholder.h>

using namespace config;

TEST("Require that element order is correct")
{
    ConfigValue value(std::vector<vespalib::string>(), "foo");
    ConfigValue value2(std::vector<vespalib::string>(), "bar");

    ConfigHolder holder;
    holder.handle(ConfigUpdate::UP(new ConfigUpdate(value, true, 0)));
    std::unique_ptr<ConfigUpdate> update = holder.provide();
    ASSERT_TRUE(value == update->getValue());

    holder.handle(ConfigUpdate::UP(new ConfigUpdate(value, false, 1)));
    holder.handle(ConfigUpdate::UP(new ConfigUpdate(value2, false, 2)));
    update = holder.provide();
    ASSERT_TRUE(value2 == update->getValue());
}

TEST("Require that waiting is done")
{
    ConfigValue value;

    ConfigHolder holder;
    FastOS_Time timer;
    timer.SetNow();
    holder.wait(1000);
    ASSERT_TRUE(timer.MilliSecsToNow() >= 1000);
    ASSERT_TRUE(timer.MilliSecsToNow() < 60000);

    timer.SetNow();
    holder.handle(ConfigUpdate::UP(new ConfigUpdate(value, true, 0)));
    holder.wait(100);
    ASSERT_TRUE(timer.MilliSecsToNow() >= 100);
}

TEST("Require that polling for elements work")
{
    ConfigValue value;

    ConfigHolder holder;
    ASSERT_FALSE(holder.poll());
    holder.handle(ConfigUpdate::UP(new ConfigUpdate(value, true, 0)));
    ASSERT_TRUE(holder.poll());
    holder.provide();
    ASSERT_TRUE(holder.poll());
}

TEST_MT_F("Require that wait is interrupted", 2, ConfigHolder)
{
    if (thread_id == 0) {
        FastOS_Time timer;
        timer.SetNow();
        TEST_BARRIER();
        f.wait(1000);
        EXPECT_TRUE(timer.MilliSecsToNow() < 60000.0);
        EXPECT_TRUE(timer.MilliSecsToNow() > 400.0);
        TEST_BARRIER();
    } else {
        TEST_BARRIER();
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
        f.interrupt();
        TEST_BARRIER();
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
