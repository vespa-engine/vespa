// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <vbench/test/all.h>

using namespace vbench;

TEST("untainted") {
    Taint t;
    bool fail = t;
    EXPECT_FALSE(fail);
    EXPECT_FALSE(t.taint());
    EXPECT_EQUAL("", t.reason());
}

TEST("Taintable::nil") {
    const Taint &t = Taintable::nil().tainted();
    bool fail = t;
    EXPECT_FALSE(fail);
    EXPECT_FALSE(t.taint());
    EXPECT_EQUAL("", t.reason());
}

TEST("tainted") {
    Taint t("argh");
    bool fail = t;
    EXPECT_TRUE(fail);
    EXPECT_TRUE(t.taint());
    EXPECT_EQUAL("argh", t.reason());
}

TEST("reset taint") {
    Taint t;
    EXPECT_FALSE(t.taint());
    EXPECT_EQUAL("", t.reason());
    t.reset("argh");
    EXPECT_TRUE(t.taint());
    EXPECT_EQUAL("argh", t.reason());
    t.reset();
    EXPECT_FALSE(t.taint());
    EXPECT_EQUAL("", t.reason());
}

TEST_MAIN() { TEST_RUN_ALL(); }
