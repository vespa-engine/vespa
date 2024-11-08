// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/server/memory_flush_config_updater.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>

using namespace proton;
using vespa::config::search::core::ProtonConfig;
using vespalib::HwInfo;

inline namespace memory_flush_config_updater_test {

ProtonConfig::Flush::Memory
getConfig(int64_t maxMemory, int64_t eachMaxMemory, int64_t maxTlsSize,
          double conservativeMemoryLimitFactor = 0.5,
          double conservativeDiskLimitFactor = 0.6,
          double high_watermark_factor = 0.9,
          double lowWatermarkFactor = 0.8)
{
    ProtonConfig::Flush::Memory result;
    result.maxmemory = maxMemory;
    result.each.maxmemory = eachMaxMemory;
    result.maxtlssize = maxTlsSize;
    result.conservative.memorylimitfactor = conservativeMemoryLimitFactor;
    result.conservative.disklimitfactor = conservativeDiskLimitFactor;
    result.conservative.highwatermarkfactor = high_watermark_factor;
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
    // The high watermark limit is 0.63 (0.7 * 0.9 (factor)).
    return ResourceUsageState(0.7, 0.64);
}

ResourceUsageState
belowLimit()
{
    // The high watermark limit is 0.63 (0.7 * 0.9 (factor)).
    // This is still over the low watermark limit of 0.56 (0.7 * 0.8 (factor)).
    return ResourceUsageState(0.7, 0.62);
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
    ~Fixture();
    void assertStrategyConfig(uint64_t expMaxGlobalMemory, int64_t expMaxEachMemory, uint64_t expMaxGlobalTlsSize) {
        EXPECT_EQ(expMaxGlobalMemory, strategy->getConfig().maxGlobalMemory);
        EXPECT_EQ(expMaxEachMemory, strategy->getConfig().maxMemoryGain);
        EXPECT_EQ(expMaxGlobalTlsSize, strategy->getConfig().maxGlobalTlsSize);
    }
    void assertStrategyConfig(const std::string& label, uint64_t expMaxGlobalMemory, int64_t expMaxEachMemory, uint64_t expMaxGlobalTlsSize) {
        SCOPED_TRACE(label);
        assertStrategyConfig(expMaxGlobalMemory, expMaxEachMemory, expMaxGlobalTlsSize);
    }
    void assertStrategyDiskConfig(const std::string& label, double expGlobalDiskBloatFactor, double expDiskBloatFactor) {
        SCOPED_TRACE(label);
        EXPECT_NEAR(expGlobalDiskBloatFactor, strategy->getConfig().globalDiskBloatFactor, 0.00001);
        EXPECT_NEAR(expDiskBloatFactor, strategy->getConfig().diskBloatFactor, 0.00001);
    }
    void notifyDiskMemUsage(const ResourceUsageState &diskState, const ResourceUsageState &memoryState) {
        updater.notifyDiskMemUsage(DiskMemUsageState(diskState, memoryState));
    }
    void set_node_retired_or_maintenance(bool value) {
        updater.set_node_retired_or_maintenance(value);
    }
};

Fixture::~Fixture() = default;

void
expectEqual(const MemoryFlush::Config & a, const MemoryFlush::Config & b) {
    EXPECT_TRUE(a.equal(b));
    EXPECT_TRUE(a == b);
    EXPECT_FALSE( a != b);
    EXPECT_TRUE(b.equal(a));
    EXPECT_TRUE(b == a);
    EXPECT_FALSE( b != a);
}

void
expectNotEqual(const MemoryFlush::Config & a, const MemoryFlush::Config & b) {
    EXPECT_FALSE(a.equal(b));
    EXPECT_FALSE(a == b);
    EXPECT_TRUE( a != b);
    EXPECT_FALSE(b.equal(a));
    EXPECT_FALSE(b == a);
    EXPECT_TRUE( b != a);
}

}

