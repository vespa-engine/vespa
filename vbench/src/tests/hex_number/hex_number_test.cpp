// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("hex number") {
    EXPECT_EQUAL(0u, HexNumber("").value());
    EXPECT_EQUAL(0u, HexNumber("").length());
    EXPECT_EQUAL(0u, HexNumber("000").value());
    EXPECT_EQUAL(3u, HexNumber("000").length());
    EXPECT_EQUAL(0x1ee7u, HexNumber("1ee7").value());
    EXPECT_EQUAL(0x0123456789abcdefU, HexNumber("0123456789abcdef").value());
    EXPECT_EQUAL(0xfedcba9876543210U, HexNumber("FEDCBA9876543210").value());
    EXPECT_EQUAL(16u, HexNumber("0123456789ABCDEF").length());
    EXPECT_EQUAL(0xdefu, HexNumber("defghijk").value());
    EXPECT_EQUAL(3u, HexNumber("defghijk").length());
}

TEST_MAIN() { TEST_RUN_ALL(); }
