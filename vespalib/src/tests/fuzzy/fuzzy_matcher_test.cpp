// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;

FuzzyMatcher from_term(std::string_view term, uint8_t threshold, uint8_t prefix_size) {
    return {LowerCase::convert_to_ucs4(term), threshold, prefix_size};
}

TEST(FuzzyMatcherTest, fuzzy_match_empty_prefix) {
    FuzzyMatcher fuzzy = from_term("abc", 2, 0);
    EXPECT_TRUE(fuzzy.isMatch("abc"));
    EXPECT_TRUE(fuzzy.isMatch("ABC"));
    EXPECT_TRUE(fuzzy.isMatch("ab1"));
    EXPECT_TRUE(fuzzy.isMatch("a12"));
    EXPECT_FALSE(fuzzy.isMatch("123"));
}

TEST(FuzzyMatcherTest, fuzzy_match_with_prefix) {
    FuzzyMatcher fuzzy = from_term("abcdef", 2, 2);
    EXPECT_TRUE(fuzzy.isMatch("abcdef"));
    EXPECT_TRUE(fuzzy.isMatch("ABCDEF"));
    EXPECT_TRUE(fuzzy.isMatch("abcde1"));
    EXPECT_TRUE(fuzzy.isMatch("abcd12"));
    EXPECT_FALSE(fuzzy.isMatch("abc123"));
    EXPECT_TRUE(fuzzy.isMatch("12cdef")); // prefix match is not enforced
}

TEST(FuzzyMatcherTest, get_prefix_is_empty) {
    FuzzyMatcher fuzzy = from_term("whatever", 2, 0);
    EXPECT_EQ(fuzzy.getPrefix(), "");
}

TEST(FuzzyMatcherTest, get_prefix_non_empty) {
    FuzzyMatcher fuzzy = from_term("abcd", 2, 2);
    EXPECT_EQ(fuzzy.getPrefix(), "ab");
}

GTEST_MAIN_RUN_ALL_TESTS()
