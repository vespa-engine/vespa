// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/config.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/common/configholder.h>
#include <vespa/config/subscription/configsubscription.h>
#include <fstream>
#include "config-foo.h"
#include "config-bar.h"
#include "config-baz.h"

using namespace config;
using namespace vespalib;

namespace {

    ConfigValue createValue(const std::string & value)
    {
        std::vector< vespalib::string > lines;
        lines.push_back(value);
        return ConfigValue(lines, calculateContentMd5(lines));
    }

    ConfigValue createFooValue(const std::string & value)
    {
        return createValue("fooValue \"" + value + "\"");
    }

    ConfigValue createBarValue(const std::string & value)
    {
        return createValue("barValue \"" + value + "\"");
    }

    ConfigValue createBazValue(const std::string & value)
    {
        return createValue("bazValue \"" + value + "\"");
    }

    void verifyConfig(const std::string & expected, std::unique_ptr<FooConfig> cfg)
    {
        ASSERT_TRUE(cfg.get() != NULL);
        ASSERT_EQUAL(expected, cfg->fooValue);
    }

    void verifyConfig(const std::string & expected, std::unique_ptr<BarConfig> cfg)
    {
        ASSERT_TRUE(cfg.get() != NULL);
        ASSERT_EQUAL(expected, cfg->barValue);
    }

    void verifyConfig(const std::string & expected, std::unique_ptr<BazConfig> cfg)
    {
        ASSERT_TRUE(cfg.get() != NULL);
        ASSERT_EQUAL(expected, cfg->bazValue);
    }

    class MySource : public Source
    {
        void getConfig() override { }
        void close() override { }
        void reload(int64_t gen) override { (void) gen; }
    };

    class MyManager : public IConfigManager
    {
    public:

        void unsubscribeAll()  { }
        size_t numSubscribers() const { return 0; }


        SubscriptionId idCounter;
        std::vector<IConfigHolder::SP> _holders;
        int numCancel;


        MyManager() : idCounter(0), numCancel(0) { }

        ConfigSubscription::SP subscribe(const ConfigKey & key, uint64_t timeoutInMillis) override {
            (void) timeoutInMillis;
            IConfigHolder::SP holder(new ConfigHolder());
            _holders.push_back(holder);

            ConfigSubscription::SP s(new ConfigSubscription(0, key, holder, Source::UP(new MySource())));
            return s;
        }
        void unsubscribe(const ConfigSubscription::SP & subscription) override {
            (void) subscription;
            numCancel++;
        }

        void updateValue(size_t index, const ConfigValue & value, int64_t generation) {
            ASSERT_TRUE(index < _holders.size());
            _holders[index]->handle(ConfigUpdate::UP(new ConfigUpdate(value, true, generation)));
        }

        void updateGeneration(size_t index, int64_t generation) {
            ASSERT_TRUE(index < _holders.size());
            ConfigValue value;
            // Give previous value just as API.
            if (_holders[index]->poll()) {
                value = _holders[index]->provide()->getValue();
            }
            _holders[index]->handle(ConfigUpdate::UP(new ConfigUpdate(value, false, generation)));
        }

        void reload(int64_t generation) override
        {
            (void) generation;
        }

    };

    class APIFixture : public IConfigContext
    {
    public:
        MyManager & _m;
        APIFixture(MyManager & m)
            : _m(m)
        {
        }

        APIFixture(const APIFixture & rhs)
            : IConfigContext(rhs),
              _m(rhs._m)
        { }

        IConfigManager & getManagerInstance() override {
            return _m;
        }

        IConfigManager & getManagerInstance(const SourceSpec & spec) {
            (void) spec;
            return getManagerInstance();
        }

        void reload() override { }
    };

    struct StandardFixture {
        MyManager & f1;
        ConfigSubscriber s;
        ConfigHandle<FooConfig>::UP h1;
        ConfigHandle<BarConfig>::UP h2;

        StandardFixture(MyManager & F1, APIFixture & F2) : f1(F1), s(IConfigContext::SP(new APIFixture(F2)))
        {
            h1 = s.subscribe<FooConfig>("myid");
            h2 = s.subscribe<BarConfig>("myid");
            f1.updateValue(0, createFooValue("foo"), 1);
            f1.updateValue(1, createBarValue("bar"), 1);
            ASSERT_TRUE(s.nextConfig(0));
            verifyConfig("foo", h1->getConfig());
            verifyConfig("bar", h2->getConfig());
        }
    };

