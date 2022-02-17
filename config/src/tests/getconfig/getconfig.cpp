// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/config/common/configcontext.h>
#include "config-my.h"

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

TEST("requireThatGetConfigReturnsCorrectConfig")
{
    RawSpec spec("myField \"foo\"\n");

    std::unique_ptr<MyConfig> cfg = ConfigGetter<MyConfig>::getConfig("myid", spec);
    ASSERT_TRUE(cfg);
    ASSERT_EQUAL("my", cfg->defName());
    ASSERT_EQUAL("foo", cfg->myField);
}


TEST("requireThatGetConfigReturnsCorrectConfig")
{
    FileSpec spec(TEST_PATH("my.cfg"));
    std::unique_ptr<MyConfig> cfg = ConfigGetter<MyConfig>::getConfig("", spec);
    ASSERT_TRUE(cfg);
    ASSERT_EQUAL("my", cfg->defName());
    ASSERT_EQUAL("foobar", cfg->myField);
}

TEST_F("require that ConfigGetter can be used to obtain config generation", ConfigFixture) {
    f1.builder.myField = "foo";
    {
        int64_t gen1;
        int64_t gen2;
        std::unique_ptr<MyConfig> cfg1 = ConfigGetter<MyConfig>::getConfig(gen1, "cfgid", f1.set);
        std::unique_ptr<MyConfig> cfg2 = ConfigGetter<MyConfig>::getConfig(gen2, "cfgid", f1.context);
        EXPECT_EQUAL(1, gen1);
        EXPECT_EQUAL(1, gen2);
        EXPECT_EQUAL("foo", cfg1.get()->myField);
        EXPECT_EQUAL("foo", cfg2.get()->myField);
    }
    f1.builder.myField = "bar";
    f1.context->reload();
    {
        int64_t gen1;
        int64_t gen2;
        std::unique_ptr<MyConfig> cfg1 = ConfigGetter<MyConfig>::getConfig(gen1, "cfgid", f1.set);
        std::unique_ptr<MyConfig> cfg2 = ConfigGetter<MyConfig>::getConfig(gen2, "cfgid", f1.context);
        EXPECT_EQUAL(1, gen1); // <-- NB: generation will not increase when using the builder set directly
        EXPECT_EQUAL(2, gen2);
        EXPECT_EQUAL("bar", cfg1.get()->myField);
        EXPECT_EQUAL("bar", cfg2.get()->myField);
    }
}

TEST_MAIN() { TEST_RUN_ALL(); }
