// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/server/disk_mem_usage_filter.h>
#include <vespa/searchcore/proton/server/resource_usage_state.h>

using namespace proton;

namespace fs = std::filesystem;

struct Fixture
{
    DiskMemUsageFilter _filter;
    using State = DiskMemUsageFilter::State;
    using Config = DiskMemUsageFilter::Config;

    Fixture()
        : _filter(HwInfo(HwInfo::Disk(100, false, false), HwInfo::Memory(1000), HwInfo::Cpu(0)))
    {
        _filter.setDiskUsedSize(20);
        _filter.setMemoryStats(vespalib::ProcessMemoryStats(297,
                                                            298,
                                                            299,
                                                            300,
                                                            42));
    }

    void testWrite(const vespalib::string &exp) {
        if (exp.empty()) {
            EXPECT_TRUE(_filter.acceptWriteOperation());
            State state = _filter.getAcceptState();
            EXPECT_TRUE(state.acceptWriteOperation());
            EXPECT_EQUAL(exp, state.message());
        } else {
            EXPECT_FALSE(_filter.acceptWriteOperation());
            State state = _filter.getAcceptState();
            EXPECT_FALSE(state.acceptWriteOperation());
            EXPECT_EQUAL(exp, state.message());
        }
    }

    void triggerDiskLimit() {
        _filter.setDiskUsedSize(90);
    }

    void triggerMemoryLimit()
    {
        _filter.setMemoryStats(vespalib::ProcessMemoryStats(897,
                                                            898,
                                                            899,
                                                            900,
                                                            43));
    }
};

TEST_F("Check that default filter allows write", Fixture)
{
    f.testWrite("");
}

TEST_F("Check that stats are wired through", Fixture)
{
    EXPECT_EQUAL(42u, f._filter.getMemoryStats().getMappingsCount());
    f.triggerMemoryLimit();
    EXPECT_EQUAL(43u, f._filter.getMemoryStats().getMappingsCount());
}

void
assertResourceUsage(double usage, double limit, double utilization, const ResourceUsageState &state)
{
    EXPECT_EQUAL(usage, state.usage());
    EXPECT_EQUAL(limit, state.limit());
    EXPECT_EQUAL(utilization, state.utilization());
}

TEST_F("Check that disk limit can be reached", Fixture)
{
    f._filter.setConfig(Fixture::Config(1.0, 0.8));
    TEST_DO(assertResourceUsage(0.2, 0.8, 0.25, f._filter.usageState().diskState()));
    f.triggerDiskLimit();
    f.testWrite("diskLimitReached: { "
                "action: \"add more content nodes\", "
                "reason: \"disk used (0.9) > disk limit (0.8)\", "
                "stats: { "
                "capacity: 100, used: 90, diskUsed: 0.9, diskLimit: 0.8}}");
    TEST_DO(assertResourceUsage(0.9, 0.8, 1.125, f._filter.usageState().diskState()));
}

TEST_F("Check that memory limit can be reached", Fixture)
{
    f._filter.setConfig(Fixture::Config(0.8, 1.0));
    TEST_DO(assertResourceUsage(0.3, 0.8, 0.375, f._filter.usageState().memoryState()));
    f.triggerMemoryLimit();
    f.testWrite("memoryLimitReached: { "
                "action: \"add more content nodes\", "
                "reason: \"memory used (0.9) > memory limit (0.8)\", "
                "stats: { "
                "mapped: { virt: 897, rss: 898}, "
                "anonymous: { virt: 899, rss: 900}, "
                "physicalMemory: 1000, memoryUsed: 0.9, memoryLimit: 0.8}}");
    TEST_DO(assertResourceUsage(0.9, 0.8, 1.125, f._filter.usageState().memoryState()));
}

TEST_F("Check that both disk limit and memory limit can be reached", Fixture)
{
    f._filter.setConfig(Fixture::Config(0.8, 0.8));
    f.triggerMemoryLimit();
    f.triggerDiskLimit();
    f.testWrite("memoryLimitReached: { "
                "action: \"add more content nodes\", "
                "reason: \"memory used (0.9) > memory limit (0.8)\", "
                "stats: { "
                "mapped: { virt: 897, rss: 898}, "
                "anonymous: { virt: 899, rss: 900}, "
                "physicalMemory: 1000, memoryUsed: 0.9, memoryLimit: 0.8}}, "
                "diskLimitReached: { "
                "action: \"add more content nodes\", "
                "reason: \"disk used (0.9) > disk limit (0.8)\", "
                "stats: { "
                "capacity: 100, used: 90, diskUsed: 0.9, diskLimit: 0.8}}");
}

TEST_MAIN() { TEST_RUN_ALL(); }
