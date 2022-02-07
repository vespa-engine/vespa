// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

/*
 * TODO: Convert to frt test.
TEST_MT_FFF("require that source may be unable to serve config temporarily", 2, ConfigContext::SP(new ConfigContext()),
                                                                                 ConfigSet(),
                                                                                 MyConfigBuilder()) {
    if (thread_id == 0) {
        ConfigSubscriber subscriber(f1, f2);
        ConfigHandle<MyConfig>::UP handle = subscriber.subscribe<MyConfig>("myid", 10000);
        ASSERT_TRUE(subscriber.nextConfig(10000));
        std::unique_ptr<MyConfig> cfg(handle->getConfig());
        ASSERT_TRUE(cfg.get() != NULL);
        ASSERT_EQUAL("myfoo", cfg->myField);
    } else {
        std::this_thread::sleep_for(1s);
        f3.myField = "myfoo";
        f2.addBuilder("myid", &f3);
        f1->reload();

    }
}
*/

TEST_MAIN() { TEST_RUN_ALL(); }