    struct SimpleFixture {
        ConfigSet set;
        FooConfigBuilder fooBuilder;
        BarConfigBuilder barBuilder;
        SimpleFixture() {
            fooBuilder.fooValue = "bar";
            barBuilder.barValue = "foo";
            set.addBuilder("myid", &fooBuilder);
            set.addBuilder("myid", &barBuilder);
        }
    };
}

TEST_F("requireThatSubscriberCanGetMultipleTypes", SimpleFixture()) {
    ConfigSubscriber s(f.set);
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid");
    ASSERT_TRUE(s.nextConfig(0));
    std::unique_ptr<FooConfig> foo = h1->getConfig();
    std::unique_ptr<BarConfig> bar = h2->getConfig();
    ASSERT_EQUAL("bar", foo->fooValue);
    ASSERT_EQUAL("foo", bar->barValue);
}

TEST_F("requireThatNextConfigMustBeCalled", SimpleFixture()) {
    ConfigSubscriber s(f.set);
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    bool thrown = false;
    try {
        std::unique_ptr<FooConfig> foo = h1->getConfig();
    } catch (const ConfigRuntimeException & e) {
        thrown = true;
    }
    ASSERT_TRUE(thrown);
}

TEST_F("requireThatSubscriptionsCannotBeAddedWhenFrozen", SimpleFixture()) {
    ConfigSubscriber s(f.set);
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    ASSERT_TRUE(s.nextConfig(0));
    bool thrown = false;
    try {
        ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid");
    } catch (const ConfigRuntimeException & e) {
        thrown = true;
    }
    ASSERT_TRUE(thrown);
}

TEST_FF("requireThatNextConfigReturnsFalseUntilSubscriptionHasSucceeded", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(IConfigContext::SP(new APIFixture(f2)));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid");
    ASSERT_FALSE(s.nextConfig(0));
    ASSERT_FALSE(s.nextConfig(100));
    f1.updateValue(0, createFooValue("foo"), 1);
    ASSERT_FALSE(s.nextConfig(100));
    f1.updateValue(1, createBarValue("bar"), 1);
    ASSERT_TRUE(s.nextConfig(100));
}

TEST_FFF("requireThatNewGenerationIsFetchedOnReload", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());

    ASSERT_FALSE(f3.s.nextConfig(1000));

    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());

    f1.updateValue(0, createFooValue("foo2"), 3);
    f1.updateValue(1, createBarValue("bar2"), 3);

    ASSERT_TRUE(f3.s.nextConfig(1000));

    verifyConfig("foo2", f3.h1->getConfig());
    verifyConfig("bar2", f3.h2->getConfig());
}

TEST_FFF("requireThatAllConfigsMustGetTimestampUpdate", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    f1.updateValue(0, createFooValue("foo2"), 2);
    ASSERT_FALSE(f3.s.nextConfig(100));
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());

    f1.updateValue(0, createFooValue("foo2"), 3);
    f1.updateGeneration(1, 3);

    ASSERT_TRUE(f3.s.nextConfig(0));
    verifyConfig("foo2", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
}

TEST_FFF("requireThatNextConfigMaySucceedIfInTheMiddleOfConfigUpdate", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    f1.updateValue(0, createFooValue("foo2"), 2);
    ASSERT_FALSE(f3.s.nextConfig(1000));
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());

    f1.updateGeneration(1, 2);
    ASSERT_TRUE(f3.s.nextConfig(0));
    verifyConfig("foo2", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
}

TEST_FFF("requireThatCorrectConfigIsReturnedAfterTimestampUpdate", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    f1.updateGeneration(0, 2);
    f1.updateGeneration(1, 2);
    ASSERT_FALSE(f3.s.nextConfig(1000));
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
    ASSERT_TRUE(f3.s.nextGeneration(0));
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
}

TEST_MT_FFF("requireThatConfigIsReturnedWhenUpdatedDuringNextConfig", 2, MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    if (thread_id == 0) {
        FastOS_Time timer;
        timer.SetNow();
        ASSERT_TRUE(f3.s.nextConfig(10000));
        ASSERT_TRUE(timer.MilliSecsToNow() > 250);
        ASSERT_TRUE(timer.MilliSecsToNow() <= 5000);
        verifyConfig("foo2", f3.h1->getConfig());
        verifyConfig("bar", f3.h2->getConfig());
    } else {
        FastOS_Thread::Sleep(300);
        f1.updateValue(0, createFooValue("foo2"), 2);
        FastOS_Thread::Sleep(300);
        f1.updateGeneration(1, 2);
    }
}

