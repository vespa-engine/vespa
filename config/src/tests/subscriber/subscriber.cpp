// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include "config-foo.h"
#include "config-bar.h"
#include "config-baz.h"
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/misc.h>
#include <vespa/config/common/configholder.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/iconfigmanager.h>
#include <vespa/config/common/iconfigcontext.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <thread>

using namespace config;
using namespace vespalib;

namespace {

    ConfigValue createValue(const std::string & value)
    {
        StringVector lines;
        lines.push_back(value);
        return ConfigValue(std::move(lines));
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
        ASSERT_TRUE(cfg);
        ASSERT_EQUAL(expected, cfg->fooValue);
    }

    void verifyConfig(const std::string & expected, std::unique_ptr<BarConfig> cfg)
    {
        ASSERT_TRUE(cfg);
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
        std::vector<std::shared_ptr<IConfigHolder>> _holders;
        int numCancel;


        MyManager() : idCounter(0), numCancel(0) { }
        ~MyManager() override;

        ConfigSubscription::SP subscribe(const ConfigKey & key, vespalib::duration timeout) override {
            (void) timeout;
            auto holder = std::make_shared<ConfigHolder>();
            _holders.push_back(holder);

            return std::make_shared<ConfigSubscription>(0, key, holder, std::make_unique<MySource>());
        }
        void unsubscribe(const ConfigSubscription & subscription) override {
            (void) subscription;
            numCancel++;
        }

        void updateValue(size_t index, const ConfigValue & value, int64_t generation) {
            ASSERT_TRUE(index < _holders.size());
            _holders[index]->handle(std::make_unique<ConfigUpdate>(value, true, generation));
        }

        void updateGeneration(size_t index, int64_t generation) {
            ASSERT_TRUE(index < _holders.size());
            ConfigValue value;
            // Give previous value just as API.
            if (_holders[index]->poll()) {
                value = _holders[index]->provide()->getValue();
            }
            _holders[index]->handle(std::make_unique<ConfigUpdate>(value, false, generation));
        }

        void reload(int64_t generation) override
        {
            (void) generation;
        }

    };

    MyManager::~MyManager() = default;

    class APIFixture : public IConfigContext
    {
    public:
        MyManager & _m;
        APIFixture(MyManager & m) noexcept
            : _m(m)
        {
        }

        APIFixture(const APIFixture & rhs) noexcept
            : IConfigContext(rhs),
              _m(rhs._m)
        { }

        ~APIFixture() override = default;

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

        StandardFixture(MyManager & F1, APIFixture & F2) : f1(F1), s(std::make_shared<APIFixture>(F2))
        {
            h1 = s.subscribe<FooConfig>("myid");
            h2 = s.subscribe<BarConfig>("myid");
            f1.updateValue(0, createFooValue("foo"), 1);
            f1.updateValue(1, createBarValue("bar"), 1);
            ASSERT_TRUE(s.nextConfigNow());
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
    ASSERT_TRUE(s.nextConfigNow());
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
    ASSERT_TRUE(s.nextConfigNow());
    bool thrown = false;
    try {
        ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid");
    } catch (const ConfigRuntimeException & e) {
        thrown = true;
    }
    ASSERT_TRUE(thrown);
}

TEST_FF("requireThatNextConfigReturnsFalseUntilSubscriptionHasSucceeded", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(std::make_shared<APIFixture>(f2));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid");
    ASSERT_FALSE(s.nextConfigNow());
    ASSERT_FALSE(s.nextConfig(100ms));
    f1.updateValue(0, createFooValue("foo"), 1);
    ASSERT_FALSE(s.nextConfig(100ms));
    f1.updateValue(1, createBarValue("bar"), 1);
    ASSERT_TRUE(s.nextConfig(100ms));
}

TEST_FFF("requireThatNewGenerationIsFetchedOnReload", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());

    ASSERT_FALSE(f3.s.nextConfig(1000ms));

    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());

    f1.updateValue(0, createFooValue("foo2"), 3);
    f1.updateValue(1, createBarValue("bar2"), 3);

    ASSERT_TRUE(f3.s.nextConfig(1000ms));

    verifyConfig("foo2", f3.h1->getConfig());
    verifyConfig("bar2", f3.h2->getConfig());
}

TEST_FFF("requireThatAllConfigsMustGetTimestampUpdate", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    f1.updateValue(0, createFooValue("foo2"), 2);
    ASSERT_FALSE(f3.s.nextConfig(100ms));
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());

    f1.updateValue(0, createFooValue("foo2"), 3);
    f1.updateGeneration(1, 3);

    ASSERT_TRUE(f3.s.nextConfigNow());
    verifyConfig("foo2", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
}

