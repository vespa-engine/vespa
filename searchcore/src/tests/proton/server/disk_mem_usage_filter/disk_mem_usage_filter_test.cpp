// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/server/disk_mem_usage_filter.h>

using proton::DiskMemUsageFilter;
using proton::HwInfo;

namespace fs = std::experimental::filesystem;

namespace {

struct Fixture
{
    DiskMemUsageFilter _filter;
    using State = DiskMemUsageFilter::State;
    using Config = DiskMemUsageFilter::Config;

    Fixture()
        : _filter(HwInfo(HwInfo::Disk(100, false, false), HwInfo::Memory(64 * 1024 * 1024), HwInfo::Cpu(0)))
    {
        _filter.setDiskUsedSize(0);
        _filter.setMemoryStats(vespalib::ProcessMemoryStats(10000000,
                                                            10000001,
                                                            10000002,
                                                            10000003,
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
        _filter.setMemoryStats(vespalib::ProcessMemoryStats(58720259,
                                                            58720258,
                                                            58720257,
                                                            58720256,
                                                            43));
    }
};

}

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

TEST_F("Check that disk limit can be reached", Fixture)
{
    f._filter.setConfig(Fixture::Config(1.0, 0.8));
    f.triggerDiskLimit();
    f.testWrite("diskLimitReached: { "
                "action: \"add more content nodes\", "
                "reason: \"disk used (0.9) > disk limit (0.8)\", "
                "stats: { "
                "capacity: 100, used: 90, diskUsed: 0.9, diskLimit: 0.8}}");
}

TEST_F("Check that memory limit can be reached", Fixture)
{
    f._filter.setConfig(Fixture::Config(0.8, 1.0));
    f.triggerMemoryLimit();
    f.testWrite("memoryLimitReached: { "
                "action: \"add more content nodes\", "
                "reason: \"memory used (0.875) > memory limit (0.8)\", "
                "stats: { "
                "mapped: { virt: 58720259, rss: 58720258}, "
                "anonymous: { virt: 58720257, rss: 58720256}, "
                "physicalMemory: 67108864, memoryUsed: 0.875, memoryLimit: 0.8}}");
}

TEST_F("Check that both disk limit and memory limit can be reached", Fixture)
{
    f._filter.setConfig(Fixture::Config(0.8, 0.8));
    f.triggerMemoryLimit();
    f.triggerDiskLimit();
    f.testWrite("memoryLimitReached: { "
                "action: \"add more content nodes\", "
                "reason: \"memory used (0.875) > memory limit (0.8)\", "
                "stats: { "
                "mapped: { virt: 58720259, rss: 58720258}, "
                "anonymous: { virt: 58720257, rss: 58720256}, "
                "physicalMemory: 67108864, memoryUsed: 0.875, memoryLimit: 0.8}}, "
                "diskLimitReached: { "
                "action: \"add more content nodes\", "
                "reason: \"disk used (0.9) > disk limit (0.8)\", "
                "stats: { "
                "capacity: 100, used: 90, diskUsed: 0.9, diskLimit: 0.8}}");
}

TEST_MAIN() { TEST_RUN_ALL(); }
