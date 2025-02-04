// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-my.h"
#include <vespa/config/common/misc.h>
#include <vespa/config/common/configholder.h>
#include <vespa/config/subscription/configsubscription.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/nexus.h>
#include <thread>

using namespace config;
using vespalib::test::Nexus;

namespace {

struct SourceFixture
{
    int numClose;
    int numGetConfig;
    int numReload;
    SourceFixture()
        : numClose(0),
          numGetConfig(0),
          numReload(0)
    { }
};

struct MySource : public Source
{
    MySource(SourceFixture * src)
        : source(src)
    {}

    void getConfig() override { source->numGetConfig++; }
    void close() override { source->numClose++; }
    void reload(int64_t gen) override { (void) gen; source->numReload++; }

    SourceFixture * source;
};

struct SubscriptionFixture
{
    std::shared_ptr<IConfigHolder> holder;
    ConfigSubscription sub;
    SourceFixture src;
    SubscriptionFixture(const ConfigKey & key)
        : holder(new ConfigHolder()),
          sub(0, key, holder, std::make_unique<MySource>(&src))
    {
    }
    ~SubscriptionFixture();
};

SubscriptionFixture::~SubscriptionFixture() = default;

vespalib::steady_time
deadline(vespalib::duration timeout) {
    return vespalib::steady_clock::now() + timeout;
}
}

TEST(SubscriptionTest, requireThatKeyIsReturned)
{
    ConfigKey f1("foo", "bar", "bim", "boo");
    SubscriptionFixture f2(f1);
    ASSERT_TRUE(f1 == f2.sub.getKey());
}

TEST(SubscriptionTest, requireThatUpdateReturns)
{
    SubscriptionFixture f1(ConfigKey::create<MyConfig>("myid"));
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
    ASSERT_TRUE(f1.sub.nextUpdate(0, deadline(0ms)));
    ASSERT_TRUE(f1.sub.hasChanged());
    ASSERT_EQ(1, f1.sub.getGeneration());
}

TEST(SubscriptionTest, requireThatNextUpdateBlocks)
{
    SubscriptionFixture f1(ConfigKey::create<MyConfig>("myid"));
    ASSERT_FALSE(f1.sub.nextUpdate(0, deadline(0ms)));
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
    vespalib::Timer timer;
    ASSERT_FALSE(f1.sub.nextUpdate(1, deadline(500ms)));
    ASSERT_TRUE(timer.elapsed() > 400ms);
}

TEST(SubscriptionTest, requireThatNextUpdateReturnsWhenNotified)
{
    constexpr size_t num_threads = 2;
    SubscriptionFixture f1(ConfigKey::create<MyConfig>("myid"));
    auto task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            vespalib::Timer timer;
            f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
            ASSERT_TRUE(f1.sub.nextUpdate(2, deadline(5000ms)));
            ASSERT_TRUE(timer.elapsed() > 200ms);
        } else {
            std::this_thread::sleep_for(500ms);
            f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
        }
    };
    Nexus::run(num_threads, task);
}


TEST(SubscriptionTest, requireThatNextUpdateReturnsInterrupted)
{
    constexpr size_t num_threads = 2;
    SubscriptionFixture f1(ConfigKey::create<MyConfig>("myid"));
    auto task = [&f1](Nexus& ctx) {
        auto thread_id = ctx.thread_id();
        if (thread_id == 0) {
            vespalib::Timer timer;
            f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
            ASSERT_TRUE(f1.sub.nextUpdate(1, deadline(5000ms)));
            ASSERT_TRUE(timer.elapsed() > 300ms);
        } else {
            std::this_thread::sleep_for(500ms);
            f1.sub.close();
        }
    };
    Nexus::run(num_threads, task);
}

TEST(SubscriptionTest, Require_that_isChanged_takes_generation_into_account)
{
    SubscriptionFixture f1(ConfigKey::create<MyConfig>("myid"));
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(StringVector(), "a"), true, 1));
    ASSERT_TRUE(f1.sub.nextUpdate(0, deadline(0ms)));
    f1.sub.flip();
    ASSERT_EQ(1, f1.sub.getLastGenerationChanged());
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(StringVector(), "b"), true, 2));
    ASSERT_TRUE(f1.sub.nextUpdate(1, deadline(0ms)));
    f1.sub.flip();
    ASSERT_EQ(2, f1.sub.getLastGenerationChanged());
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), false, 3));
    ASSERT_TRUE(f1.sub.nextUpdate(2, deadline(0ms)));
    f1.sub.flip();
    ASSERT_EQ(2, f1.sub.getLastGenerationChanged());
}

GTEST_MAIN_RUN_ALL_TESTS()
