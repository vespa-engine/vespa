// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;

void testDebug() {
    TEST_DEBUG("lhs.out", "rhs.out");
    EXPECT_EQUAL("a\n"
               "b\n"
               "c\n",

               "a\n"
               "b\n"
               "c\n"
               "d\n");
    EXPECT_EQUAL("a\n"
               "d\n"
               "b\n"
               "c\n",

               "a\n"
               "b\n"
               "c\n"
               "d\n");
    EXPECT_EQUAL(1, 2);
    EXPECT_EQUAL("foo", "bar");
}

TEST_MAIN() {
    testDebug();
}
