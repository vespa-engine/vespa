// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/fiddle.h>

using namespace vespalib::bits;

TEST(FiddleTest, require_that_mix_mixes) {
    EXPECT_EQ(0xffffffffu, mix(0xffffffffu, 0x00000000, 100));
    EXPECT_EQ(0xffffffffu, mix(0xffffffffu, 0x00000000, 33));

    EXPECT_EQ(0x00000000u, mix(0x00000000, 0xffffffffu, 100));
    EXPECT_EQ(0x00000000u, mix(0x00000000, 0xffffffffu, 33));

    EXPECT_EQ(0xffffffffu, mix(0xffffffffu, 0x00000000, 32));
    EXPECT_EQ(0xfffffffeu, mix(0xffffffffu, 0x00000000, 31));
    EXPECT_EQ(0xfffffffcu, mix(0xffffffffu, 0x00000000, 30));
    EXPECT_EQ(0xfffffff8u, mix(0xffffffffu, 0x00000000, 29));
    EXPECT_EQ(0xfffffff0u, mix(0xffffffffu, 0x00000000, 28));
    EXPECT_EQ(0xffffffe0u, mix(0xffffffffu, 0x00000000, 27));
    EXPECT_EQ(0xffffffc0u, mix(0xffffffffu, 0x00000000, 26));
    EXPECT_EQ(0xffffff80u, mix(0xffffffffu, 0x00000000, 25));
    EXPECT_EQ(0xffffff00u, mix(0xffffffffu, 0x00000000, 24));
    EXPECT_EQ(0xfffffe00u, mix(0xffffffffu, 0x00000000, 23));
    EXPECT_EQ(0xfffffc00u, mix(0xffffffffu, 0x00000000, 22));
    EXPECT_EQ(0xfffff800u, mix(0xffffffffu, 0x00000000, 21));
    EXPECT_EQ(0xfffff000u, mix(0xffffffffu, 0x00000000, 20));
    EXPECT_EQ(0xffffe000u, mix(0xffffffffu, 0x00000000, 19));
    EXPECT_EQ(0xffffc000u, mix(0xffffffffu, 0x00000000, 18));
    EXPECT_EQ(0xffff8000u, mix(0xffffffffu, 0x00000000, 17));
    EXPECT_EQ(0xffff0000u, mix(0xffffffffu, 0x00000000, 16));
    EXPECT_EQ(0xfffe0000u, mix(0xffffffffu, 0x00000000, 15));
    EXPECT_EQ(0xfffc0000u, mix(0xffffffffu, 0x00000000, 14));
    EXPECT_EQ(0xfff80000u, mix(0xffffffffu, 0x00000000, 13));
    EXPECT_EQ(0xfff00000u, mix(0xffffffffu, 0x00000000, 12));
    EXPECT_EQ(0xffe00000u, mix(0xffffffffu, 0x00000000, 11));
    EXPECT_EQ(0xffc00000u, mix(0xffffffffu, 0x00000000, 10));
    EXPECT_EQ(0xff800000u, mix(0xffffffffu, 0x00000000,  9));
    EXPECT_EQ(0xff000000u, mix(0xffffffffu, 0x00000000,  8));
    EXPECT_EQ(0xfe000000u, mix(0xffffffffu, 0x00000000,  7));
    EXPECT_EQ(0xfc000000u, mix(0xffffffffu, 0x00000000,  6));
    EXPECT_EQ(0xf8000000u, mix(0xffffffffu, 0x00000000,  5));
    EXPECT_EQ(0xf0000000u, mix(0xffffffffu, 0x00000000,  4));
    EXPECT_EQ(0xe0000000u, mix(0xffffffffu, 0x00000000,  3));
    EXPECT_EQ(0xc0000000u, mix(0xffffffffu, 0x00000000,  2));
    EXPECT_EQ(0x80000000u, mix(0xffffffffu, 0x00000000,  1));
    EXPECT_EQ(0x00000000u, mix(0xffffffffu, 0x00000000,  0));

    EXPECT_EQ(0x00000000u, mix(0x00000000, 0xffffffffu, 32));
    EXPECT_EQ(0x00000001u, mix(0x00000000, 0xffffffffu, 31));
    EXPECT_EQ(0x00000003u, mix(0x00000000, 0xffffffffu, 30));
    EXPECT_EQ(0x00000007u, mix(0x00000000, 0xffffffffu, 29));
    EXPECT_EQ(0x0000000fu, mix(0x00000000, 0xffffffffu, 28));
    EXPECT_EQ(0x0000001fu, mix(0x00000000, 0xffffffffu, 27));
    EXPECT_EQ(0x0000003fu, mix(0x00000000, 0xffffffffu, 26));
    EXPECT_EQ(0x0000007fu, mix(0x00000000, 0xffffffffu, 25));
    EXPECT_EQ(0x000000ffu, mix(0x00000000, 0xffffffffu, 24));
    EXPECT_EQ(0x000001ffu, mix(0x00000000, 0xffffffffu, 23));
    EXPECT_EQ(0x000003ffu, mix(0x00000000, 0xffffffffu, 22));
    EXPECT_EQ(0x000007ffu, mix(0x00000000, 0xffffffffu, 21));
    EXPECT_EQ(0x00000fffu, mix(0x00000000, 0xffffffffu, 20));
    EXPECT_EQ(0x00001fffu, mix(0x00000000, 0xffffffffu, 19));
    EXPECT_EQ(0x00003fffu, mix(0x00000000, 0xffffffffu, 18));
    EXPECT_EQ(0x00007fffu, mix(0x00000000, 0xffffffffu, 17));
    EXPECT_EQ(0x0000ffffu, mix(0x00000000, 0xffffffffu, 16));
    EXPECT_EQ(0x0001ffffu, mix(0x00000000, 0xffffffffu, 15));
    EXPECT_EQ(0x0003ffffu, mix(0x00000000, 0xffffffffu, 14));
    EXPECT_EQ(0x0007ffffu, mix(0x00000000, 0xffffffffu, 13));
    EXPECT_EQ(0x000fffffu, mix(0x00000000, 0xffffffffu, 12));
    EXPECT_EQ(0x001fffffu, mix(0x00000000, 0xffffffffu, 11));
    EXPECT_EQ(0x003fffffu, mix(0x00000000, 0xffffffffu, 10));
    EXPECT_EQ(0x007fffffu, mix(0x00000000, 0xffffffffu,  9));
    EXPECT_EQ(0x00ffffffu, mix(0x00000000, 0xffffffffu,  8));
    EXPECT_EQ(0x01ffffffu, mix(0x00000000, 0xffffffffu,  7));
    EXPECT_EQ(0x03ffffffu, mix(0x00000000, 0xffffffffu,  6));
    EXPECT_EQ(0x07ffffffu, mix(0x00000000, 0xffffffffu,  5));
    EXPECT_EQ(0x0fffffffu, mix(0x00000000, 0xffffffffu,  4));
    EXPECT_EQ(0x1fffffffu, mix(0x00000000, 0xffffffffu,  3));
    EXPECT_EQ(0x3fffffffu, mix(0x00000000, 0xffffffffu,  2));
    EXPECT_EQ(0x7fffffffu, mix(0x00000000, 0xffffffffu,  1));
    EXPECT_EQ(0xffffffffu, mix(0x00000000, 0xffffffffu,  0));
}

