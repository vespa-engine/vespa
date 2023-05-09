// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-hwinfo.h>
#include <vespa/config/print/fileconfigwriter.h>
#include <vespa/searchcore/proton/common/hw_info_sampler.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/testkit/testapp.h>

using proton::HwInfoSampler;
using search::test::DirectoryHandler;
using vespa::config::search::core::HwinfoConfig;
using vespa::config::search::core::HwinfoConfigBuilder;

using Clock = std::chrono::system_clock;
using Config = HwInfoSampler::Config;

namespace {

const vespalib::string test_dir = "temp";
constexpr uint64_t sampleLen = 40_Mi;
constexpr bool sharedDisk = false;

long time_point_to_long(Clock::time_point tp)
{
    return std::chrono::duration_cast<std::chrono::seconds>(tp.time_since_epoch()).count();
}

}

struct Fixture
{
    DirectoryHandler _dirHandler;

    Fixture()
        : _dirHandler(test_dir)
    {
    }

    void writeConfig(const HwinfoConfig &config) {
        config::FileConfigWriter writer(test_dir + "/hwinfo.cfg");
        ASSERT_TRUE(writer.write(config));
    }

};

TEST_F("Test that hw_info_sampler uses override info", Fixture)
{
    Config samplerCfg(0, 75.0, 100.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQUAL(75.0, sampler.diskWriteSpeed());
    EXPECT_NOT_EQUAL(0, time_point_to_long(sampler.sampleTime()));
    EXPECT_TRUE(sampler.hwInfo().disk().slow());
}

TEST_F("Test that hw_info_sampler uses saved info", Fixture)
{
    HwinfoConfigBuilder builder;
    builder.disk.writespeed = 72.0;
    builder.disk.sampletime = time_point_to_long(Clock::now());
    f.writeConfig(builder);
    Config samplerCfg(0, 0.0, 70.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQUAL(builder.disk.writespeed, sampler.diskWriteSpeed());
    EXPECT_EQUAL(builder.disk.sampletime, time_point_to_long(sampler.sampleTime()));
    EXPECT_FALSE(sampler.hwInfo().disk().slow());
}

TEST_F("Test that hw_info_sampler can sample disk write speed", Fixture)
{
    Config samplerCfg(0, 0.0, 100.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    ASSERT_NOT_EQUAL(0.0, sampler.diskWriteSpeed());
    ASSERT_NOT_EQUAL(0, time_point_to_long(sampler.sampleTime()));
    HwInfoSampler sampler2(test_dir, samplerCfg);
    EXPECT_APPROX(sampler.diskWriteSpeed(), sampler2.diskWriteSpeed(), 0.1);
    EXPECT_EQUAL(time_point_to_long(sampler.sampleTime()),
                 time_point_to_long(sampler2.sampleTime()));
}

TEST_F("require that disk size can be specified", Fixture)
{
    Config samplerCfg(1024, 1.0, 0.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQUAL(1024u, sampler.hwInfo().disk().sizeBytes());
}

TEST_F("require that disk size can be sampled", Fixture)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_GREATER(sampler.hwInfo().disk().sizeBytes(), 0u);
}

TEST_F("require that memory size can be specified", Fixture)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 1024, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQUAL(1024u, sampler.hwInfo().memory().sizeBytes());
}

TEST_F("require that memory size can be sampled", Fixture)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_GREATER(sampler.hwInfo().memory().sizeBytes(), 0u);
}

TEST_F("require that num cpu cores can be specified", Fixture)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 0, 8);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQUAL(8u, sampler.hwInfo().cpu().cores());
}

TEST_F("require that num cpu cores can be sampled", Fixture)
{
    Config samplerCfg(0, 1.0, 0.0, sampleLen, sharedDisk, 0, 0);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_GREATER(sampler.hwInfo().cpu().cores(), 0u);
}

TEST_MAIN()
{
    std::filesystem::remove_all(std::filesystem::path(test_dir));
    TEST_RUN_ALL();
}
