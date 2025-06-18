// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-my.h"
#include "config-foo.h"
#include "config-bar.h"
#include <vespa/config/helper/legacysubscriber.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <fstream>

using namespace config;

template <typename ConfigType>
class MyCallback : public IFetcherCallback<ConfigType>
{
public:
    MyCallback() : _config(), _configured(false) { }
    void configure(std::unique_ptr<ConfigType> config) override
    {
        _configured = true;
        _config = std::move(config);
    }
    std::unique_ptr<ConfigType> _config;
    bool _configured;
};

struct ConfigIdGenerator
{
    static std::string id(const std::string &type, const std::string &name)
    {
        return std::string(type + ":" + TEST_PATH(name));
    }
};

TEST(LegacySubscriberTest, requireThatFileLegacyWorks)
{
    LegacySubscriber s;
    MyCallback<MyConfig> cb;
    s.subscribe<MyConfig>(ConfigIdGenerator::id("file", "test1.cfg"), &cb);
    ASSERT_TRUE(cb._configured);
    ASSERT_TRUE(cb._config.get() != NULL);
    ASSERT_EQ("bar", cb._config->myField);
}

TEST(LegacySubscriberTest, requireThatDirLegacyWorks)
{
    LegacySubscriber s;
    MyCallback<MyConfig> cb;
    s.subscribe<MyConfig>(ConfigIdGenerator::id("dir","testdir"), &cb);
    ASSERT_TRUE(cb._configured);
    ASSERT_TRUE(cb._config.get() != NULL);
    ASSERT_EQ("bar", cb._config->myField);
}


TEST(LegacySubscriberTest, requireThatDirMultiFileLegacyWorks)
{
    MyCallback<FooConfig> cb1;
    MyCallback<BarConfig> cb2;

    LegacySubscriber s1, s2;
    s1.subscribe<FooConfig>(ConfigIdGenerator::id("dir", "testdir/foobar"), &cb1);
    s2.subscribe<BarConfig>(ConfigIdGenerator::id("dir", "testdir/foobar"), &cb2);

    ASSERT_TRUE(cb1._configured);
    ASSERT_TRUE(cb1._config.get() != NULL);
    ASSERT_EQ("bar", cb1._config->fooValue);

    ASSERT_TRUE(cb2._configured);
    ASSERT_TRUE(cb2._config.get() != NULL);
    ASSERT_EQ("foo", cb2._config->barValue);
}

TEST(LegacySubscriberTest, requireThatFileLegacyWorksMultipleTimes)
{
    LegacySubscriber s;
    MyCallback<MyConfig> cb;
    s.subscribe<MyConfig>(ConfigIdGenerator::id("file", "test1.cfg"), &cb);
    ASSERT_TRUE(cb._configured);
    ASSERT_TRUE(cb._config.get() != NULL);
    ASSERT_EQ("bar", cb._config->myField);
    cb._configured = false;
    LegacySubscriber s2;
    s2.subscribe<MyConfig>(ConfigIdGenerator::id("file", "test1.cfg"), &cb);
    ASSERT_TRUE(cb._configured);
    ASSERT_TRUE(cb._config.get() != NULL);
    ASSERT_EQ("bar", cb._config->myField);
}

TEST(LegacySubscriberTest, requireThatRawLegacyWorks)
{
    LegacySubscriber s;
    MyCallback<MyConfig> cb;
    s.subscribe<MyConfig>("raw:myField \"bar\"\n", &cb);
    ASSERT_TRUE(cb._configured);
    ASSERT_TRUE(cb._config.get() != NULL);
    ASSERT_EQ("bar", cb._config->myField);
}

GTEST_MAIN_RUN_ALL_TESTS()