TEST(FiddleTest, require_that_leading_zeros_are_counted_correctly) {
    EXPECT_EQ(32u, leading_zeros(0x00000000u));
    EXPECT_EQ(31u, leading_zeros(0x00000001u));
    EXPECT_EQ(30u, leading_zeros(0x00000003u));
    EXPECT_EQ(29u, leading_zeros(0x00000007u));
    EXPECT_EQ(28u, leading_zeros(0x0000000fu));
    EXPECT_EQ(27u, leading_zeros(0x0000001fu));
    EXPECT_EQ(26u, leading_zeros(0x0000003fu));
    EXPECT_EQ(25u, leading_zeros(0x0000007fu));
    EXPECT_EQ(24u, leading_zeros(0x000000ffu));
    EXPECT_EQ(23u, leading_zeros(0x000001ffu));
    EXPECT_EQ(22u, leading_zeros(0x000003ffu));
    EXPECT_EQ(21u, leading_zeros(0x000007ffu));
    EXPECT_EQ(20u, leading_zeros(0x00000fffu));
    EXPECT_EQ(19u, leading_zeros(0x00001fffu));
    EXPECT_EQ(18u, leading_zeros(0x00003fffu));
    EXPECT_EQ(17u, leading_zeros(0x00007fffu));
    EXPECT_EQ(16u, leading_zeros(0x0000ffffu));
    EXPECT_EQ(15u, leading_zeros(0x0001ffffu));
    EXPECT_EQ(14u, leading_zeros(0x0003ffffu));
    EXPECT_EQ(13u, leading_zeros(0x0007ffffu));
    EXPECT_EQ(12u, leading_zeros(0x000fffffu));
    EXPECT_EQ(11u, leading_zeros(0x001fffffu));
    EXPECT_EQ(10u, leading_zeros(0x003fffffu));
    EXPECT_EQ( 9u, leading_zeros(0x007fffffu));
    EXPECT_EQ( 8u, leading_zeros(0x00ffffffu));
    EXPECT_EQ( 7u, leading_zeros(0x01ffffffu));
    EXPECT_EQ( 6u, leading_zeros(0x03ffffffu));
    EXPECT_EQ( 5u, leading_zeros(0x07ffffffu));
    EXPECT_EQ( 4u, leading_zeros(0x0fffffffu));
    EXPECT_EQ( 3u, leading_zeros(0x1fffffffu));
    EXPECT_EQ( 2u, leading_zeros(0x3fffffffu));
    EXPECT_EQ( 1u, leading_zeros(0x7fffffffu));
    EXPECT_EQ( 0u, leading_zeros(0xffffffffu));

    EXPECT_EQ(8u, leading_zeros(0x00ffff00u));
    EXPECT_EQ(8u, leading_zeros(0x00fffe00u));
    EXPECT_EQ(8u, leading_zeros(0x00fffc00u));
    EXPECT_EQ(8u, leading_zeros(0x00fff800u));
    EXPECT_EQ(8u, leading_zeros(0x00fff000u));
    EXPECT_EQ(8u, leading_zeros(0x00ffe000u));
    EXPECT_EQ(8u, leading_zeros(0x00ffc000u));
    EXPECT_EQ(8u, leading_zeros(0x00ff8000u));
    EXPECT_EQ(8u, leading_zeros(0x00ff0000u));
    EXPECT_EQ(8u, leading_zeros(0x00fe0000u));
    EXPECT_EQ(8u, leading_zeros(0x00fc0000u));
    EXPECT_EQ(8u, leading_zeros(0x00f80000u));
    EXPECT_EQ(8u, leading_zeros(0x00f00000u));
    EXPECT_EQ(8u, leading_zeros(0x00e00000u));
    EXPECT_EQ(8u, leading_zeros(0x00c00000u));
    EXPECT_EQ(8u, leading_zeros(0x00800000u));
}


void verify_range_split(uint32_t min, uint32_t max,
                        uint32_t expect_suffix,
                        uint32_t expect_first_max,
                        uint32_t expect_last_min)
{
    uint32_t first_max = 0;
    uint32_t last_min = 0;
    EXPECT_EQ(expect_suffix, split_range(min, max, first_max, last_min));
    EXPECT_EQ(expect_first_max, first_max);
    EXPECT_EQ(expect_last_min, last_min);
}

TEST(FiddleTest, require_that_ranges_are_split_correctly) {
    GTEST_DO(verify_range_split(0, 0, 0, 0, 0));
    GTEST_DO(verify_range_split(503, 503, 0, 503, 503));

    GTEST_DO(verify_range_split(0xc5, 0xf7, 6, 0xdf, 0xe0));
}

GTEST_MAIN_RUN_ALL_TESTS()
