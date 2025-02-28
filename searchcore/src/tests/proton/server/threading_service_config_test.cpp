// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-proton.h>
#include <vespa/searchcore/proton/server/threading_service_config.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/hw_info.h>

using namespace proton;
using ProtonConfig = vespa::config::search::core::ProtonConfig;
using ProtonConfigBuilder = vespa::config::search::core::ProtonConfigBuilder;

namespace threading_service_config_test {

struct Fixture {
    ProtonConfig cfg;
    Fixture(uint32_t master_task_limit = 2000, int32_t task_limit = 500)
        : cfg(makeConfig(master_task_limit, task_limit))
    {
    }
    ProtonConfig makeConfig(uint32_t master_task_limit, int32_t task_limit) {
        ProtonConfigBuilder builder;
        builder.indexing.tasklimit = task_limit;
        builder.feeding.masterTaskLimit = master_task_limit;
        return builder;
    }
    ThreadingServiceConfig make() {
        return ThreadingServiceConfig::make(cfg);
    }
};

TEST(ThreadingServiceConfigTest, require_that_task_limits_are_set)
{
    Fixture f;
    auto tcfg = f.make();
    EXPECT_EQ(2000u, tcfg.master_task_limit());
    EXPECT_EQ(500u, tcfg.defaultTaskLimit());
    EXPECT_TRUE(tcfg.is_task_limit_hard());
}

TEST(ThreadingServiceConfigTest, require_that_negative_task_limit_makes_it_soft)
{
    Fixture f(3000, -700);
    auto tcfg = f.make();
    EXPECT_EQ(3000u, tcfg.master_task_limit());
    EXPECT_EQ(700u, tcfg.defaultTaskLimit());
    EXPECT_FALSE(tcfg.is_task_limit_hard());
}

namespace {

void assertConfig(uint32_t exp_master_task_limit, uint32_t exp_default_task_limit, const ThreadingServiceConfig& config) {
    EXPECT_EQ(exp_master_task_limit, config.master_task_limit());
    EXPECT_EQ(exp_default_task_limit, config.defaultTaskLimit());
}

}

TEST(ThreadingServiceConfigTest, require_that_config_can_be_somewhat_updated)
{
    Fixture f1;
    Fixture f2(3000, 1000);
    auto cfg1 = f1.make();
    assertConfig(2000, 500u, cfg1);
    const auto cfg2 = f2.make();
    assertConfig(3000u, 1000u, cfg2);
    cfg1.update(cfg2);
    assertConfig(3000u, 1000u, cfg1);
}

}
