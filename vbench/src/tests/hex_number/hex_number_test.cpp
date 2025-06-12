// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/gtest/gtest.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST(HexNumberTest, hex_number) {
    EXPECT_EQ(0u, HexNumber("").value());
    EXPECT_EQ(0u, HexNumber("").length());
    EXPECT_EQ(0u, HexNumber("000").value());
    EXPECT_EQ(3u, HexNumber("000").length());
    EXPECT_EQ(0x1ee7u, HexNumber("1ee7").value());
    EXPECT_EQ(0x0123456789abcdefU, HexNumber("0123456789abcdef").value());
    EXPECT_EQ(0xfedcba9876543210U, HexNumber("FEDCBA9876543210").value());
    EXPECT_EQ(16u, HexNumber("0123456789ABCDEF").length());
    EXPECT_EQ(0xdefu, HexNumber("defghijk").value());
    EXPECT_EQ(3u, HexNumber("defghijk").length());
}

GTEST_MAIN_RUN_ALL_TESTS()
