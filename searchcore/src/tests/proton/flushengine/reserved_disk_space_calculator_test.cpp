// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/flushengine/reserved_disk_space_calculator.h>
#include <vespa/vespalib/gtest/gtest.h>

using proton::flushengine::ReservedDiskSpaceCalculator;
using searchcorespi::IFlushTarget;

class ReservedDiskSpaceCalculatorTest : public testing::Test {
protected:
    ReservedDiskSpaceCalculatorTest();
    ~ReservedDiskSpaceCalculatorTest() override;
    uint64_t calc_reserved_disk_space(size_t concurrent, std::vector<IFlushTarget::DiskGain> gains);
};

ReservedDiskSpaceCalculatorTest::ReservedDiskSpaceCalculatorTest()
    : testing::Test()
{
}

ReservedDiskSpaceCalculatorTest::~ReservedDiskSpaceCalculatorTest() = default;

uint64_t
ReservedDiskSpaceCalculatorTest::calc_reserved_disk_space(size_t concurrent, std::vector<IFlushTarget::DiskGain> gains)
{
    ReservedDiskSpaceCalculator calc(concurrent);
    for (auto& gain : gains) {
        calc.track_disk_gain(gain);
    }
    return calc.get_reserved_disk();
}

TEST_F(ReservedDiskSpaceCalculatorTest, calc_reserved_disk_space)
{
    EXPECT_EQ(0, calc_reserved_disk_space(1, {}));
    EXPECT_EQ(20, calc_reserved_disk_space(1, {{20, 20}}));
    EXPECT_EQ(30, calc_reserved_disk_space(1, {{10, 20}}));
    /*
     * Reserved disk space for growth is calculated for all targets.
     * Reserved disk space for flush limited by the total number of flush threads, using the targets with the largest
     * reported disk space after flush (which is considered reserved disk space for flush for that target).
     */
    EXPECT_EQ(200, calc_reserved_disk_space(1, {{20, 20}, {200, 200}}));
    EXPECT_EQ(210, calc_reserved_disk_space(1, {{10, 20}, {200, 200}}));
    EXPECT_EQ(300, calc_reserved_disk_space(1, {{20, 20}, {100, 200}}));
    EXPECT_EQ(310, calc_reserved_disk_space(1, {{10, 20}, {100, 200}}));
    EXPECT_EQ(220, calc_reserved_disk_space(2, {{20, 20}, {200, 200}}));
    EXPECT_EQ(230, calc_reserved_disk_space(2, {{10, 20}, {200, 200}}));
    EXPECT_EQ(320, calc_reserved_disk_space(2, {{20, 20}, {100, 200}}));
    EXPECT_EQ(330, calc_reserved_disk_space(2, {{10, 20}, {100, 200}}));
    EXPECT_EQ(3110, calc_reserved_disk_space(1, {{10, 20}, {100, 200}, {1000, 2000}}));
    EXPECT_EQ(3310, calc_reserved_disk_space(2, {{10, 20}, {100, 200}, {1000, 2000}}));
    EXPECT_EQ(3330, calc_reserved_disk_space(3, {{10, 20}, {100, 200}, {1000, 2000}}));
    EXPECT_EQ(2330, calc_reserved_disk_space(3, {{10, 20}, {100, 200}, {2000, 2000}}));
}
