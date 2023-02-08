// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/ownership_transfer_safe_time_point_calculator.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::distributor {

using CalcType = OwnershipTransferSafeTimePointCalculator;
using TimePoint = vespalib::system_time;

using namespace std::literals::chrono_literals;

TEST(OwnershipTransferSafeTimePointCalculatorTest, generated_safe_time_point_rounds_up_to_nearest_second) {
    EXPECT_EQ(TimePoint(6s), CalcType(1s).safeTimePoint(TimePoint(4001ms)));
    EXPECT_EQ(TimePoint(6s), CalcType(1s).safeTimePoint(TimePoint(4999ms)));
    EXPECT_EQ(TimePoint(6s), CalcType(1s).safeTimePoint(TimePoint(4000ms)));
    EXPECT_EQ(TimePoint(7s), CalcType(2s).safeTimePoint(TimePoint(4001ms)));
    EXPECT_EQ(TimePoint(7s), CalcType(2s).safeTimePoint(TimePoint(4999ms)));
}

TEST(OwnershipTransferSafeTimePointCalculatorTest, zero_clock_skew_returns_epoch) {
    EXPECT_EQ(TimePoint(0s), CalcType(0s).safeTimePoint(TimePoint(4001ms)));
}

}
