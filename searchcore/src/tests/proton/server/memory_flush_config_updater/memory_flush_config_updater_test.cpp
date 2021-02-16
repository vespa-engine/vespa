// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/server/memory_flush_config_updater.h>
#include <vespa/vespalib/util/size_literals.h>

using namespace proton;
using vespa::config::search::core::ProtonConfig;

ProtonConfig::Flush::Memory
getConfig(int64_t maxMemory, int64_t eachMaxMemory, int64_t maxTlsSize,
          double conservativeMemoryLimitFactor = 0.5,
          double conservativeDiskLimitFactor = 0.6,
          double lowWatermarkFactor = 0.8)
{
    ProtonConfig::Flush::Memory result;
    result.maxmemory = maxMemory;
    result.each.maxmemory = eachMaxMemory;
    result.maxtlssize = maxTlsSize;
    result.conservative.memorylimitfactor = conservativeMemoryLimitFactor;
    result.conservative.disklimitfactor = conservativeDiskLimitFactor;
    result.conservative.lowwatermarkfactor = lowWatermarkFactor;
    return result;
}

ProtonConfig::Flush::Memory
getDefaultConfig()
{
    return getConfig(4, 1, 20);
}

ResourceUsageState
aboveLimit()
{
    return ResourceUsageState(0.7, 0.8);
}

ResourceUsageState
belowLimit()
{
    // This is still over default low watermark of 0.56 (0.7 * 0.8)
    return ResourceUsageState(0.7, 0.6);
}

const HwInfo::Memory defaultMemory(8_Gi);

struct Fixture
{
    MemoryFlush::SP strategy;
    MemoryFlushConfigUpdater updater;
    Fixture()
        : strategy(std::make_shared<MemoryFlush>(MemoryFlushConfigUpdater::convertConfig(getDefaultConfig(), defaultMemory))),
          updater(strategy, getDefaultConfig(), defaultMemory)
    {}
    void assertStrategyConfig(uint64_t expMaxGlobalMemory, int64_t expMaxEachMemory, uint64_t expMaxGlobalTlsSize) {
        EXPECT_EQUAL(expMaxGlobalMemory, strategy->getConfig().maxGlobalMemory);
        EXPECT_EQUAL(expMaxEachMemory, strategy->getConfig().maxMemoryGain);
        EXPECT_EQUAL(expMaxGlobalTlsSize, strategy->getConfig().maxGlobalTlsSize);
    }
    void assertStrategyDiskConfig(double expGlobalDiskBloatFactor, double expDiskBloatFactor) {
        EXPECT_APPROX(expGlobalDiskBloatFactor, strategy->getConfig().globalDiskBloatFactor, 0.00001);
        EXPECT_APPROX(expDiskBloatFactor, strategy->getConfig().diskBloatFactor, 0.00001);
    }
    void notifyDiskMemUsage(const ResourceUsageState &diskState, const ResourceUsageState &memoryState) {
        updater.notifyDiskMemUsage(DiskMemUsageState(diskState, memoryState));
    }
    void setNodeRetired(bool nodeRetired) {
        updater.setNodeRetired(nodeRetired);
    }
};

TEST_F("require that strategy is updated when setting new config", Fixture)
{
    f.updater.setConfig(getConfig(6, 3, 30));
    TEST_DO(f.assertStrategyConfig(6, 3, 30));
}

TEST("require that we use configured memory limits") {
    auto cfg = MemoryFlushConfigUpdater::convertConfig(getConfig(6, 3, 30), defaultMemory);
    EXPECT_EQUAL(cfg.maxGlobalMemory, 6u);
    EXPECT_EQUAL(cfg.maxMemoryGain, 3);
}

TEST("require that we cap configured limits based on available memory") {
    const uint64_t LIMIT = defaultMemory.sizeBytes()/4;
    auto cfg = MemoryFlushConfigUpdater::convertConfig(getConfig(4_Gi, 4_Gi, 30), defaultMemory);
    EXPECT_EQUAL(cfg.maxGlobalMemory, LIMIT);
    EXPECT_EQUAL(uint64_t(cfg.maxMemoryGain), LIMIT);
}

TEST_F("require that strategy is updated with normal values if no limits are reached", Fixture)
{
    f.updater.notifyDiskMemUsage(DiskMemUsageState());
    TEST_DO(f.assertStrategyConfig(4, 1, 20));
}

TEST_F("require that strategy is updated with conservative max tls size value if disk limit is reached", Fixture)
{
    f.notifyDiskMemUsage(aboveLimit(), belowLimit());
    TEST_DO(f.assertStrategyConfig(4, 1, 12));
}

TEST_F("require that strategy is updated with conservative max memory value if memory limit is reached", Fixture)
{
    f.notifyDiskMemUsage(belowLimit(), aboveLimit());
    TEST_DO(f.assertStrategyConfig(2, 0, 20));
}

TEST_F("require that strategy is updated with all conservative values if both limits are reached", Fixture)
{
    f.notifyDiskMemUsage(aboveLimit(), aboveLimit());
    TEST_DO(f.assertStrategyConfig(2, 0, 12));
}

TEST_F("require that last disk/memory usage state is remembered when setting new config", Fixture)
{
    f.notifyDiskMemUsage(aboveLimit(), belowLimit());
    f.updater.setConfig(getConfig(6, 3, 30));
    TEST_DO(f.assertStrategyConfig(6, 3, 18));
}

TEST_F("require that last config if remembered when setting new disk/memory usage state", Fixture)
{
    f.updater.setConfig(getConfig(6, 3, 30));
    f.notifyDiskMemUsage(aboveLimit(), belowLimit());
    TEST_DO(f.assertStrategyConfig(6, 3, 18));
}

TEST_F("require that we must go below low watermark for disk usage before using normal tls size value again", Fixture)
{
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.8), belowLimit());
    TEST_DO(f.assertStrategyConfig(4, 1, 12));
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.7), belowLimit());
    TEST_DO(f.assertStrategyConfig(4, 1, 12));
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.56), belowLimit());
    TEST_DO(f.assertStrategyConfig(4, 1, 12));
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.55), belowLimit());
    TEST_DO(f.assertStrategyConfig(4, 1, 20));
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.6), belowLimit());
    TEST_DO(f.assertStrategyConfig(4, 1, 20));
}

TEST_F("require that we must go below low watermark for memory usage before using normal max memory value again", Fixture)
{
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.8));
    TEST_DO(f.assertStrategyConfig(2, 0, 20));
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.7));
    TEST_DO(f.assertStrategyConfig(2, 0, 20));
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.56));
    TEST_DO(f.assertStrategyConfig(2, 0, 20));
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.55));
    TEST_DO(f.assertStrategyConfig(4, 1, 20));
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.6));
    TEST_DO(f.assertStrategyConfig(4, 1, 20));
}

TEST_F("require that more disk bloat is allowed while node state is retired", Fixture)
{
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.3), belowLimit());
    TEST_DO(f.assertStrategyDiskConfig(0.2, 0.2));
    f.setNodeRetired(true);
    TEST_DO(f.assertStrategyDiskConfig((0.8 - 0.3 / 0.7) * 0.8, 1.0));
    f.notifyDiskMemUsage(belowLimit(), belowLimit());
    TEST_DO(f.assertStrategyDiskConfig(0.2, 0.2));
}

TEST_MAIN() { TEST_RUN_ALL(); }
