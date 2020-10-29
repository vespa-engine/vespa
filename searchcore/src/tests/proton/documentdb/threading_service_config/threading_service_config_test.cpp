// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/config/config-proton.h>
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
    Fixture(uint32_t baseLineIndexingThreads = 2, uint32_t task_limit = 500, uint32_t semi_unbound_task_limit = 50000)
        : cfg(makeConfig(baseLineIndexingThreads, task_limit, semi_unbound_task_limit))
    {
    }
    ProtonConfig makeConfig(uint32_t baseLineIndexingThreads, uint32_t task_limit, uint32_t semi_unbound_task_limit) {
        ProtonConfigBuilder builder;
        builder.indexing.threads = baseLineIndexingThreads;
        builder.indexing.tasklimit = task_limit;
        builder.indexing.semiunboundtasklimit = semi_unbound_task_limit;
        return builder;
    }
    ThreadingServiceConfig make(uint32_t cpuCores) {
        return ThreadingServiceConfig::make(cfg, 0.5, HwInfo::Cpu(cpuCores));
    }
    void assertIndexingThreads(uint32_t expIndexingThreads, uint32_t cpuCores) {
        EXPECT_EQUAL(expIndexingThreads, make(cpuCores).indexingThreads());
    }
};

TEST_F("require that indexing threads are set based on cpu cores and feeding concurrency", Fixture)
{
    TEST_DO(f.assertIndexingThreads(2, 1));
    TEST_DO(f.assertIndexingThreads(2, 4));
    TEST_DO(f.assertIndexingThreads(2, 8));
    TEST_DO(f.assertIndexingThreads(2, 12));
    TEST_DO(f.assertIndexingThreads(3, 13));
    TEST_DO(f.assertIndexingThreads(3, 18));
    TEST_DO(f.assertIndexingThreads(4, 19));
    TEST_DO(f.assertIndexingThreads(4, 24));
    TEST_DO(f.assertIndexingThreads(4, 64)); // Ensure it is capped at 4
}

TEST_F("require that indexing threads is always >= 1", Fixture(0))
{
    TEST_DO(f.assertIndexingThreads(1, 0));
}

TEST_F("require that default task limit is set", Fixture)
{
    EXPECT_EQUAL(500u, f.make(24).defaultTaskLimit());
}

namespace {

void assertConfig(uint32_t exp_indexing_threads, uint32_t exp_default_task_limit, const ThreadingServiceConfig &config) {
    EXPECT_EQUAL(exp_indexing_threads, config.indexingThreads());
    EXPECT_EQUAL(exp_default_task_limit, config.defaultTaskLimit());
}

}

TEST_FF("require that config can be somewhat updated", Fixture(), Fixture(2, 1000, 100000))
{
    auto cfg1 = f1.make(1);
    assertConfig(2u, 500u, cfg1);
    const auto cfg2 = f2.make(13);
    assertConfig(3u, 1000u, cfg2);
    cfg1.update(cfg2);
    assertConfig(2u, 1000u, cfg1); // Indexing threads not changed
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
