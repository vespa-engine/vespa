// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config/helper/configfetcher.hpp>
#include <vespa/config/common/configcontext.h>
#include <vespa/vespalib/util/exception.h>
#include <vespa/vespalib/gtest/gtest.h>
#include "config-my.h"
#include <atomic>
#include <thread>

using namespace config;


class MyCallback : public IFetcherCallback<MyConfig>
{
public:
    MyCallback(const std::string & badConfig="");
    ~MyCallback() override;
    void configure(std::unique_ptr<MyConfig> config) override
    {
        _config = std::move(config);
        _configured = true;
        if (_config->myField.compare(_badConfig) == 0) {
            throw vespalib::Exception("Buhu");
        }
    }
    std::unique_ptr<MyConfig> _config;
    std::atomic<bool> _configured;
    std::string _badConfig;
};

MyCallback::MyCallback(const std::string & badConfig) : _config(), _configured(false), _badConfig(badConfig) { }
MyCallback::~MyCallback() = default;

TEST(ConfigFetcherTest, requireThatConfigIsAvailableOnConstruction)
{
    RawSpec spec("myField \"foo\"\n");
    MyCallback cb;

    {
        ConfigFetcher fetcher(spec);
        fetcher.subscribe<MyConfig>("myid", &cb);
        fetcher.start();
        ASSERT_TRUE(cb._config);
        ASSERT_EQ("my", cb._config->defName());
        ASSERT_EQ("foo", cb._config->myField);
    }
}

#if 0
TEST(ConfigFetcherTest, requireThatConfigUpdatesArePerformed)
{
    writeFile("test1.cfg", "foo");
    FileSpec spec("test1.cfg");
    MyCallback cb;
    cb._configured = false;
    vespalib::ThreadStackExecutor executor(1, 128_Ki);

    {
        ConfigFetcher fetcher(500);
        fetcher.subscribe<MyConfig>("test1", &cb, spec);
        fetcher.start();
        ASSERT_TRUE(cb._configured);
        ASSERT_TRUE(cb._config.get() != nullptr);
        ASSERT_EQ("my", cb._config->defName());
        ASSERT_EQ("foo", cb._config->myField);

        sleep(2);
        writeFile("test1.cfg", "bar");

        cb._configured = false;
        vespalib::Timer timer;
        while (!cb._configured && timer.elapsed() < 20s) {
            if (cb._configured)
                break;
            std::this_thread::sleep_for(1s);
        }
        ASSERT_TRUE(cb._configured);
        ASSERT_TRUE(cb._config);
        ASSERT_EQ("my", cb._config->defName());
        ASSERT_EQ("bar", cb._config->myField);
    }
}
#endif

TEST(ConfigFetcherTest, requireThatFetcherCanHandleMultipleConfigs)
{
    MyConfigBuilder b1, b2;
    b1.myField = "foo";
    b2.myField = "bar";
    ConfigSet set;
    set.addBuilder("test1", &b1);
    set.addBuilder("test2", &b2);
    MyCallback cb1;
    MyCallback cb2;

    {
        ConfigFetcher fetcher(set);
        fetcher.subscribe<MyConfig>("test1", &cb1);
        fetcher.subscribe<MyConfig>("test2", &cb2);
        fetcher.start();

        ASSERT_TRUE(cb1._configured);
        ASSERT_TRUE(cb2._configured);
        ASSERT_TRUE(cb1._config);
        ASSERT_TRUE(cb2._config);
        ASSERT_EQ("my", cb1._config->defName());
        ASSERT_EQ("foo", cb1._config->myField);
        ASSERT_EQ("my", cb2._config->defName());
        ASSERT_EQ("bar", cb2._config->myField);
    }
}

TEST(ConfigFetcherTest, verify_that_exceptions_in_callback_is_thrown_on_initial_subscribe)
{
    MyConfigBuilder b1;
    b1.myField = "foo";
    ConfigSet set;
    set.addBuilder("test1", &b1);
    MyCallback cb("foo");
    {
        ConfigFetcher fetcher(set);
        fetcher.subscribe<MyConfig>("test1", &cb);
        VESPA_EXPECT_EXCEPTION(fetcher.start(), vespalib::Exception, "Buhu");
    }
}

namespace {

struct ConfigFixture {
    MyConfigBuilder builder;
    ConfigSet set;
    std::shared_ptr<ConfigContext> context;
    ConfigFixture() : builder(), set(), context() {
        set.addBuilder("cfgid", &builder);
        context = std::make_shared<ConfigContext>(set);
    }
};

} // namespace <unnamed>

TEST(ConfigFetcherTest, verify_that_config_generation_can_be_obtained_from_config_fetcher)
{
    ConfigFixture f1;
    f1.builder.myField = "foo";
    MyCallback cb;
    {
        ConfigFetcher fetcher(f1.context);
        fetcher.subscribe<MyConfig>("cfgid", &cb);
        fetcher.start();
        EXPECT_EQ("foo", cb._config.get()->myField);
        EXPECT_EQ(1, fetcher.getGeneration());
        f1.builder.myField = "bar";
        cb._configured = false;
        f1.context->reload();
        vespalib::Timer timer;
        while (timer.elapsed() < 120s) {
            if (cb._configured) {
                break;
            }
            std::this_thread::sleep_for(10ms);;
        }
        EXPECT_EQ(2, fetcher.getGeneration());
        EXPECT_EQ("bar", cb._config.get()->myField);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
