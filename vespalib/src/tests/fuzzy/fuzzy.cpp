// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/fuzzy/fuzzy.h>

using namespace vespalib;


TEST("require that levenstein distance works") {
    EXPECT_EQUAL(0u, Fuzzy::levenstein_distance("abc", "abc", 2).value());
    EXPECT_EQUAL(0u, Fuzzy::levenstein_distance("abc", "ABC", 2).value());
    EXPECT_EQUAL(1u, Fuzzy::levenstein_distance("abc", "abd", 2).value());
    EXPECT_EQUAL(1u, Fuzzy::levenstein_distance("ABC", "abd", 2).value());
    EXPECT_EQUAL(2u, Fuzzy::levenstein_distance("ABC", "add", 2).value());
    EXPECT_FALSE(Fuzzy::levenstein_distance("ABC", "ddd", 2).has_value());
}

TEST("require that extracting of a prefix works") {
    Fuzzy fuzzy(Fuzzy::folded_codepoints("prefix"), 2, 2);
    EXPECT_EQUAL("pr", fuzzy.getPrefix());
}

TEST("require that empty prefix works") {
    Fuzzy fuzzy(Fuzzy::folded_codepoints("prefix"), 0, 2);
    EXPECT_EQUAL("", fuzzy.getPrefix());
}

TEST("require that longer prefix size works") {
    Fuzzy fuzzy(Fuzzy::folded_codepoints("prefix"), 100, 2);
    EXPECT_EQUAL("prefix", fuzzy.getPrefix());
}


TEST_MAIN() { TEST_RUN_ALL(); }
