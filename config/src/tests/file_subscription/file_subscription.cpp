// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/subscription/configsubscriber.hpp>
#include <vespa/config/common/configholder.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/config/common/sourcefactory.h>
#include <vespa/config/common/configcontext.h>
#include <fstream>
#include <config-my.h>
#include <config-foo.h>
#include <config-foodefault.h>
#include <config-bar.h>
#include <config-foobar.h>
#include <vespa/log/log.h>
LOG_SETUP(".filesubscription_test");

using namespace config;

namespace {

    void writeFile(const std::string & fileName, const std::string & myFieldVal)
    {
        std::ofstream of;
        of.open(fileName.c_str());
        of << "myField \"" << myFieldVal << "\"\n";
        of.close();
    }
}


TEST("requireThatFileSpecGivesCorrectKey") {
    std::string str("/home/my/config.cfg");
    FileSpec spec(str);
    bool thrown = false;
    try {
        FileSpec s1("fb");
        FileSpec s2("fb.cfh");
        FileSpec s3("fb.dch");
        FileSpec s4("fbcfg");
        FileSpec s5(".cfg");
    } catch (const InvalidConfigSourceException & e) {
        thrown = true;
    }
    ASSERT_TRUE(thrown);

    thrown = false;
    try {
        FileSpec s1("fb.cfg");
        FileSpec s2("a.cfg");
        FileSpec s3("fljdlfjsalf.cfg");
    } catch (const InvalidConfigSourceException & e) {
        thrown = true;
    }
    ASSERT_FALSE(thrown);
}

TEST("requireThatFileSpecGivesCorrectSource") {
    writeFile("my.cfg", "foobar");
    FileSpec spec("my.cfg");

    auto factory = spec.createSourceFactory(TimingValues());
    ASSERT_TRUE(factory);
    auto holder = std::make_shared<ConfigHolder>();
    std::unique_ptr<Source> src = factory->createSource(holder, ConfigKey("my", "my", "bar", "foo"));
    ASSERT_TRUE(src);

    src->getConfig();
    ASSERT_TRUE(holder->poll());
    std::unique_ptr<ConfigUpdate> update(holder->provide());
    ASSERT_TRUE(update);
    const ConfigValue & value(update->getValue());
    ASSERT_EQUAL(1u, value.numLines());
    ASSERT_EQUAL("myField \"foobar\"", value.getLine(0));
}

TEST("requireThatFileSubscriptionReturnsCorrectConfig") {
    writeFile("my.cfg", "foobar");
    ConfigSubscriber s(FileSpec("my.cfg"));
    std::unique_ptr<ConfigHandle<MyConfig> > handle = s.subscribe<MyConfig>("my");
    s.nextConfigNow();
    std::unique_ptr<MyConfig> cfg = handle->getConfig();
    ASSERT_TRUE(cfg);
    ASSERT_EQUAL("foobar", cfg->myField);
    ASSERT_EQUAL("my", cfg->defName());
    ASSERT_FALSE(s.nextConfig(100ms));
}

TEST("requireThatReconfigIsCalledWhenConfigChanges") {
    writeFile("my.cfg", "foo");
    {
        auto context = std::make_shared<ConfigContext>(FileSpec("my.cfg"));
        ConfigSubscriber s(context);
        std::unique_ptr<ConfigHandle<MyConfig> > handle = s.subscribe<MyConfig>("");
        s.nextConfigNow();
        std::unique_ptr<MyConfig> cfg = handle->getConfig();
        ASSERT_TRUE(cfg);
        ASSERT_EQUAL("foo", cfg->myField);
        ASSERT_EQUAL("my", cfg->defName());
        ASSERT_FALSE(s.nextConfig(3000ms));
        writeFile("my.cfg", "bar");
        context->reload();
        bool correctValue = false;
        vespalib::Timer timer;
        while (!correctValue && timer.elapsed() < 20s) {
            LOG(info, "Testing value...");
            if (s.nextConfig(1000ms)) {
                break;
            }
        }
        cfg = handle->getConfig();
        ASSERT_TRUE(cfg);
        ASSERT_EQUAL("bar", cfg->myField);
        ASSERT_EQUAL("my", cfg->defName());
        ASSERT_FALSE(s.nextConfig(1000ms));
    }
}

TEST("requireThatMultipleSubscribersCanSubscribeToSameFile") {
    writeFile("my.cfg", "foobar");
    FileSpec spec("my.cfg");
    {
        ConfigSubscriber s1(spec);
        std::unique_ptr<ConfigHandle<MyConfig> > h1 = s1.subscribe<MyConfig>("");
        ASSERT_TRUE(s1.nextConfigNow());
        ConfigSubscriber s2(spec);
        std::unique_ptr<ConfigHandle<MyConfig> > h2 = s2.subscribe<MyConfig>("");
        ASSERT_TRUE(s2.nextConfigNow());
    }
}

