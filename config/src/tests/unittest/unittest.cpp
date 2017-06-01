// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("unittest");
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/config.h>
#include "config-my.h"
#include "config-foo.h"
#include "config-bar.h"

using namespace config;

namespace {
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
    ConfigContext::SP ctx(new ConfigContext(set));
    MyConfigBuilder builder;
    builder.myField = "myfoo";
    set.addBuilder("myid", &builder);
    ConfigSubscriber subscriber(ctx);

    ConfigHandle<MyConfig>::UP handle = subscriber.subscribe<MyConfig>("myid");
    ASSERT_TRUE(subscriber.nextConfig(0));
    std::unique_ptr<MyConfig> cfg(handle->getConfig());
    ASSERT_TRUE(cfg.get() != NULL);
    ASSERT_EQUAL("myfoo", cfg->myField);
    ctx->reload();
    ASSERT_FALSE(subscriber.nextConfig(1000));
    builder.myField = "foobar";
    ctx->reload();
    ASSERT_TRUE(subscriber.nextConfig(10000));
    cfg = handle->getConfig();
    ASSERT_TRUE(cfg.get() != NULL);
    ASSERT_EQUAL("foobar", cfg->myField);
}

TEST("requireThatCanSubscribeWithSameIdToDifferentDefs") {
    ConfigSet set;
    ConfigContext::SP ctx(new ConfigContext(set));
    FooConfigBuilder fooBuilder;
    BarConfigBuilder barBuilder;

    fooBuilder.fooValue = "myfoo";
    barBuilder.barValue = "mybar";

    set.addBuilder("fooid", &fooBuilder);
    set.addBuilder("fooid", &barBuilder);

    ConfigSubscriber subscriber(ctx);
    ConfigHandle<FooConfig>::UP h1 = subscriber.subscribe<FooConfig>("fooid");
    ConfigHandle<BarConfig>::UP h2 = subscriber.subscribe<BarConfig>("fooid");

    ASSERT_TRUE(subscriber.nextConfig(0));
    verifyConfig("myfoo", h1->getConfig());
    verifyConfig("mybar", h2->getConfig());
    ctx->reload();
    ASSERT_FALSE(subscriber.nextConfig(100));

    fooBuilder.fooValue = "blabla";
    ctx->reload();
    ASSERT_TRUE(subscriber.nextConfig(5000));
    verifyConfig("blabla", h1->getConfig());
    verifyConfig("mybar", h2->getConfig());

    barBuilder.barValue = "blabar";
    ctx->reload();
    ASSERT_TRUE(subscriber.nextConfig(5000));
    verifyConfig("blabla", h1->getConfig());
    verifyConfig("blabar", h2->getConfig());
}

TEST_MAIN() { TEST_RUN_ALL(); }
