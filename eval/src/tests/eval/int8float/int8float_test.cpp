// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/eval/eval/int8float.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cmath>

using namespace vespalib;
using namespace vespalib::eval;

static std::vector<float> simple_values = {
    0.0, 1.0, -1.0, -17.0, 42.0, 127.0, -128.0
};

TEST(Int8FloatTest, normal_usage) {
    EXPECT_EQ(sizeof(float), 4);
    EXPECT_EQ(sizeof(Int8Float), 1);
    Int8Float answer = 42;
    double fortytwo = answer;
    EXPECT_EQ(fortytwo, 42);
    for (float value : simple_values) {
        Int8Float b = value;
        float recover = b;
        EXPECT_EQ(value, recover);
    }
    // undefined behavior here:
    Int8Float b1 = 128.0;
    EXPECT_NE(float(b1), 128.0);
    Int8Float b2 = -129.0;
    EXPECT_NE(float(b2), -129.0);
}

TEST(Int8FloatTest, with_nbostream) {
    nbostream buf;
    for (Int8Float value : simple_values) {
        buf << value;
    }
    for (float value : simple_values) {
        Int8Float stored;
        buf >> stored;
        EXPECT_EQ(float(stored), value);
    }
}

TEST(Int8FloatTest, traits_check) {
        EXPECT_TRUE(std::is_trivially_constructible<Int8Float>::value);
        EXPECT_TRUE(std::is_trivially_move_constructible<Int8Float>::value);
        EXPECT_TRUE(std::is_trivially_default_constructible<Int8Float>::value);
        EXPECT_TRUE((std::is_trivially_assignable<Int8Float,Int8Float>::value));
        EXPECT_TRUE(std::is_trivially_move_assignable<Int8Float>::value);
        EXPECT_TRUE(std::is_trivially_copy_assignable<Int8Float>::value);
        EXPECT_TRUE(std::is_trivially_copyable<Int8Float>::value);
        EXPECT_TRUE(std::is_trivially_destructible<Int8Float>::value);
        EXPECT_TRUE(std::is_trivial<Int8Float>::value);
        EXPECT_TRUE(std::is_swappable<Int8Float>::value);
        EXPECT_TRUE(std::has_unique_object_representations<Int8Float>::value);
}

GTEST_MAIN_RUN_ALL_TESTS()
