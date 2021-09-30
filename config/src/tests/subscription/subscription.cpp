// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/common/configholder.h>
#include <vespa/config/subscription/configsubscription.h>
#include <config-my.h>
#include <thread>

using namespace config;
using namespace std::chrono_literals;

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
        IConfigHolder::SP holder;
        ConfigSubscription sub;
        SourceFixture src;
        SubscriptionFixture(const ConfigKey & key)
            : holder(new ConfigHolder()),
              sub(0, key, holder, Source::UP(new MySource(&src)))
        {
        }
    };
}

TEST_FF("requireThatKeyIsReturned", ConfigKey("foo", "bar", "bim", "boo"), SubscriptionFixture(f1))
{
    ASSERT_TRUE(f1 == f2.sub.getKey());
}

TEST_F("requireThatUpdateReturns", SubscriptionFixture(ConfigKey::create<MyConfig>("myid")))
{
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
    ASSERT_TRUE(f1.sub.nextUpdate(0, 0ms));
    ASSERT_TRUE(f1.sub.hasChanged());
    ASSERT_EQUAL(1, f1.sub.getGeneration());
}

TEST_F("requireThatNextUpdateBlocks", SubscriptionFixture(ConfigKey::create<MyConfig>("myid")))
{
    ASSERT_FALSE(f1.sub.nextUpdate(0, 0ms));
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
    vespalib::Timer timer;
    ASSERT_FALSE(f1.sub.nextUpdate(1, 500ms));
    ASSERT_TRUE(timer.elapsed() > 400ms);
}

TEST_MT_F("requireThatNextUpdateReturnsWhenNotified", 2, SubscriptionFixture(ConfigKey::create<MyConfig>("myid")))
{
    if (thread_id == 0) {
        vespalib::Timer timer;
        f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
        ASSERT_TRUE(f1.sub.nextUpdate(2, 5000ms));
        ASSERT_TRUE(timer.elapsed() > 200ms);
    } else {
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
        f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
    }
}


TEST_MT_F("requireThatNextUpdateReturnsInterrupted", 2, SubscriptionFixture(ConfigKey::create<MyConfig>("myid")))
{
    if (thread_id == 0) {
        vespalib::Timer timer;
        f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), 1, 1));
        ASSERT_TRUE(f1.sub.nextUpdate(1, 5000ms));
        ASSERT_TRUE(timer.elapsed() > 300ms);
    } else {
        std::this_thread::sleep_for(std::chrono::milliseconds(500));
        f1.sub.close();
    }
}

TEST_F("Require that isChanged takes generation into account", SubscriptionFixture(ConfigKey::create<MyConfig>("myid")))
{
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(std::vector<vespalib::string>(), "a"), true, 1));
    ASSERT_TRUE(f1.sub.nextUpdate(0, 0ms));
    f1.sub.flip();
    ASSERT_EQUAL(1, f1.sub.getLastGenerationChanged());
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(std::vector<vespalib::string>(), "b"), true, 2));
    ASSERT_TRUE(f1.sub.nextUpdate(1, 0ms));
    f1.sub.flip();
    ASSERT_EQUAL(2, f1.sub.getLastGenerationChanged());
    f1.holder->handle(std::make_unique<ConfigUpdate>(ConfigValue(), false, 3));
    ASSERT_TRUE(f1.sub.nextUpdate(2, 0ms));
    f1.sub.flip();
    ASSERT_EQUAL(2, f1.sub.getLastGenerationChanged());
}

TEST_MAIN() { TEST_RUN_ALL(); }