TEST_FFF("requireThatNextConfigMaySucceedIfInTheMiddleOfConfigUpdate", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    f1.updateValue(0, createFooValue("foo2"), 2);
    ASSERT_FALSE(f3.s.nextConfig(1000ms));
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());

    f1.updateGeneration(1, 2);
    ASSERT_TRUE(f3.s.nextConfigNow());
    verifyConfig("foo2", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
}

TEST_FFF("requireThatCorrectConfigIsReturnedAfterTimestampUpdate", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    f1.updateGeneration(0, 2);
    f1.updateGeneration(1, 2);
    ASSERT_FALSE(f3.s.nextConfig(1000ms));
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
    ASSERT_TRUE(f3.s.nextGenerationNow());
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
}

TEST_MT_FFF("requireThatConfigIsReturnedWhenUpdatedDuringNextConfig", 2, MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    if (thread_id == 0) {
        vespalib::Timer timer;
        ASSERT_TRUE(f3.s.nextConfig(10000ms));
        ASSERT_TRUE(timer.elapsed() > 250ms);
        ASSERT_TRUE(timer.elapsed() <= 5s);
        verifyConfig("foo2", f3.h1->getConfig());
        verifyConfig("bar", f3.h2->getConfig());
    } else {
        std::this_thread::sleep_for(300ms);
        f1.updateValue(0, createFooValue("foo2"), 2);
        std::this_thread::sleep_for(300ms);
        f1.updateGeneration(1, 2);
    }
}

TEST_FFF("requireThatConfigIsReturnedWhenUpdatedBeforeNextConfig", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    vespalib::Timer timer;
    ASSERT_FALSE(f3.s.nextConfig(1000ms));
    ASSERT_TRUE(timer.elapsed() > 850ms);
    f1.updateGeneration(0, 2);
    f1.updateGeneration(1, 2);
    timer = vespalib::Timer();
    ASSERT_TRUE(f3.s.nextGeneration(10000ms));
    ASSERT_TRUE(timer.elapsed() <= 5s);
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
    ASSERT_FALSE(f3.s.nextConfig(100ms));
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
        vespalib::Timer timer;
        ASSERT_FALSE(f3.s.nextConfig(5000ms));
        ASSERT_TRUE(timer.elapsed() >= 500ms);
        ASSERT_TRUE(timer.elapsed() < 60s);
    } else {
        std::this_thread::sleep_for(1000ms);
        f3.s.close();
    }
}

TEST_FF("requireThatHandlesAreMarkedAsChanged", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(std::make_shared<APIFixture>(f2));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid2");
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid2");
    EXPECT_FALSE(s.nextConfigNow());

    f1.updateValue(0, createFooValue("foo"), 1);
    f1.updateValue(1, createFooValue("bar"), 1);
    EXPECT_TRUE(s.nextConfig(100ms));
    EXPECT_TRUE(h1->isChanged());
    EXPECT_TRUE(h2->isChanged());

    EXPECT_FALSE(s.nextConfig(100ms));
    EXPECT_FALSE(h1->isChanged());
    EXPECT_FALSE(h2->isChanged());
    f1.updateValue(0, createFooValue("bar"), 2);
    f1.updateGeneration(1, 2);
    EXPECT_TRUE(s.nextConfig(100ms));
    EXPECT_TRUE(h1->isChanged());
    EXPECT_FALSE(h2->isChanged());
}

TEST_FF("requireThatNextGenerationMarksChanged", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(std::make_shared<APIFixture>(f2));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid2");
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid2");
    f1.updateValue(0, createFooValue("foo"), 1);
    f1.updateValue(1, createFooValue("bar"), 1);
    ASSERT_TRUE(s.nextGenerationNow());
    ASSERT_TRUE(h1->isChanged());
    ASSERT_TRUE(h2->isChanged());

    f1.updateValue(0, createFooValue("bar"), 2);
    f1.updateGeneration(1, 2);
    ASSERT_TRUE(s.nextGenerationNow());
    ASSERT_TRUE(h1->isChanged());
    ASSERT_FALSE(h2->isChanged());

    f1.updateGeneration(0, 3);
    f1.updateGeneration(1, 3);
    ASSERT_TRUE(s.nextGenerationNow());
    ASSERT_FALSE(h1->isChanged());
    ASSERT_FALSE(h2->isChanged());
}

TEST_FF("requireThatgetGenerationIsSet", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(std::make_shared<APIFixture>(f2));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid2");
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid2");
    f1.updateValue(0, createFooValue("foo"), 1);
    f1.updateValue(1, createFooValue("bar"), 1);
    ASSERT_TRUE(s.nextGenerationNow());
    ASSERT_EQUAL(1, s.getGeneration());
    ASSERT_TRUE(h1->isChanged());
    ASSERT_TRUE(h2->isChanged());
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(1, 2);
    ASSERT_FALSE(s.nextGenerationNow());
    ASSERT_EQUAL(1, s.getGeneration());
    f1.updateGeneration(0, 2);
    ASSERT_TRUE(s.nextGenerationNow());
    ASSERT_EQUAL(2, s.getGeneration());
}

