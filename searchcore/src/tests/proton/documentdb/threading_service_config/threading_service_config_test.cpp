// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-proton.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/server/threading_service_config.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/log/log.h>
LOG_SETUP("threading_service_config_test");

using namespace proton;
using ProtonConfig = vespa::config::search::core::ProtonConfig;
using ProtonConfigBuilder = vespa::config::search::core::ProtonConfigBuilder;

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

TEST_F("require that task limits are set", Fixture)
{
    auto tcfg = f.make();
    EXPECT_EQUAL(2000u, tcfg.master_task_limit());
    EXPECT_EQUAL(500u, tcfg.defaultTaskLimit());
    EXPECT_TRUE(tcfg.is_task_limit_hard());
}

TEST_F("require that negative task limit makes it soft", Fixture(3000, -700))
{
    auto tcfg = f.make();
    EXPECT_EQUAL(3000u, tcfg.master_task_limit());
    EXPECT_EQUAL(700u, tcfg.defaultTaskLimit());
    EXPECT_FALSE(tcfg.is_task_limit_hard());
}

namespace {

void assertConfig(uint32_t exp_master_task_limit, uint32_t exp_default_task_limit, const ThreadingServiceConfig& config) {
    EXPECT_EQUAL(exp_master_task_limit, config.master_task_limit());
    EXPECT_EQUAL(exp_default_task_limit, config.defaultTaskLimit());
}

}

TEST_FF("require that config can be somewhat updated", Fixture(), Fixture(3000, 1000))
{
    auto cfg1 = f1.make();
    assertConfig(2000, 500u, cfg1);
    const auto cfg2 = f2.make();
    assertConfig(3000u, 1000u, cfg2);
    cfg1.update(cfg2);
    assertConfig(3000u, 1000u, cfg1);
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
