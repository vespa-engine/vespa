// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("empty memory") {
    Memory m;
    EXPECT_EQUAL((const char*)0, m.data);
    EXPECT_EQUAL(0u, m.size);
}

TEST("from string") {
    string str("foo");
    Memory m1(str);
    Memory m2 = str;
    EXPECT_EQUAL(str.data(), m1.data);
    EXPECT_EQUAL(str.size(), m1.size);
    EXPECT_EQUAL(str.data(), m2.data);
    EXPECT_EQUAL(str.size(), m2.size);
}

TEST("from cstring") {
    const char *str = "foo";
    Memory m1(str);
    Memory m2 = str;
    EXPECT_EQUAL(str, m1.data);
    EXPECT_EQUAL(strlen(str), m1.size);
    EXPECT_EQUAL(str, m2.data);
    EXPECT_EQUAL(strlen(str), m2.size);
}

TEST("from ptr and len") {
    string str("foo");
    Memory m1(str.data(), str.size());
    EXPECT_EQUAL(str.data(), m1.data);
    EXPECT_EQUAL(str.size(), m1.size);
}

TEST_MAIN() { TEST_RUN_ALL(); }
