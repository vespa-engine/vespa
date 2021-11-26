// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/config/config-proton.h>
#include <vespa/searchcore/proton/server/shared_threading_service_config.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace proton;
using ProtonConfig = vespa::config::search::core::ProtonConfig;
using ProtonConfigBuilder = vespa::config::search::core::ProtonConfigBuilder;

ProtonConfig
make_proton_config(double concurrency)
{
    ProtonConfigBuilder builder;
    // This setup requires a minimum of 4 shared threads.
    builder.documentdb.push_back(ProtonConfig::Documentdb());
    builder.documentdb.push_back(ProtonConfig::Documentdb());
    builder.flush.maxconcurrent = 1;

    builder.feeding.concurrency = concurrency;
    return builder;
}

void
expect_shared_threads(uint32_t exp_threads, uint32_t cpu_cores)
{
    auto cfg = SharedThreadingServiceConfig::make(make_proton_config(0.5), HwInfo::Cpu(cpu_cores));
    EXPECT_EQ(exp_threads, cfg.shared_threads());
    EXPECT_EQ(exp_threads * 16, cfg.shared_task_limit());
}

TEST(SharedThreadingServiceConfigTest, shared_threads_are_derived_from_cpu_cores_and_feeding_concurrency)
{
    expect_shared_threads(4, 1);
    expect_shared_threads(4, 6);
    expect_shared_threads(4, 8);
    expect_shared_threads(5, 9);
    expect_shared_threads(5, 10);
}

GTEST_MAIN_RUN_ALL_TESTS()
