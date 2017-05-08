// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>

#include <vespa/config/print/fileconfigwriter.h>
#include <vespa/searchcore/config/config-hwinfo.h>
#include <vespa/searchcore/proton/common/hw_info_sampler.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/testkit/testapp.h>

using proton::HwInfoSampler;
using search::test::DirectoryHandler;
using vespa::config::search::core::HwinfoConfig;
using vespa::config::search::core::HwinfoConfigBuilder;

using Clock = std::chrono::system_clock;
using Config = HwInfoSampler::Config;

namespace {

const vespalib::string test_dir = "temp";
constexpr uint64_t sampleLen = 1024 * 1024 * 40;

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
    Config samplerCfg(75.0, 100.0, sampleLen);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQUAL(75.0, sampler.diskWriteSpeed());
    EXPECT_NOT_EQUAL(0, time_point_to_long(sampler.sampleTime()));
    EXPECT_TRUE(sampler.hwInfo().slowDisk());
}

TEST_F("Test that hw_info_sampler uses saved info", Fixture)
{
    HwinfoConfigBuilder builder;
    builder.disk.writespeed = 72.0;
    builder.disk.sampletime = time_point_to_long(Clock::now());
    f.writeConfig(builder);
    Config samplerCfg(0.0, 70.0, sampleLen);
    HwInfoSampler sampler(test_dir, samplerCfg);
    EXPECT_EQUAL(builder.disk.writespeed, sampler.diskWriteSpeed());
    EXPECT_EQUAL(builder.disk.sampletime, time_point_to_long(sampler.sampleTime()));
    EXPECT_FALSE(sampler.hwInfo().slowDisk());
}

TEST_F("Test that hw_info_sampler can sample disk write speed", Fixture)
{
    Config samplerCfg(0.0, 100.0, sampleLen);
    HwInfoSampler sampler(test_dir, samplerCfg);
    ASSERT_NOT_EQUAL(0.0, sampler.diskWriteSpeed());
    ASSERT_NOT_EQUAL(0, time_point_to_long(sampler.sampleTime()));
    HwInfoSampler sampler2(test_dir, samplerCfg);
    EXPECT_APPROX(sampler.diskWriteSpeed(), sampler2.diskWriteSpeed(), 0.1);
    EXPECT_EQUAL(time_point_to_long(sampler.sampleTime()),
                 time_point_to_long(sampler2.sampleTime()));
}

TEST_MAIN()
{
    vespalib::rmdir(test_dir, true);
    TEST_RUN_ALL();
}
