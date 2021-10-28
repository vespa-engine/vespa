// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/index/field_length_calculator.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::index::FieldLengthCalculator;

namespace search::index {

namespace {

// Arithmetic average of arithmetic sequence 1, 2, ... , samples
double arith_avg(uint32_t samples) {
    return static_cast<double>(samples + 1) / 2;
}

}

TEST(FieldLengthCalculatorTest, empty_is_zero)
{
    FieldLengthCalculator calc;
    EXPECT_EQ(0.0, calc.get_average_field_length());
    EXPECT_EQ(0, calc.get_num_samples());
}

TEST(FieldLengthCalculatorTest, startup_is_average)
{
    FieldLengthCalculator calc;
    calc.add_field_length(3);
    EXPECT_DOUBLE_EQ(3.0, calc.get_average_field_length());
    EXPECT_EQ(1, calc.get_num_samples());
    calc.add_field_length(4);
    EXPECT_DOUBLE_EQ(3.5, calc.get_average_field_length());
    EXPECT_EQ(2, calc.get_num_samples());
    calc.add_field_length(7);
    EXPECT_DOUBLE_EQ((3 + 4 + 7)/3.0, calc.get_average_field_length());
    EXPECT_EQ(3, calc.get_num_samples());
    calc.add_field_length(9);
    EXPECT_DOUBLE_EQ((3 + 4 + 7 + 9)/4.0, calc.get_average_field_length());
    EXPECT_EQ(4, calc.get_num_samples());
}

TEST(FieldLengthCalculatorTest, average_until_max_num_samples)
{
    const uint32_t max_num_samples = 5;
    FieldLengthCalculator calc(0.0, 0, max_num_samples);
    static constexpr double epsilon = 0.000000001; // Allowed difference
    for (uint32_t i = 0; i + 1 < max_num_samples; ++i) {
        calc.add_field_length(i + 1);
    }
    // Arithmetic average
    EXPECT_NEAR(arith_avg(max_num_samples - 1), calc.get_average_field_length(), epsilon);
    EXPECT_EQ(max_num_samples - 1, calc.get_num_samples());
    calc.add_field_length(max_num_samples);
    // Arithmetic average
    EXPECT_NEAR(arith_avg(max_num_samples), calc.get_average_field_length(), epsilon);
    EXPECT_EQ(max_num_samples, calc.get_num_samples());
    calc.add_field_length(max_num_samples + 1);
    // No longer arithmetic average
    EXPECT_LT(arith_avg(max_num_samples + 1), calc.get_average_field_length());
    // Switched to exponential decay
    EXPECT_NEAR((arith_avg(max_num_samples) * (max_num_samples - 1) + max_num_samples + 1) / max_num_samples, calc.get_average_field_length(), epsilon);
    EXPECT_EQ(max_num_samples, calc.get_num_samples());
}

TEST(FieldLengthCalculatorTest, calculator_can_return_info_object)
{
    FieldLengthCalculator calc(3, 5);
    auto info = calc.get_info();
    EXPECT_EQ(3, info.get_average_field_length());
    EXPECT_EQ(5, info.get_num_samples());
}

}

GTEST_MAIN_RUN_ALL_TESTS()
