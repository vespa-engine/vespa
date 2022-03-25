// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
}

GTEST_MAIN_RUN_ALL_TESTS()

