// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("lowercase_test");
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>
#include <vespa/vespalib/text/lowercase.h>
#include <iostream>
#include <fstream>

using namespace vespalib;

TEST(LowercaseTest, test_basic_lowercase)
{
    EXPECT_EQ('a', LowerCase::convert('A'));
    EXPECT_EQ((int8_t)'a', LowerCase::convert((int8_t)'A'));
    EXPECT_EQ((uint8_t)'a', LowerCase::convert((uint8_t)'A'));

    std::ifstream yellData(TEST_PATH("yell-want.dat"));

    EXPECT_TRUE(yellData.good());

    while (yellData.good()) {
        uint32_t hi=0, lo=0;
        yellData >> hi >> lo;
        EXPECT_EQ(lo, LowerCase::convert(hi));
        if (lo != LowerCase::convert(hi)) {
            printf("lc(%04X) -> %04X\n", hi, LowerCase::convert(hi));
            printf("yell(%04X) -> %04X\n", hi, lo);
        }
        // printf("lowercase( %d )= %d\n", hi, lo);
    }
}

TEST(LowercaseTest, lowercase_utf8_string_to_ucs4)
{
    auto res = LowerCase::convert_to_ucs4(std::string_view("ABC"));
    EXPECT_EQ(3u, res.size());
    EXPECT_EQ((uint32_t)'a', res[0]);
    EXPECT_EQ((uint32_t)'b', res[1]);
    EXPECT_EQ((uint32_t)'c', res[2]);
}

GTEST_MAIN_RUN_ALL_TESTS()