TEST(MemoryFlushConfigUpdaterTest, require_that_strategy_is_updated_when_setting_new_config)
{
    Fixture f;
    f.updater.setConfig(getConfig(6, 3, 30));
    f.assertStrategyConfig(6, 3, 30);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_MemoryFlush_Config_equal_is_correct)
{
    MemoryFlush::Config a, b;
    expectEqual(a, b);
    a.maxGlobalMemory = 7;
    expectNotEqual(a, b);
    b.maxGlobalMemory = 7;
    expectEqual(a, b);
    a.maxMemoryGain = 8;
    expectNotEqual(a, b);
    b.maxMemoryGain = 8;
    expectEqual(a, b);
    a.maxGlobalTlsSize = 9;
    expectNotEqual(a, b);
    b.maxGlobalTlsSize = 9;
    expectEqual(a, b);
    a.maxTimeGain = 10us;
    expectNotEqual(a, b);
    b.maxTimeGain = 10us;
    expectEqual(a, b);
    a.globalDiskBloatFactor = 11;
    expectNotEqual(a, b);
    b.globalDiskBloatFactor = 11;
    expectEqual(a, b);
    a.diskBloatFactor = 12;
    expectNotEqual(a, b);
    b.diskBloatFactor = 12;
    expectEqual(a, b);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_we_use_configured_memory_limits)
{
    auto cfg = MemoryFlushConfigUpdater::convertConfig(getConfig(6, 3, 30), defaultMemory);
    EXPECT_EQ(cfg.maxGlobalMemory, 6u);
    EXPECT_EQ(cfg.maxMemoryGain, 3);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_we_cap_configured_limits_based_on_available_memory)
{
    const uint64_t LIMIT = defaultMemory.sizeBytes()/4;
    auto cfg = MemoryFlushConfigUpdater::convertConfig(getConfig(4_Gi, 4_Gi, 30), defaultMemory);
    EXPECT_EQ(cfg.maxGlobalMemory, LIMIT);
    EXPECT_EQ(uint64_t(cfg.maxMemoryGain), LIMIT);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_strategy_is_updated_with_normal_values_if_no_limits_are_reached)
{
    Fixture f;
    f.updater.notifyDiskMemUsage(DiskMemUsageState());
    f.assertStrategyConfig(4, 1, 20);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_strategy_is_updated_with_conservative_max_tls_size_value_if_disk_limit_is_reached)
{
    Fixture f;
    f.notifyDiskMemUsage(aboveLimit(), belowLimit());
    f.assertStrategyConfig(4, 1, 12);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_strategy_is_updated_with_conservative_max_memory_value_if_memory_limit_is_reached)
{
    Fixture f;
    f.notifyDiskMemUsage(belowLimit(), aboveLimit());
    f.assertStrategyConfig(2, 0, 20);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_strategy_is_updated_with_all_conservative_values_if_both_limits_are_reached)
{
    Fixture f;
    f.notifyDiskMemUsage(aboveLimit(), aboveLimit());
    f.assertStrategyConfig(2, 0, 12);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_last_disk_and_memory_usage_state_is_remembered_when_setting_new_config)
{
    Fixture f;
    f.notifyDiskMemUsage(aboveLimit(), belowLimit());
    f.updater.setConfig(getConfig(6, 3, 30));
    f.assertStrategyConfig(6, 3, 18);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_last_config_if_remembered_when_setting_new_disk_and_memory_usage_state)
{
    Fixture f;
    f.updater.setConfig(getConfig(6, 3, 30));
    f.notifyDiskMemUsage(aboveLimit(), belowLimit());
    f.assertStrategyConfig(6, 3, 18);
}

TEST(MemoryFlushConfigUpdaterTest, Use_conservative_settings_when_above_high_watermark_for_disk_usage)
{
    Fixture f;
    // The high watermark limit is 0.63 (0.7 * 0.9 (factor)).
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.62), belowLimit());
    f.assertStrategyConfig("1st notify", 4, 1, 20);
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.64), belowLimit());
    f.assertStrategyConfig("2nd notify", 4, 1, 12);
}

TEST(MemoryFlushConfigUpdaterTest, Use_conservative_settings_when_above_high_watermark_for_memory_usage)
{
    Fixture f;
    // The high watermark limit is 0.54 (0.6 * 0.9 (factor)).
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.6, 0.53));
    f.assertStrategyConfig("1st notify", 4, 1, 20);
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.6, 0.55));
    f.assertStrategyConfig("2nd notify", 2, 0, 20);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_we_must_go_below_low_watermark_for_disk_usage_before_using_normal_tls_size_value_again)
{
    Fixture f;
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.8), belowLimit());
    f.assertStrategyConfig("1st notify", 4, 1, 12);
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.7), belowLimit());
    f.assertStrategyConfig("2nd notify", 4, 1, 12);
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.56), belowLimit());
    f.assertStrategyConfig("3rd notify", 4, 1, 12);
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.55), belowLimit());
    f.assertStrategyConfig("4th notify", 4, 1, 20);
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.6), belowLimit());
    f.assertStrategyConfig("5th notify", 4, 1, 20);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_we_must_go_below_low_watermark_for_memory_usage_before_using_normal_max_memory_value_again)
{
    Fixture f;
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.8));
    f.assertStrategyConfig("1st notify", 2, 0, 20);
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.7));
    f.assertStrategyConfig("2nd notify", 2, 0, 20);
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.56));
    f.assertStrategyConfig("3rd notify", 2, 0, 20);
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.55));
    f.assertStrategyConfig("4th notify", 4, 1, 20);
    f.notifyDiskMemUsage(belowLimit(), ResourceUsageState(0.7, 0.6));
    f.assertStrategyConfig("5th notify", 4, 1, 20);
}

TEST(MemoryFlushConfigUpdaterTest, require_that_more_disk_bloat_is_allowed_while_node_state_is_retired_or_maintenance)
{
    Fixture f;
    constexpr double DEFAULT_DISK_BLOAT = 0.25;
    f.notifyDiskMemUsage(ResourceUsageState(0.7, 0.3), belowLimit());
    f.assertStrategyDiskConfig("1st notify", DEFAULT_DISK_BLOAT, DEFAULT_DISK_BLOAT);
    f.set_node_retired_or_maintenance(true);
    f.assertStrategyDiskConfig("2nd notify", (0.8 - ((0.3/0.7)*(1 - DEFAULT_DISK_BLOAT))) / 0.8, 1.0);
    f.notifyDiskMemUsage(belowLimit(), belowLimit());
    f.assertStrategyDiskConfig("erd notify", DEFAULT_DISK_BLOAT, DEFAULT_DISK_BLOAT);
}
