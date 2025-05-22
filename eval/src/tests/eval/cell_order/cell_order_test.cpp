// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/cell_order.h>
#include <vespa/vespalib/gtest/gtest.h>

using vespalib::BFloat16;
using namespace vespalib::eval;

TEST(CellOrderTest, enum_to_string)
{
    EXPECT_EQ(as_string(CellOrder::MAX), "max");
    EXPECT_EQ(as_string(CellOrder::MIN), "min");
}

TEST(CellOrderTest, string_to_enum)
{
    EXPECT_FALSE(cell_order_from_string("avg").has_value());
    EXPECT_EQ(cell_order_from_string("max").value(), CellOrder::MAX);
    EXPECT_EQ(cell_order_from_string("min").value(), CellOrder::MIN);
}

TEST(CellOrderTest, sort_max)
{
    float my_nan = std::numeric_limits<float>::quiet_NaN();

    std::vector<Int8Float> int8_list     {5, 8, -2, 0};
    std::vector<BFloat16>  bfloat16_list {5, my_nan, 8, my_nan, -2, my_nan, 0};
    std::vector<float>     float_list    {5, my_nan, 8, my_nan, -2, my_nan, 0};
    std::vector<double>    double_list   {5, my_nan, 8, my_nan, -2, my_nan, 0};

    std::vector<double> expect {8, 5, 0, -2};

    std::sort(int8_list.begin(), int8_list.end(), CellOrderMAX{});
    std::sort(bfloat16_list.begin(), bfloat16_list.end(), CellOrderMAX{});
    std::sort(float_list.begin(), float_list.end(), CellOrderMAX{});
    std::sort(double_list.begin(), double_list.end(), CellOrderMAX{});

    for (size_t i = 0; i < float_list.size(); ++i) {
        if (i < int8_list.size()) {
            EXPECT_EQ(int8_list[i],     expect[i]);
            EXPECT_EQ(bfloat16_list[i], expect[i]);
            EXPECT_EQ(float_list[i],    expect[i]);
            EXPECT_EQ(double_list[i],   expect[i]);
        } else {
            EXPECT_TRUE(std::isnan(bfloat16_list[i]));
            EXPECT_TRUE(std::isnan(float_list[i]));
            EXPECT_TRUE(std::isnan(double_list[i]));
        }
    }
}

TEST(CellOrderTest, sort_min)
{
    float my_nan = std::numeric_limits<float>::quiet_NaN();

    std::vector<Int8Float> int8_list     {5, 8, -2, 0};
    std::vector<BFloat16>  bfloat16_list {5, my_nan, 8, my_nan, -2, my_nan, 0};
    std::vector<float>     float_list    {5, my_nan, 8, my_nan, -2, my_nan, 0};
    std::vector<double>    double_list   {5, my_nan, 8, my_nan, -2, my_nan, 0};

    std::vector<double> expect {-2, 0, 5, 8};

    std::sort(int8_list.begin(), int8_list.end(), CellOrderMIN{});
    std::sort(bfloat16_list.begin(), bfloat16_list.end(), CellOrderMIN{});
    std::sort(float_list.begin(), float_list.end(), CellOrderMIN{});
    std::sort(double_list.begin(), double_list.end(), CellOrderMIN{});

    for (size_t i = 0; i < float_list.size(); ++i) {
        SCOPED_TRACE(std::to_string(i));
        if (i < int8_list.size()) {
            EXPECT_EQ(int8_list[i],     expect[i]);
            EXPECT_EQ(bfloat16_list[i], expect[i]);
            EXPECT_EQ(float_list[i],    expect[i]);
            EXPECT_EQ(double_list[i],   expect[i]);
        } else {
            EXPECT_TRUE(std::isnan(bfloat16_list[i]));
            EXPECT_TRUE(std::isnan(float_list[i]));
            EXPECT_TRUE(std::isnan(double_list[i]));
        }
    }
}

GTEST_MAIN_RUN_ALL_TESTS()
