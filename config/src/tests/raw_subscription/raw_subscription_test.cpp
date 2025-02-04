// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-my.h"
#include <vespa/config/common/configholder.h>
#include <vespa/config/common/sourcefactory.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace config;

TEST(RawSubscriptionTest, require_that_raw_spec_can_create_source_factory)
{
    RawSpec spec("myField \"foo\"\n");
    auto raw = spec.createSourceFactory(TimingValues());
    ASSERT_TRUE(raw);
    std::shared_ptr<IConfigHolder> holder(new ConfigHolder());
    std::unique_ptr<Source> src = raw->createSource(holder, ConfigKey("myid", "my", "bar", "foo"));
    ASSERT_TRUE(src);

    src->getConfig();
    ASSERT_TRUE(holder->poll());
    std::unique_ptr<ConfigUpdate> update(holder->provide());
    ASSERT_TRUE(update);
    const ConfigValue & value(update->getValue());
    ASSERT_EQ(1u, value.numLines());
    ASSERT_EQ("myField \"foo\"", value.getLine(0));
}

TEST(RawSubscriptionTest, requireThatRawSubscriptionReturnsCorrectConfig)
{
    RawSpec spec("myField \"foo\"\n");
    ConfigSubscriber s(spec);
    std::unique_ptr<ConfigHandle<MyConfig> > handle = s.subscribe<MyConfig>("myid");
    s.nextConfigNow();
    std::unique_ptr<MyConfig> cfg = handle->getConfig();
    ASSERT_TRUE(cfg);
    ASSERT_EQ("foo", cfg->myField);
    ASSERT_EQ("my", cfg->defName());
}

GTEST_MAIN_RUN_ALL_TESTS()
