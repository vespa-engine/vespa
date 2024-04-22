// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/fuzzy/levenshtein_distance.h>
#include <vespa/vespalib/text/lowercase.h>
#include <vespa/vespalib/gtest/gtest.h>

std::optional<uint32_t> calculate(std::string_view left, std::string_view right, uint32_t threshold) {
    std::vector<uint32_t> leftCodepoints = vespalib::LowerCase::convert_to_ucs4(left);
    std::vector<uint32_t> rightCodepoints = vespalib::LowerCase::convert_to_ucs4(right);

    std::optional<uint32_t> leftRight = vespalib::LevenshteinDistance::calculate(leftCodepoints, rightCodepoints, threshold);
    std::optional<uint32_t> rightLeft = vespalib::LevenshteinDistance::calculate(rightCodepoints, leftCodepoints, threshold);

    EXPECT_EQ(leftRight, rightLeft); // should be independent whether left or right strings are swapped

    return leftRight;
}

// Prefix matching is asymmetric and therefore cannot implicitly test result symmetry
std::optional<uint32_t> prefix_calculate(std::string_view left, std::string_view right, uint32_t threshold) {
    auto left_codepoints  = vespalib::LowerCase::convert_to_ucs4(left);
    auto right_codepoints = vespalib::LowerCase::convert_to_ucs4(right);
    return vespalib::LevenshteinDistance::calculate(left_codepoints, right_codepoints, threshold, true);
}

TEST(LevenshteinDistance, calculate_edgecases) {
    EXPECT_EQ(calculate("abc", "abc", 2), std::optional{0});
    EXPECT_EQ(calculate("abc", "ab1", 2), std::optional{1});
    EXPECT_EQ(calculate("abc", "1bc", 2), std::optional{1});
    EXPECT_EQ(calculate("abc", "a1c", 2), std::optional{1});
    EXPECT_EQ(calculate("abc", "ab", 2), std::optional{1});
    EXPECT_EQ(calculate("abc", "abcd", 2), std::optional{1});
    EXPECT_EQ(calculate("bc", "abcd", 2), std::optional{2});
    EXPECT_EQ(calculate("ab", "abcd", 2), std::optional{2});
    EXPECT_EQ(calculate("cd", "abcd", 2), std::optional{2});
    EXPECT_EQ(calculate("ad", "abcd", 2), std::optional{2});
    EXPECT_EQ(calculate("abc", "a12", 2), std::optional{2});
    EXPECT_EQ(calculate("abc", "123", 2), std::nullopt);
    EXPECT_EQ(calculate("a", "", 2), std::optional{1});
    EXPECT_EQ(calculate("ab", "", 2), std::optional{2});
    EXPECT_EQ(calculate("abc", "", 2), std::nullopt);
    EXPECT_EQ(calculate("abc", "123", 2), std::nullopt);
    EXPECT_EQ(calculate("abcde", "xad", 2), std::nullopt);
}

TEST(LevenshteinDistance, prefix_match_edge_cases) {
    // Same cases as LevenshteinDfaTest (TODO consolidate these somehow)
    for (auto max : {1, 2}) {
        EXPECT_EQ(prefix_calculate("",    "literally anything", max), std::optional{0});
        EXPECT_EQ(prefix_calculate("",    "", max),        std::optional{0});
        EXPECT_EQ(prefix_calculate("x",   "", max),        std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "abc", max),     std::optional{0});
        EXPECT_EQ(prefix_calculate("abc", "abcd", max),    std::optional{0});
        EXPECT_EQ(prefix_calculate("abc", "abcdef", max),  std::optional{0});
        EXPECT_EQ(prefix_calculate("abc", "ab", max),      std::optional{1});
        EXPECT_EQ(prefix_calculate("ac",  "abcdef", max),  std::optional{1});
        EXPECT_EQ(prefix_calculate("acd", "abcdef", max),  std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "xabcdef", max), std::optional{1});
        EXPECT_EQ(prefix_calculate("bc",  "abcdef", max),  std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "acb", max),     std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "acdefg", max),  std::optional{1});
        EXPECT_EQ(prefix_calculate("acb", "abcdef", max),  std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "abd", max),     std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "abdcfgh", max), std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "abdefgh", max), std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "xbc", max),     std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "xbcdefg", max), std::optional{1});
        EXPECT_EQ(prefix_calculate("abc", "xy", max),      std::nullopt);
    }
    EXPECT_EQ(prefix_calculate("abc", "xxabc", 2),   std::optional{2});
    EXPECT_EQ(prefix_calculate("abc", "xxabcd", 2),  std::optional{2});
    EXPECT_EQ(prefix_calculate("abcxx", "abc", 2),   std::optional{2});
    EXPECT_EQ(prefix_calculate("abcxx", "abcd", 2),  std::optional{2});
    EXPECT_EQ(prefix_calculate("xy",  "", 2), std::optional{2});
    EXPECT_EQ(prefix_calculate("xyz", "", 2), std::nullopt);

    // Max edits not in {1, 2} cases; not supported by DFA implementation.
    EXPECT_EQ(prefix_calculate("", "", 0),         std::optional{0});
    EXPECT_EQ(prefix_calculate("abc", "abc", 0),   std::optional{0});
    EXPECT_EQ(prefix_calculate("abc", "abcde", 0), std::optional{0});
    EXPECT_EQ(prefix_calculate("abc", "dbc", 0),   std::nullopt);
    EXPECT_EQ(prefix_calculate("abc", "", 3),      std::optional{3});
    EXPECT_EQ(prefix_calculate("abc", "xy", 3),    std::optional{3});
    EXPECT_EQ(prefix_calculate("abc", "xyz", 3),   std::optional{3});
    EXPECT_EQ(prefix_calculate("abc", "xyzzz", 3), std::optional{3});
    EXPECT_EQ(prefix_calculate("abcd", "xyzd", 3), std::optional{3});
    EXPECT_EQ(prefix_calculate("abcd", "xyzz", 3), std::nullopt);
    EXPECT_EQ(prefix_calculate("abcd", "", 3),     std::nullopt);
}

TEST(LevenshteinDistance, oversized_max_edits_is_well_defined) {
    const auto k = uint32_t(INT32_MAX) + 10000u;
    EXPECT_EQ(calculate("abc", "xyz", k), std::optional{3});
    EXPECT_EQ(prefix_calculate("abc", "xyzzzz", k), std::optional{3});
}

GTEST_MAIN_RUN_ALL_TESTS()

