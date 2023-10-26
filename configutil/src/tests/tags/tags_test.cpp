// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/testapp.h>
#include <lib/tags.h>

using namespace configdefinitions;

TEST("upcase") {
    EXPECT_EQUAL(vespalib::string("A"), upcase(vespalib::string("a")));
    EXPECT_EQUAL(vespalib::string("A"), upcase(vespalib::string("A")));
}

TEST("tagsContain") {
    EXPECT_TRUE(tagsContain("a b c", "a"));
    EXPECT_TRUE(tagsContain("a b c", "b"));
    EXPECT_TRUE(tagsContain("a b c", "c"));

    EXPECT_FALSE(tagsContain("a b c", "d"));
}

TEST_MAIN() { TEST_RUN_ALL(); }
