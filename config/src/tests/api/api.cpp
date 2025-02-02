// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/config/common/configcontext.h>
#include <config-my.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace config;

TEST(ConfigApiTest, require_that_can_subscribe_with_empty_config_id)
{
    ConfigSet set;
    auto ctx = std::make_shared<ConfigContext>(set);
    MyConfigBuilder builder;
    builder.myField = "myfoo";
    set.addBuilder("", &builder);
    ConfigSubscriber subscriber(ctx);
    ConfigHandle<MyConfig>::UP handle = subscriber.subscribe<MyConfig>("");
    ASSERT_TRUE(subscriber.nextConfigNow());
    std::unique_ptr<MyConfig> cfg(handle->getConfig());
    ASSERT_TRUE(cfg);
    ASSERT_EQ("myfoo", cfg->myField);
}

GTEST_MAIN_RUN_ALL_TESTS()
