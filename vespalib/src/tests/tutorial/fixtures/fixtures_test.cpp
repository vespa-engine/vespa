// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>

struct Fixture {
    int value;
    Fixture() : value(5) {}
};

TEST_F("basic fixture", Fixture) {
    EXPECT_EQUAL(5, f1.value);
}

TEST_FFF("fancy fixtures", size_t(10), int(5), std::vector<int>(f1, f2)) {
    EXPECT_EQUAL(10u, f1);
    EXPECT_EQUAL(5, f2);
    ASSERT_EQUAL(10u, f3.size());
    EXPECT_EQUAL(5, f3[7]);
}

TEST_MAIN() { TEST_RUN_ALL(); }
