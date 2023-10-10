// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("empty") {
    ServerSpec spec;
    EXPECT_EQUAL("", spec.host);
    EXPECT_EQUAL(0, spec.port);
}

TEST("standard") {
    ServerSpec spec("foo", 3);
    EXPECT_EQUAL("foo", spec.host);
    EXPECT_EQUAL(3, spec.port);
}

TEST("compare") {
    ServerSpec spec0("foo", 3);
    ServerSpec spec1("foo", 3);
    ServerSpec spec2("bar", 3);
    ServerSpec spec3("foo", 4);
    ServerSpec spec4("bar", 4);
    EXPECT_TRUE(spec0 == spec1);
    EXPECT_TRUE(spec1 == spec0);
    EXPECT_FALSE(spec0 == spec2);
    EXPECT_FALSE(spec2 == spec0);
    EXPECT_TRUE(spec2 < spec0);
    EXPECT_TRUE(spec0 < spec3);
    EXPECT_TRUE(spec0 < spec4);
    EXPECT_FALSE(spec0 < spec1);
    EXPECT_FALSE(spec0 < spec2);
    EXPECT_FALSE(spec3 < spec0);
    EXPECT_FALSE(spec4 < spec0);
}

TEST_MAIN() { TEST_RUN_ALL(); }