TEST_FFF("requireThatConfigIsReturnedWhenUpdatedBeforeNextConfig", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    FastOS_Time timer;
    timer.SetNow();
    ASSERT_FALSE(f3.s.nextConfig(1000));
    ASSERT_TRUE(timer.MilliSecsToNow() > 850);
    f1.updateGeneration(0, 2);
    f1.updateGeneration(1, 2);
    timer.SetNow();
    ASSERT_TRUE(f3.s.nextGeneration(10000));
    ASSERT_TRUE(timer.MilliSecsToNow() <= 5000);
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
}

TEST_FFF("requireThatSubscriptionsAreUnsubscribedOnClose", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    ASSERT_FALSE(f3.s.isClosed());
    f3.s.close();
    ASSERT_TRUE(f3.s.isClosed());
    ASSERT_EQUAL(2, f1.numCancel);
}

TEST_FFF("requireThatNothingCanBeCalledAfterClose", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    ASSERT_FALSE(f3.s.isClosed());
    f3.s.close();
    ASSERT_TRUE(f3.s.isClosed());
    ASSERT_FALSE(f3.s.nextConfig(100));
    bool thrown = false;
    try {
        f3.h1->getConfig();
    } catch (const ConfigRuntimeException & e) {
        thrown = true;
    }
    ASSERT_TRUE(thrown);
}

TEST_MT_FFF("requireThatNextConfigIsInterruptedOnClose", 2, MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    if (thread_id == 0) {
        FastOS_Time timer;
        timer.SetNow();
        ASSERT_FALSE(f3.s.nextConfig(5000));
        ASSERT_TRUE(timer.MilliSecsToNow() >= 500.0);
        ASSERT_TRUE(timer.MilliSecsToNow() < 60000.0);
    } else {
        FastOS_Thread::Sleep(1000);
        f3.s.close();
    }
}

TEST_FF("requireThatHandlesAreMarkedAsChanged", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(IConfigContext::SP(new APIFixture(f2)));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid2");
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid2");
    ASSERT_FALSE(s.nextConfig(0));

    f1.updateValue(0, createFooValue("foo"), 1);
    f1.updateValue(1, createFooValue("bar"), 1);
    ASSERT_TRUE(s.nextConfig(100));
    ASSERT_TRUE(h1->isChanged());
    ASSERT_TRUE(h2->isChanged());

    ASSERT_FALSE(s.nextConfig(100));
    ASSERT_FALSE(h1->isChanged());
    ASSERT_FALSE(h2->isChanged());
    f1.updateValue(0, createFooValue("bar"), 2);
    f1.updateGeneration(1, 2);
    ASSERT_TRUE(s.nextConfig(100));
    ASSERT_TRUE(h1->isChanged());
    ASSERT_FALSE(h2->isChanged());
}

TEST_FF("requireThatNextGenerationMarksChanged", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(IConfigContext::SP(new APIFixture(f2)));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid2");
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid2");
    f1.updateValue(0, createFooValue("foo"), 1);
    f1.updateValue(1, createFooValue("bar"), 1);
    ASSERT_TRUE(s.nextGeneration(0));
    ASSERT_TRUE(h1->isChanged());
    ASSERT_TRUE(h2->isChanged());

    f1.updateValue(0, createFooValue("bar"), 2);
    f1.updateGeneration(1, 2);
    ASSERT_TRUE(s.nextGeneration(0));
    ASSERT_TRUE(h1->isChanged());
    ASSERT_FALSE(h2->isChanged());

    f1.updateGeneration(0, 3);
    f1.updateGeneration(1, 3);
    ASSERT_TRUE(s.nextGeneration(0));
    ASSERT_FALSE(h1->isChanged());
    ASSERT_FALSE(h2->isChanged());
}

TEST_FF("requireThatgetGenerationIsSet", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(IConfigContext::SP(new APIFixture(f2)));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid2");
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid2");
    f1.updateValue(0, createFooValue("foo"), 1);
    f1.updateValue(1, createFooValue("bar"), 1);
    ASSERT_TRUE(s.nextGeneration(0));
    ASSERT_EQUAL(1, s.getGeneration());
    ASSERT_TRUE(h1->isChanged());
    ASSERT_TRUE(h2->isChanged());
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(1, 2);
    ASSERT_FALSE(s.nextGeneration(0));
    ASSERT_EQUAL(1, s.getGeneration());
    f1.updateGeneration(0, 2);
    ASSERT_TRUE(s.nextGeneration(0));
    ASSERT_EQUAL(2, s.getGeneration());
}

