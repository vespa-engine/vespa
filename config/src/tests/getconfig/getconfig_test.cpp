// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "config-my.h"
#include <vespa/config/common/configcontext.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>

using namespace config;

namespace {

struct ConfigFixture {
    MyConfigBuilder builder;
    ConfigSet set;
    std::shared_ptr<IConfigContext> context;
    ConfigFixture() : builder(), set(), context() {
        set.addBuilder("cfgid", &builder);
        context = std::make_shared<ConfigContext>(set);
    }
};

} // namespace <unnamed>

TEST(GetConfigTest, requireThatGetConfigReturnsCorrectConfig_from_raw)
{
    RawSpec spec("myField \"foo\"\n");

    std::unique_ptr<MyConfig> cfg = ConfigGetter<MyConfig>::getConfig("myid", spec);
    ASSERT_TRUE(cfg);
    ASSERT_EQ("my", cfg->defName());
    ASSERT_EQ("foo", cfg->myField);
}


TEST(GetConfigTest, requireThatGetConfigReturnsCorrectConfig_from_file)
{
    FileSpec spec(TEST_PATH("my.cfg"));
    std::unique_ptr<MyConfig> cfg = ConfigGetter<MyConfig>::getConfig("", spec);
    ASSERT_TRUE(cfg);
    ASSERT_EQ("my", cfg->defName());
    ASSERT_EQ("foobar", cfg->myField);
}

TEST(GetConfigTest, require_that_ConfigGetter_can_be_used_to_obtain_config_generation)
{
    ConfigFixture f1;
    f1.builder.myField = "foo";
    {
        int64_t gen1;
        int64_t gen2;
        std::unique_ptr<MyConfig> cfg1 = ConfigGetter<MyConfig>::getConfig(gen1, "cfgid", f1.set);
        std::unique_ptr<MyConfig> cfg2 = ConfigGetter<MyConfig>::getConfig(gen2, "cfgid", f1.context);
        EXPECT_EQ(1, gen1);
        EXPECT_EQ(1, gen2);
        EXPECT_EQ("foo", cfg1.get()->myField);
        EXPECT_EQ("foo", cfg2.get()->myField);
    }
    f1.builder.myField = "bar";
    f1.context->reload();
    {
        int64_t gen1;
        int64_t gen2;
        std::unique_ptr<MyConfig> cfg1 = ConfigGetter<MyConfig>::getConfig(gen1, "cfgid", f1.set);
        std::unique_ptr<MyConfig> cfg2 = ConfigGetter<MyConfig>::getConfig(gen2, "cfgid", f1.context);
        EXPECT_EQ(1, gen1); // <-- NB: generation will not increase when using the builder set directly
        EXPECT_EQ(2, gen2);
        EXPECT_EQ("bar", cfg1.get()->myField);
        EXPECT_EQ("bar", cfg2.get()->myField);
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
