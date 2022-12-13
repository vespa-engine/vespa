// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/common/i_transient_resource_usage_provider.h>
#include <vespa/searchcore/proton/server/disk_mem_usage_sampler.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP("disk_mem_usage_sampler_test");

using namespace proton;
using namespace std::chrono_literals;

constexpr uint64_t disk_size_bytes = 200000;
constexpr uint64_t memory_size_bytes = 100000;

HwInfo
make_hw_info()
{
    return HwInfo(HwInfo::Disk(disk_size_bytes, false, true),
                  HwInfo::Memory(memory_size_bytes),
                  HwInfo::Cpu(1));
}

class MyProvider : public ITransientResourceUsageProvider {
private:
    size_t _memory_usage;
    size_t _disk_usage;

public:
    MyProvider(size_t memory_usage, size_t disk_usage) noexcept
        : _memory_usage(memory_usage),
          _disk_usage(disk_usage)
    {}
    TransientResourceUsage get_transient_resource_usage() const override { return {_disk_usage, _memory_usage}; }
};

struct DiskMemUsageSamplerTest : public ::testing::Test {
    Transport transport;
    std::unique_ptr<DiskMemUsageSampler> sampler;
    DiskMemUsageSamplerTest()
        : transport(),
          sampler(std::make_unique<DiskMemUsageSampler>(transport.transport(), ".", DiskMemUsageSampler::Config(0.8, 0.8, 50ms, make_hw_info())))
    {
        sampler->add_transient_usage_provider(std::make_shared<MyProvider>(50, 200));
        sampler->add_transient_usage_provider(std::make_shared<MyProvider>(100, 150));
    }
    ~DiskMemUsageSamplerTest() {
        sampler.reset();
    }
    const DiskMemUsageFilter& filter() const { return sampler->writeFilter(); }
};

TEST_F(DiskMemUsageSamplerTest, resource_usage_is_sampled)
{
    // Poll for up to 20 seconds to get a sample.
    size_t i = 0;
    for (; i < static_cast<size_t>(20s / 50ms); ++i) {
        if (filter().get_transient_resource_usage().memory() > 0) {
            break;
        }
        std::this_thread::sleep_for(50ms);
    }
    LOG(info, "Polled %zu times (%zu ms) to get a sample", i, i * 50);
#ifdef __linux__
    // Anonymous resident memory used by current process is sampled.
    EXPECT_GT(filter().getMemoryStats().getAnonymousRss(), 0);
#else
    // Anonymous resident memory used by current process is not sampled.
    EXPECT_EQ(filter().getMemoryStats().getAnonymousRss(), 0);
#endif
    EXPECT_GT(filter().getDiskUsedSize(), 0);
    EXPECT_EQ(150, filter().get_transient_resource_usage().memory());
    EXPECT_EQ(150.0 / memory_size_bytes, filter().usageState().transient_memory_usage());
    EXPECT_EQ(350, filter().get_transient_resource_usage().disk());
    EXPECT_EQ(350.0 / disk_size_bytes, filter().usageState().transient_disk_usage());
}

GTEST_MAIN_RUN_ALL_TESTS()