TEST_FFF("requireThatConfigHandleStillHasConfigOnTimestampUpdate", MyManager, APIFixture(f1), StandardFixture(f1, f2)) {
    f1.updateGeneration(0, 2);
    f1.updateGeneration(1, 2);
    ASSERT_TRUE(f3.s.nextGenerationNow());
    verifyConfig("foo", f3.h1->getConfig());
    verifyConfig("bar", f3.h2->getConfig());
}

TEST_FF("requireThatTimeStamp0Works", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(std::make_shared<APIFixture>(f2));
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid");
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    ConfigHandle<BazConfig>::UP h3 = s.subscribe<BazConfig>("myid");
    f1.updateValue(0, createBarValue("bar"), 0);
    f1.updateValue(1, createFooValue("foo"), 0);
    f1.updateValue(2, createBazValue("baz"), 0);
    ASSERT_TRUE(s.nextConfigNow());
    verifyConfig("bar", h2->getConfig());
    verifyConfig("foo", h1->getConfig());
    verifyConfig("baz", h3->getConfig());
}

TEST_FF("requireThatNextGenerationWorksWithManyConfigs", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(std::make_shared<APIFixture>(f2));
    ConfigHandle<BarConfig>::UP h2 = s.subscribe<BarConfig>("myid");
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    ConfigHandle<BazConfig>::UP h3 = s.subscribe<BazConfig>("myid");
    f1.updateValue(0, createBarValue("bar"), 1);
    f1.updateValue(1, createFooValue("foo"), 1);
    f1.updateValue(2, createBazValue("baz"), 1);
    ASSERT_TRUE(s.nextGeneration(100ms));
    verifyConfig("bar", h2->getConfig());
    verifyConfig("foo", h1->getConfig());
    verifyConfig("baz", h3->getConfig());
    int generation = 2;

    f1.updateGeneration(0, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(1, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(2, generation);
    ASSERT_TRUE(s.nextGeneration(100ms));

    generation++;
    f1.updateGeneration(0, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(2, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(1, generation);
    ASSERT_TRUE(s.nextGeneration(100ms));

    generation++;
    f1.updateGeneration(1, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(0, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(2, generation);
    ASSERT_TRUE(s.nextGeneration(100ms));

    generation++;
    f1.updateGeneration(1, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(2, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(0, generation);
    ASSERT_TRUE(s.nextGeneration(100ms));

    generation++;
    f1.updateGeneration(2, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(0, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(1, generation);
    ASSERT_TRUE(s.nextGeneration(100ms));

    generation++;
    f1.updateGeneration(2, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(1, generation);
    ASSERT_FALSE(s.nextGenerationNow());
    f1.updateGeneration(0, generation);
    ASSERT_TRUE(s.nextGeneration(100ms));
}

TEST_FF("requireThatConfigSubscriberHandlesProxyCache", MyManager, APIFixture(f1)) {
    ConfigSubscriber s(std::make_shared<APIFixture>(f2));
    ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
    f1.updateValue(0, createFooValue("foo"), 1);
    f1.updateGeneration(0, 2);
    ASSERT_TRUE(s.nextConfigNow());
    ASSERT_EQUAL(2, s.getGeneration());
    ASSERT_TRUE(h1->isChanged());
    verifyConfig("foo", h1->getConfig());

    f1.updateGeneration(0, 3);
    ASSERT_TRUE(s.nextGenerationNow());
    ASSERT_EQUAL(3, s.getGeneration());
    ASSERT_FALSE(h1->isChanged());
    verifyConfig("foo", h1->getConfig());
}

TEST_MT_FF("requireThatConfigSubscriberWaitsUntilNextConfigSucceeds", 2, MyManager, APIFixture(f1)) {
    if (thread_id == 0) {
        ConfigSubscriber s(std::make_shared<APIFixture>(f2));
        ConfigHandle<FooConfig>::UP h1 = s.subscribe<FooConfig>("myid");
        f1.updateValue(0, createFooValue("foo"), 1);
        ASSERT_TRUE(s.nextConfigNow());
        f1.updateGeneration(0, 2);
        ASSERT_FALSE(s.nextConfig(1000ms));
        TEST_BARRIER();
        ASSERT_TRUE(s.nextConfig(2000ms));
        verifyConfig("foo2", h1->getConfig()); // First update is skipped
    } else {
        TEST_BARRIER();
        std::this_thread::sleep_for(1000ms);
        f1.updateValue(0, createFooValue("foo2"), 3);
    }
}

TEST_MAIN() {
    TEST_RUN_ALL();
}
