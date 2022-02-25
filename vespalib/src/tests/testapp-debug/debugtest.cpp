// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

using namespace vespalib;

void testDebug() {
    TEST_DEBUG("lhs.out", "rhs.out");
    EXPECT_EQUAL(string("a\n"
                        "b\n"
                        "c\n"),

                string("a\n"
                       "b\n"
                       "c\n"
                       "d\n"));
    EXPECT_EQUAL(string("a\n"
                        "d\n"
                        "b\n"
                        "c\n"),

                 string("a\n"
                        "b\n"
                        "c\n"
                        "d\n"));
    EXPECT_EQUAL(1, 2);
    EXPECT_EQUAL(string("foo"), string("bar"));
}

TEST_MAIN() {
    testDebug();
}
