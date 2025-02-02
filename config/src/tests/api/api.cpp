// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configcontext.h>
#include <config-my.h>
#include <vespa/config/subscription/configsubscriber.hpp>

using namespace config;

TEST("require that can subscribe with empty config id") {
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
    ASSERT_EQUAL("myfoo", cfg->myField);
}

TEST_MAIN() { TEST_RUN_ALL(); }
