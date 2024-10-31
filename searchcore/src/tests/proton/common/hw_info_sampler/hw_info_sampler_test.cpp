// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-hwinfo.h>
#include <vespa/config/print/fileconfigwriter.h>
#include <vespa/searchcore/proton/common/hw_info_sampler.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/size_literals.h>

using proton::HwInfoSampler;
using search::test::DirectoryHandler;
using vespa::config::search::core::HwinfoConfig;
using vespa::config::search::core::HwinfoConfigBuilder;

using Clock = std::chrono::system_clock;
using Config = HwInfoSampler::Config;

namespace {

const std::string test_dir = "temp";
constexpr uint64_t sampleLen = 40_Mi;
constexpr bool sharedDisk = false;

long time_point_to_long(Clock::time_point tp)
{
    return std::chrono::duration_cast<std::chrono::seconds>(tp.time_since_epoch()).count();
}

}

class HwInfoSamplerTest : public ::testing::Test {
protected:
    DirectoryHandler _dirHandler;

    HwInfoSamplerTest();
    ~HwInfoSamplerTest() override;
    static void SetUpTestSuite();

    void writeConfig(const HwinfoConfig &config) {
        config::FileConfigWriter writer(test_dir + "/hwinfo.cfg");
        ASSERT_TRUE(writer.write(config));
    }

};

HwInfoSamplerTest::HwInfoSamplerTest()
    : ::testing::Test(),
      _dirHandler(test_dir)
{
}

HwInfoSamplerTest::~HwInfoSamplerTest() = default;

void
HwInfoSamplerTest::SetUpTestSuite()
{
    std::filesystem::remove_all(std::filesystem::path(test_dir));
}

TEST_F(HwInfoSamplerTest, Test_that_hw_info_sampler_uses_override_info)
{
    Config samplerCfg(0, 75.0, 100.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQ(75.0, sampler.diskWriteSpeed());
    EXPECT_NE(0l, time_point_to_long(sampler.sampleTime()));
    EXPECT_TRUE(sampler.hwInfo().disk().slow());
}

TEST_F(HwInfoSamplerTest, Test_that_hw_info_sampler_uses_saved_info)
{
    HwinfoConfigBuilder builder;
    builder.disk.writespeed = 72.0;
    builder.disk.sampletime = time_point_to_long(Clock::now());
    writeConfig(builder);
    Config samplerCfg(0, 0.0, 70.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQ(builder.disk.writespeed, sampler.diskWriteSpeed());
    EXPECT_EQ(builder.disk.sampletime, time_point_to_long(sampler.sampleTime()));
    EXPECT_FALSE(sampler.hwInfo().disk().slow());
}

TEST_F(HwInfoSamplerTest, Test_that_hw_info_sampler_can_sample_disk_write_speed)
{
    Config samplerCfg(0, 0.0, 100.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    ASSERT_NE(0.0, sampler.diskWriteSpeed());
    ASSERT_NE(0l, time_point_to_long(sampler.sampleTime()));
    HwInfoSampler sampler2(test_dir, samplerCfg);
    EXPECT_NEAR(sampler.diskWriteSpeed(), sampler2.diskWriteSpeed(), 0.1);
    EXPECT_EQ(time_point_to_long(sampler.sampleTime()),
                 time_point_to_long(sampler2.sampleTime()));
}

TEST_F(HwInfoSamplerTest, require_that_disk_size_can_be_specified)
{
    Config samplerCfg(1024, 1.0, 0.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQ(1024u, sampler.hwInfo().disk().sizeBytes());
}

TEST_F(HwInfoSamplerTest, require_that_disk_size_can_be_sampled)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_GT(sampler.hwInfo().disk().sizeBytes(), 0u);
}

TEST_F(HwInfoSamplerTest, require_that_memory_size_can_be_specified)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 1024, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQ(1024u, sampler.hwInfo().memory().sizeBytes());
}

TEST_F(HwInfoSamplerTest, require_that_memory_size_can_be_sampled)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_GT(sampler.hwInfo().memory().sizeBytes(), 0u);
}

TEST_F(HwInfoSamplerTest, require_that_num_cpu_cores_can_be_specified)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 0, 8);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQ(8u, sampler.hwInfo().cpu().cores());
}

TEST_F(HwInfoSamplerTest, require_that_num_cpu_cores_can_be_sampled)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_GT(sampler.hwInfo().cpu().cores(), 0u);
}

GTEST_MAIN_RUN_ALL_TESTS()