TEST_FFF("requireThatConfigHandleStillHasConfigOnTimestampUpdate", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    f1.updateGeneration(0, 2);
    f1.updateGeneration(1, 2);
    ASSERT_TRUE(f3.s.nextGeneration(0));
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
}

TEST_FF("requireThatTimeStamp0Works", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(IConfigContext::SP(new APIFixture(f2)));
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid");
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    ConfigHandle<BazConfig>::UP h3 = s.subscribe<BazConfig>("myid");
    f1.updateValue(0, createBarValue("bar"), 0);
    f1.updateValue(1, createFooValue("foo"), 0);
    f1.updateValue(2, createBazValue("baz"), 0);
    ASSERT_TRUE(s.nextConfig(0));
    verifyConfig("bar", h2->getConfig());
    verifyConfig("foo", h1->getConfig());
    verifyConfig("baz", h3->getConfig());
}

TEST_FF("requireThatNextGenerationWorksWithManyConfigs", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(IConfigContext::SP(new APIFixture(f2)));
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid");
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    ConfigHandle<BazConfig>::UP h3 = s.subscribe<BazConfig>("myid");
    f1.updateValue(0, createBarValue("bar"), 1);
    f1.updateValue(1, createFooValue("foo"), 1);
    f1.updateValue(2, createBazValue("baz"), 1);
    ASSERT_TRUE(s.nextGeneration(100));
    verifyConfig("bar", h2->getConfig());
    verifyConfig("foo", h1->getConfig());
    verifyConfig("baz", h3->getConfig());
    int generation = 2;

    f1.updateGeneration(0, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(1, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(2, generation);
    ASSERT_TRUE(s.nextGeneration(100));

    generation++;
    f1.updateGeneration(0, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(2, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(1, generation);
    ASSERT_TRUE(s.nextGeneration(100));

    generation++;
    f1.updateGeneration(1, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(0, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(2, generation);
    ASSERT_TRUE(s.nextGeneration(100));

    generation++;
    f1.updateGeneration(1, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(2, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(0, generation);
    ASSERT_TRUE(s.nextGeneration(100));

    generation++;
    f1.updateGeneration(2, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(0, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(1, generation);
    ASSERT_TRUE(s.nextGeneration(100));

    generation++;
    f1.updateGeneration(2, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(1, generation);
    ASSERT_FALSE(s.nextGeneration(0));
    f1.updateGeneration(0, generation);
    ASSERT_TRUE(s.nextGeneration(100));
}

TEST_FF("requireThatConfigSubscriberHandlesProxyCache", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(IConfigContext::SP(new APIFixture(f2)));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    f1.updateValue(0, createFooValue("foo"), 1);
    f1.updateGeneration(0, 2);
    ASSERT_TRUE(s.nextConfig(0));
    ASSERT_EQUAL(2, s.getGeneration());
    ASSERT_TRUE(h1->isChanged());
    verifyConfig("foo", h1->getConfig());

    f1.updateGeneration(0, 3);
    ASSERT_TRUE(s.nextGeneration(0));
    ASSERT_EQUAL(3, s.getGeneration());
    ASSERT_FALSE(h1->isChanged());
    verifyConfig("foo", h1->getConfig());
}

TEST_MT_FF("requireThatConfigSubscriberWaitsUntilNextConfigSucceeds", 2, MyManager, APIFixture(f1)) {
    if (thread_id == 0) {
        ConfigSubscriber s(IConfigContext::SP(new APIFixture(f2)));
        ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
        f1.updateValue(0, createFooValue("foo"), 1);
        ASSERT_TRUE(s.nextConfig(0));
        f1.updateGeneration(0, 2);
        ASSERT_FALSE(s.nextConfig(1000));
        TEST_BARRIER();
        ASSERT_TRUE(s.nextConfig(2000));
        verifyConfig("foo2", h1->getConfig()); // First update is skipped
    } else {
        TEST_BARRIER();
        FastOS_Thread::Sleep(1000);
        f1.updateValue(0, createFooValue("foo2"), 3);
    }
}

TEST_MAIN() {
    TEST_RUN_ALL();
}
