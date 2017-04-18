// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/common/configholder.h>
#include <vespa/config/subscription/configsubscription.h>
#include <config-my.h>

using namespace config;

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
    f1.holder->handle(ConfigUpdate::UP(new ConfigUpdate(ConfigValue(), 1, 1)));
    ASSERT_TRUE(f1.sub.nextUpdate(0, 0));
    ASSERT_TRUE(f1.sub.hasChanged());
    ASSERT_EQUAL(1, f1.sub.getGeneration());
}

TEST_F("requireThatNextUpdateBlocks", SubscriptionFixture(ConfigKey::create<MyConfig>("myid")))
{
    ASSERT_FALSE(f1.sub.nextUpdate(0, 0));
    f1.holder->handle(ConfigUpdate::UP(new ConfigUpdate(ConfigValue(), 1, 1)));
    FastOS_Time timer;
    timer.SetNow();
    ASSERT_FALSE(f1.sub.nextUpdate(1, 500));
    ASSERT_TRUE(timer.MilliSecsToNow() > 400.0);
}

TEST_MT_F("requireThatNextUpdateReturnsWhenNotified", 2, SubscriptionFixture(ConfigKey::create<MyConfig>("myid")))
{
    if (thread_id == 0) {
        FastOS_Time timer;
        timer.SetNow();
        f1.holder->handle(ConfigUpdate::UP(new ConfigUpdate(ConfigValue(), 1, 1)));
        ASSERT_TRUE(f1.sub.nextUpdate(2, 5000));
        ASSERT_TRUE(timer.MilliSecsToNow() > 200.0);
    } else {
        FastOS_Thread::Sleep(500);
        f1.holder->handle(ConfigUpdate::UP(new ConfigUpdate(ConfigValue(), 1, 1)));
    }
}


TEST_MT_F("requireThatNextUpdateReturnsInterrupted", 2, SubscriptionFixture(ConfigKey::create<MyConfig>("myid")))
{
    if (thread_id == 0) {
        FastOS_Time timer;
        timer.SetNow();
        f1.holder->handle(ConfigUpdate::UP(new ConfigUpdate(ConfigValue(), 1, 1)));
        ASSERT_TRUE(f1.sub.nextUpdate(1, 5000));
        ASSERT_TRUE(timer.MilliSecsToNow() > 300.0);
    } else {
        FastOS_Thread::Sleep(500);
        f1.sub.close();
    }
}

TEST_F("Require that isChanged takes generation into account", SubscriptionFixture(ConfigKey::create<MyConfig>("myid")))
{
    f1.holder->handle(ConfigUpdate::UP(new ConfigUpdate(ConfigValue(), true, 1)));
    ASSERT_TRUE(f1.sub.nextUpdate(0, 0));
    f1.sub.flip();
    ASSERT_EQUAL(1, f1.sub.getLastGenerationChanged());
    f1.holder->handle(ConfigUpdate::UP(new ConfigUpdate(ConfigValue(), true, 2)));
    ASSERT_TRUE(f1.sub.nextUpdate(1, 0));
    f1.sub.flip();
    ASSERT_EQUAL(2, f1.sub.getLastGenerationChanged());
    f1.holder->handle(ConfigUpdate::UP(new ConfigUpdate(ConfigValue(), false, 3)));
    ASSERT_TRUE(f1.sub.nextUpdate(2, 0));
    f1.sub.flip();
    ASSERT_EQUAL(2, f1.sub.getLastGenerationChanged());
}

TEST_MAIN() { TEST_RUN_ALL(); }
