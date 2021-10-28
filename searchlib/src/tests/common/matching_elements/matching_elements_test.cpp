// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/searchlib/common/matching_elements.h>

using namespace search;

namespace {

std::vector<uint32_t> vec(const std::initializer_list<uint32_t> list) {
    return std::vector<uint32_t>(list);
}

}

struct MatchingElementsTest : ::testing::Test {
    MatchingElements matches;
    MatchingElementsTest() : matches() {
        matches.add_matching_elements(1, "foo", vec({1, 3, 5}));
        matches.add_matching_elements(1, "bar", vec({2, 4, 6}));
        matches.add_matching_elements(2, "foo", vec({1, 2, 3}));
        matches.add_matching_elements(2, "bar", vec({4, 5, 6}));
        matches.add_matching_elements(2, "foo", vec({2, 3, 5}));
        matches.add_matching_elements(2, "bar", vec({2, 4, 5}));
    }
    ~MatchingElementsTest() = default;
};


TEST_F(MatchingElementsTest, require_that_added_matches_can_be_looked_up) {
    EXPECT_EQ(matches.get_matching_elements(1, "foo"), vec({1, 3, 5}));
    EXPECT_EQ(matches.get_matching_elements(1, "bar"), vec({2, 4, 6}));
}

TEST_F(MatchingElementsTest, require_that_added_matches_are_merged) {
    EXPECT_EQ(matches.get_matching_elements(2, "foo"), vec({1, 2, 3, 5}));
    EXPECT_EQ(matches.get_matching_elements(2, "bar"), vec({2, 4, 5, 6}));
}

TEST_F(MatchingElementsTest, require_that_nonexisting_lookup_gives_empty_result) {
    EXPECT_EQ(matches.get_matching_elements(1, "bogus"), vec({}));
    EXPECT_EQ(matches.get_matching_elements(7, "foo"), vec({}));
}

GTEST_MAIN_RUN_ALL_TESTS()
