// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("threading_service_config_test");

#include <vespa/searchcore/config/config-proton.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/server/threading_service_config.h>
#include <vespa/vespalib/testkit/testapp.h>

using namespace proton;
using ProtonConfig = vespa::config::search::core::ProtonConfig;
using ProtonConfigBuilder = vespa::config::search::core::ProtonConfigBuilder;

struct Fixture {
    ProtonConfig cfg;
    Fixture(uint32_t baseLineIndexingThreads = 2)
        : cfg(makeConfig(baseLineIndexingThreads))
    {
    }
    ProtonConfig makeConfig(uint32_t baseLineIndexingThreads) {
        ProtonConfigBuilder builder;
        builder.indexing.threads = baseLineIndexingThreads;
        builder.indexing.tasklimit = 500;
        builder.indexing.semiunboundtasklimit = 50000;
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
    TEST_DO(f.assertIndexingThreads(4, 64));
}

TEST_F("require that indexing threads is always >= 1", Fixture(0))
{
    TEST_DO(f.assertIndexingThreads(1, 0));
}

TEST_F("require that default task limit is set", Fixture)
{
    EXPECT_EQUAL(500u, f.make(24).defaultTaskLimit());
}

TEST_F("require that semiunbound task limit is scaled based on indexing threads", Fixture)
{
    EXPECT_EQUAL(12500u, f.make(24).semiUnboundTaskLimit());
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
