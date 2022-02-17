// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configholder.h>
#include <vespa/config/common/sourcefactory.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include "config-my.h"

using namespace config;

TEST("require that raw spec can create source factory")
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
    ASSERT_EQUAL(1u, value.numLines());
    ASSERT_EQUAL("myField \"foo\"", value.getLine(0));
}

TEST("requireThatRawSubscriptionReturnsCorrectConfig")
{
    RawSpec spec("myField \"foo\"\n");
    ConfigSubscriber s(spec);
    std::unique_ptr<ConfigHandle<MyConfig> > handle = s.subscribe<MyConfig>("myid");
    s.nextConfigNow();
    std::unique_ptr<MyConfig> cfg = handle->getConfig();
    ASSERT_TRUE(cfg);
    ASSERT_EQUAL("foo", cfg->myField);
    ASSERT_EQUAL("my", cfg->defName());
}

TEST_MAIN() { TEST_RUN_ALL(); }
