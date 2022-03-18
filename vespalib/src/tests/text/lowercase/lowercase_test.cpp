// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("lowercase_test");
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/text/lowercase.h>
#include <iostream>
#include <fstream>

using namespace vespalib;

TEST("test basic lowercase")
{
    EXPECT_EQUAL('a', LowerCase::convert('A'));
    EXPECT_EQUAL((int8_t)'a', LowerCase::convert((int8_t)'A'));
    EXPECT_EQUAL((uint8_t)'a', LowerCase::convert((uint8_t)'A'));

    std::ifstream yellData(TEST_PATH("yell-want.dat"));

    EXPECT_TRUE(yellData.good());

    while (yellData.good()) {
        uint32_t hi=0, lo=0;
        yellData >> hi >> lo;
        EXPECT_EQUAL(lo, LowerCase::convert(hi));
        if (lo != LowerCase::convert(hi)) {
            printf("lc(%04X) -> %04X\n", hi, LowerCase::convert(hi));
            printf("yell(%04X) -> %04X\n", hi, lo);
        }
        // printf("lowercase( %d )= %d\n", hi, lo);
    }
}

TEST("lowercase utf8 string to ucs4")
{
    auto res = LowerCase::convert_to_ucs4(std::string_view("ABC"));
    EXPECT_EQUAL(3u, res.size());
    EXPECT_EQUAL((uint32_t)'a', res[0]);
    EXPECT_EQUAL((uint32_t)'b', res[1]);
    EXPECT_EQUAL((uint32_t)'c', res[2]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
