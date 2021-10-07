// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/log/log.h>
LOG_SETUP("lowercase_test");
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/text/lowercase.h>
#include <iostream>
#include <fstream>

using namespace vespalib;

TEST_SETUP(Test);

int
Test::Main()
{
    TEST_INIT("lowercase_test");

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
    TEST_DONE();
}
