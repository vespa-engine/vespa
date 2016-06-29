// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/proton/server/memory_flush_config_updater.h>

using namespace proton;
using vespa::config::search::core::ProtonConfig;

ProtonConfig::Flush::Memory
getConfig(int64_t maxMemory, int64_t conservativeMaxMemory, int64_t maxTlsSize, int64_t conservativeMaxTlsSize)
{
    ProtonConfig::Flush::Memory result;
    result.maxmemory = maxMemory;
    result.maxtlssize = maxTlsSize;
    result.conservative.maxmemory = conservativeMaxMemory;
    result.conservative.maxtlssize = conservativeMaxTlsSize;
    return result;
}

ProtonConfig::Flush::Memory
getDefaultConfig()
{
    return getConfig(4, 2, 20, 10);
}

struct Fixture
{
    MemoryFlush::SP strategy;
    MemoryFlushConfigUpdater updater;
    Fixture()
        : strategy(std::make_shared<MemoryFlush>(MemoryFlushConfigUpdater::convertConfig(getDefaultConfig()))),
          updater(strategy, getDefaultConfig())
    {}
    void assertStrategyConfig(int64_t expMaxGlobalMemory, int64_t expMaxGlobalTlsSize) {
        EXPECT_EQUAL(expMaxGlobalMemory, strategy->getConfig().maxGlobalMemory);
        EXPECT_EQUAL(expMaxGlobalTlsSize, strategy->getConfig().maxGlobalTlsSize);
    }
};

TEST_F("require that strategy is updated when setting new config", Fixture)
{
    f.updater.setConfig(getConfig(5, 3, 30, 15));
    TEST_DO(f.assertStrategyConfig(5, 30));
}

TEST_F("require that strategy is updated with normal values if no limits are reached", Fixture)
{
    f.updater.notifyDiskMemUsage(DiskMemUsageState(false, false));
    TEST_DO(f.assertStrategyConfig(4, 20));
}

TEST_F("require that strategy is updated with conservative max tls size value if disk limit is reached", Fixture)
{
    f.updater.notifyDiskMemUsage(DiskMemUsageState(true, false));
    TEST_DO(f.assertStrategyConfig(4, 10));
}

TEST_F("require that strategy is updated with conservative max memory value if memory limit is reached", Fixture)
{
    f.updater.notifyDiskMemUsage(DiskMemUsageState(false, true));
    TEST_DO(f.assertStrategyConfig(2, 20));
}

TEST_F("require that strategy is updated with all conservative values if both limits are reached", Fixture)
{
    f.updater.notifyDiskMemUsage(DiskMemUsageState(true, true));
    TEST_DO(f.assertStrategyConfig(2, 10));
}

TEST_F("require that last disk/memory usage state is remembered when setting new config", Fixture)
{
    f.updater.notifyDiskMemUsage(DiskMemUsageState(true, false));
    f.updater.setConfig(getConfig(5, 3, 30, 15));
    TEST_DO(f.assertStrategyConfig(5, 15));
}

TEST_F("require that last config if remembered when setting new disk/memory usage state", Fixture)
{
    f.updater.setConfig(getConfig(5, 3, 30, 15));
    f.updater.notifyDiskMemUsage(DiskMemUsageState(true, false));
    TEST_DO(f.assertStrategyConfig(5, 15));
}

TEST_MAIN() { TEST_RUN_ALL(); }
