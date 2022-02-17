// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/common/configcontext.h>
#include "config-my.h"
#include "config-foo.h"
#include "config-bar.h"
#include <vespa/config/subscription/configsubscriber.hpp>

#include <vespa/log/log.h>
LOG_SETUP("unittest");

using namespace config;

namespace {
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
}
#if 0
TEST("requireThatUnitTestsCanBeCreated") {
    MyConfigBuilder builder;
    builder.myField = "myval";
    ConfigSet set;
    set.addBuilder("myid", &builder);
    std::unique_ptr<MyConfig> cfg = ConfigGetter<MyConfig>::getConfig("myid", set);
}
#endif

TEST("requireThatConfigCanBeReloaded") {
    ConfigSet set;
    auto ctx = std::make_shared<ConfigContext>(set);
    MyConfigBuilder builder;
    builder.myField = "myfoo";
    set.addBuilder("myid", &builder);
    ConfigSubscriber subscriber(ctx);

    ConfigHandle<MyConfig>::UP handle = subscriber.subscribe<MyConfig>("myid");
    ASSERT_TRUE(subscriber.nextConfigNow());
    std::unique_ptr<MyConfig> cfg(handle->getConfig());
    ASSERT_TRUE(cfg);
    ASSERT_EQUAL("myfoo", cfg->myField);
    ctx->reload();
    ASSERT_FALSE(subscriber.nextConfig(1000ms));
    builder.myField = "foobar";
    ctx->reload();
    ASSERT_TRUE(subscriber.nextConfig(10000ms));
    cfg = handle->getConfig();
    ASSERT_TRUE(cfg);
    ASSERT_EQUAL("foobar", cfg->myField);
}

TEST("requireThatCanSubscribeWithSameIdToDifferentDefs") {
    ConfigSet set;
    auto ctx = std::make_shared<ConfigContext>(set);
    FooConfigBuilder fooBuilder;
    BarConfigBuilder barBuilder;

    fooBuilder.fooValue = "myfoo";
    barBuilder.barValue = "mybar";

    set.addBuilder("fooid", &fooBuilder);
    set.addBuilder("fooid", &barBuilder);

    ConfigSubscriber subscriber(ctx);
    ConfigHandle<FooConfig>::UP h1 = subscriber.subscribe<FooConfig>("fooid");
    ConfigHandle<BarConfig>::UP h2 = subscriber.subscribe<BarConfig>("fooid");

    ASSERT_TRUE(subscriber.nextConfigNow());
    verifyConfig("myfoo", h1->getConfig());
    verifyConfig("mybar", h2->getConfig());
    ctx->reload();
    ASSERT_FALSE(subscriber.nextConfig(100ms));

    fooBuilder.fooValue = "blabla";
    ctx->reload();
    ASSERT_TRUE(subscriber.nextConfig(5000ms));
    verifyConfig("blabla", h1->getConfig());
    verifyConfig("mybar", h2->getConfig());

    barBuilder.barValue = "blabar";
    ctx->reload();
    ASSERT_TRUE(subscriber.nextConfig(5000ms));
    verifyConfig("blabla", h1->getConfig());
    verifyConfig("blabar", h2->getConfig());
}

TEST_MAIN() { TEST_RUN_ALL(); }
