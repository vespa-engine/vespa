// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcore/proton/flushengine/reserved_disk_space_calculator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <limits>

using proton::flushengine::ReservedDiskSpaceCalculator;
using searchcorespi::IFlushTarget;

class ReservedDiskSpaceCalculatorTest : public testing::Test {
protected:
    IFlushTarget::Type      _type;
    IFlushTarget::Component _component;
    uint64_t                _max_summary_file_size;
    ReservedDiskSpaceCalculatorTest();
    ~ReservedDiskSpaceCalculatorTest() override;
    uint64_t calc_reserved_disk_space(size_t concurrent, std::vector<IFlushTarget::DiskGain> gains);
};

ReservedDiskSpaceCalculatorTest::ReservedDiskSpaceCalculatorTest()
    : testing::Test(),
      _type(IFlushTarget::Type::OTHER),
      _component(IFlushTarget::Component::OTHER),
      _max_summary_file_size(std::numeric_limits<uint64_t>::max())
{
}

ReservedDiskSpaceCalculatorTest::~ReservedDiskSpaceCalculatorTest() = default;

uint64_t
ReservedDiskSpaceCalculatorTest::calc_reserved_disk_space(size_t concurrent, std::vector<IFlushTarget::DiskGain> gains)
{
    ReservedDiskSpaceCalculator calc(concurrent, _max_summary_file_size);
    for (auto& gain : gains) {
        calc.track_disk_gain(gain, _type, _component);
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

TEST_F(ReservedDiskSpaceCalculatorTest, capped_reserved_flush_size_for_document_store_compaction)
{
    _max_summary_file_size = 1000000;
    EXPECT_EQ(4000000, calc_reserved_disk_space(1, {{4000000, 4000000}}));
    EXPECT_EQ(5000000, calc_reserved_disk_space(1, {{3000000, 4000000}}));
    _type = IFlushTarget::Type::GC;
    _component = IFlushTarget::Component::DOCUMENT_STORE;
    EXPECT_EQ(1000000, calc_reserved_disk_space(1, {{4000000, 4000000}}));
    EXPECT_EQ(2000000, calc_reserved_disk_space(1, {{3000000, 4000000}}));
}
