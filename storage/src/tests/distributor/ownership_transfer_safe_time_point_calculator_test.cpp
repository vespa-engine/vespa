// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/ownership_transfer_safe_time_point_calculator.h>
#include <vespa/vdstestlib/cppunit/macros.h>

template <typename Clock, typename Duration>
std::ostream& operator<<(std::ostream& os,
                         std::chrono::time_point<Clock, Duration> t)
{
    os << std::chrono::duration_cast<std::chrono::milliseconds>(
            t.time_since_epoch()).count() << "ms";
    return os;
}

namespace storage {
namespace distributor {

struct OwnershipTransferSafeTimePointCalculatorTest : CppUnit::TestFixture {
    void generated_safe_time_point_rounds_up_to_nearest_second();
    void zero_clock_skew_returns_epoch();

    CPPUNIT_TEST_SUITE(OwnershipTransferSafeTimePointCalculatorTest);
    CPPUNIT_TEST(generated_safe_time_point_rounds_up_to_nearest_second);
    CPPUNIT_TEST(zero_clock_skew_returns_epoch);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(OwnershipTransferSafeTimePointCalculatorTest);


using CalcType = OwnershipTransferSafeTimePointCalculator;
using Clock = CalcType::Clock;
using TimePoint = CalcType::TimePoint;

using namespace std::literals::chrono_literals;

void OwnershipTransferSafeTimePointCalculatorTest::generated_safe_time_point_rounds_up_to_nearest_second() {
    CPPUNIT_ASSERT_EQUAL(TimePoint(6s),
                         CalcType(1s).safeTimePoint(TimePoint(4001ms)));
    CPPUNIT_ASSERT_EQUAL(TimePoint(6s),
                         CalcType(1s).safeTimePoint(TimePoint(4999ms)));
    CPPUNIT_ASSERT_EQUAL(TimePoint(6s),
                         CalcType(1s).safeTimePoint(TimePoint(4000ms)));
    CPPUNIT_ASSERT_EQUAL(TimePoint(7s),
                         CalcType(2s).safeTimePoint(TimePoint(4001ms)));
    CPPUNIT_ASSERT_EQUAL(TimePoint(7s),
                         CalcType(2s).safeTimePoint(TimePoint(4999ms)));
}

void OwnershipTransferSafeTimePointCalculatorTest::zero_clock_skew_returns_epoch() {
    CPPUNIT_ASSERT_EQUAL(TimePoint(0s),
                         CalcType(0s).safeTimePoint(TimePoint(4001ms)));
}

}
}
