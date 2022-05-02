// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/fuzzy/fuzzy_matcher.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace vespalib;


template <typename T>
void assert_span(std::span<const T> left, std::vector<T> right) {
    EXPECT_TRUE(std::equal(left.begin(), left.end(), right.begin(), right.end()));
}

TEST(FuzzyMatcherTest, get_prefix_edge_cases) {
    assert_span(FuzzyMatcher::get_prefix({1, 2, 3}, 0), {});
    assert_span(FuzzyMatcher::get_prefix({1, 2, 3}, 1), {1 });
    assert_span(FuzzyMatcher::get_prefix({1, 2, 3}, 2), {1, 2});
    assert_span(FuzzyMatcher::get_prefix({1, 2, 3}, 3), {1, 2, 3});
    assert_span(FuzzyMatcher::get_prefix({1, 2, 3}, 10),{1, 2, 3});
    assert_span(FuzzyMatcher::get_prefix({}, 0), {});
    assert_span(FuzzyMatcher::get_prefix({}, 10), {});
}

TEST(FuzzyMatcherTest, get_suffix_edge_cases) {
    assert_span(FuzzyMatcher::get_suffix({1, 2, 3}, 0), {1, 2, 3});
    assert_span(FuzzyMatcher::get_suffix({1, 2, 3}, 1), {2, 3});
    assert_span(FuzzyMatcher::get_suffix({1, 2, 3}, 2), {3});
    assert_span(FuzzyMatcher::get_suffix({1, 2, 3}, 3), {});
    assert_span(FuzzyMatcher::get_suffix({1, 2, 3}, 10), {});
    assert_span(FuzzyMatcher::get_suffix({}, 0), {});
    assert_span(FuzzyMatcher::get_suffix({}, 10),{});
}

TEST(FuzzyMatcherTest, fuzzy_match_empty_prefix) {
    FuzzyMatcher fuzzy("abc", 2, 0, false);
    EXPECT_TRUE(fuzzy.isMatch("abc"));
    EXPECT_TRUE(fuzzy.isMatch("ABC"));
    EXPECT_TRUE(fuzzy.isMatch("ab1"));
    EXPECT_TRUE(fuzzy.isMatch("a12"));
    EXPECT_FALSE(fuzzy.isMatch("123"));
}

TEST(FuzzyMatcherTest, fuzzy_match_cased) {
    FuzzyMatcher fuzzy("abc", 2, 0, true);
    EXPECT_TRUE(fuzzy.isMatch("abc"));
    EXPECT_TRUE(fuzzy.isMatch("abC"));
    EXPECT_TRUE(fuzzy.isMatch("aBC"));
    EXPECT_FALSE(fuzzy.isMatch("ABC"));
}

TEST(FuzzyMatcherTest, fuzzy_match_with_prefix) {
    FuzzyMatcher fuzzy("abcdef", 2, 2, false);
    EXPECT_TRUE(fuzzy.isMatch("abcdef"));
    EXPECT_TRUE(fuzzy.isMatch("ABCDEF"));
    EXPECT_TRUE(fuzzy.isMatch("abcde1"));
    EXPECT_TRUE(fuzzy.isMatch("abcd12"));
    EXPECT_FALSE(fuzzy.isMatch("abc123"));
    EXPECT_FALSE(fuzzy.isMatch("12cdef"));
}

TEST(FuzzyMatcherTest, get_prefix_is_empty) {
    FuzzyMatcher fuzzy("whatever", 2, 0, false);
    EXPECT_EQ(fuzzy.getPrefix(), "");
}

TEST(FuzzyMatcherTest, term_is_empty) {
    FuzzyMatcher fuzzy("", 2, 0, false);
    EXPECT_TRUE(fuzzy.isMatch(""));
    EXPECT_TRUE(fuzzy.isMatch("a"));
    EXPECT_TRUE(fuzzy.isMatch("aa"));
    EXPECT_FALSE(fuzzy.isMatch("aaa"));
}

TEST(FuzzyMatcherTest, get_prefix_non_empty) {
    FuzzyMatcher fuzzy("abcd", 2, 2, false);
    EXPECT_EQ(fuzzy.getPrefix(), "ab");
}

GTEST_MAIN_RUN_ALL_TESTS()