TEST("requireThatCanSubscribeToDirectory") {
    DirSpec spec(TEST_PATH("cfgdir"));
    ConfigSubscriber s(spec);
    ConfigHandle<FooConfig>::UP fooHandle = s.subscribe<FooConfig>("");
    ConfigHandle<BarConfig>::UP barHandle = s.subscribe<BarConfig>("");
    ASSERT_TRUE(s.nextConfigNow());
    ASSERT_TRUE(fooHandle->isChanged());
    ASSERT_TRUE(barHandle->isChanged());
    std::unique_ptr<FooConfig> fooCfg = fooHandle->getConfig();
    std::unique_ptr<BarConfig> barCfg = barHandle->getConfig();
    ASSERT_TRUE(fooCfg);
    ASSERT_TRUE(barCfg);
    ASSERT_EQUAL("foofoo", fooCfg->fooValue);
    ASSERT_EQUAL("barbar", barCfg->barValue);
}

TEST("requireThatCanSubscribeToDirectoryWithEmptyCfgFile") {
    DirSpec spec(TEST_PATH("cfgemptyfile"));
    ConfigSubscriber s(spec);
    ConfigHandle<FoodefaultConfig>::UP fooHandle = s.subscribe<FoodefaultConfig>("");
    ConfigHandle<BarConfig>::UP barHandle = s.subscribe<BarConfig>("");
    ASSERT_TRUE(s.nextConfigNow());
    ASSERT_TRUE(fooHandle->isChanged());
    ASSERT_TRUE(barHandle->isChanged());
    std::unique_ptr<FoodefaultConfig> fooCfg = fooHandle->getConfig();
    std::unique_ptr<BarConfig> barCfg = barHandle->getConfig();
    ASSERT_TRUE(fooCfg);
    ASSERT_TRUE(barCfg);
    ASSERT_EQUAL("per", fooCfg->fooValue);
    ASSERT_EQUAL("barbar", barCfg->barValue);
}

TEST("requireThatCanSubscribeToDirectoryWithNonExistingCfgFile") {
    DirSpec spec(TEST_PATH("cfgnonexistingfile"));
    ConfigSubscriber s(spec);
    ConfigHandle<FoodefaultConfig>::UP fooHandle = s.subscribe<FoodefaultConfig>("");
    ConfigHandle<BarConfig>::UP barHandle = s.subscribe<BarConfig>("");
    ASSERT_TRUE(s.nextConfigNow());
    ASSERT_TRUE(fooHandle->isChanged());
    ASSERT_TRUE(barHandle->isChanged());
    std::unique_ptr<FoodefaultConfig> fooCfg = fooHandle->getConfig();
    std::unique_ptr<BarConfig> barCfg = barHandle->getConfig();
    ASSERT_TRUE(fooCfg);
    ASSERT_TRUE(barCfg);
    ASSERT_EQUAL("per", fooCfg->fooValue);
    ASSERT_EQUAL("barbar", barCfg->barValue);
}

TEST_F("requireThatDirSpecDoesNotMixNames",
       DirSpec(TEST_PATH("cfgdir2"))) {
    ConfigSubscriber s(f);
    ConfigHandle<BarConfig>::UP barHandle = s.subscribe<BarConfig>("");
    ConfigHandle<FoobarConfig>::UP foobarHandle = s.subscribe<FoobarConfig>("");
    s.nextConfigNow();
    std::unique_ptr<BarConfig> bar = barHandle->getConfig();
    std::unique_ptr<FoobarConfig> foobar = foobarHandle->getConfig();
    ASSERT_TRUE(bar);
    ASSERT_TRUE(foobar);
    ASSERT_EQUAL("barbar", bar->barValue);
    ASSERT_EQUAL("foobarlol", foobar->fooBarValue);
}

TEST_F("require that can subscribe multiple config ids of same config",
       DirSpec(TEST_PATH("cfgdir3"))) {
    ConfigSubscriber s(f1);
    ConfigHandle<BarConfig>::UP fooHandle = s.subscribe<BarConfig>("foo");
    ConfigHandle<BarConfig>::UP barHandle = s.subscribe<BarConfig>("bar");
    s.nextConfigNow();
    std::unique_ptr<BarConfig> bar1 = fooHandle->getConfig();
    std::unique_ptr<BarConfig> bar2 = barHandle->getConfig();
    ASSERT_TRUE(bar1);
    ASSERT_TRUE(bar2);
    ASSERT_EQUAL("barbar", bar1->barValue);
    ASSERT_EQUAL("foobarlol", bar2->barValue);
}

TEST_MAIN() { TEST_RUN_ALL(); }
