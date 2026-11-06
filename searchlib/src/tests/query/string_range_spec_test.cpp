// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for query_visitor.

#include <vespa/searchlib/query/string_range_spec.h>
#include <vespa/vespalib/gtest/gtest.h>

using namespace search;

namespace {

TEST(StringRangeSpecTest, test_includes_empty_string) {
    std::vector<StringRangeSpec> ranges_with_empty_string = {{std::nullopt, false, std::nullopt, false},
                                                             {std::nullopt, false, std::nullopt, true},
                                                             {std::nullopt, false, "bar", false},
                                                             {std::nullopt, false, "bar", true},
                                                             {std::nullopt, true, std::nullopt, false},
                                                             {std::nullopt, true, std::nullopt, true},
                                                             {std::nullopt, true, "bar", false},
                                                             {std::nullopt, true, "bar", true},
                                                             {"", true, std::nullopt, false},
                                                             {"", true, std::nullopt, true},
                                                             {"", true, "bar", false},
                                                             {"", true, "bar", true}};
    std::vector<StringRangeSpec> ranges_without_empty_string = {{"", false, std::nullopt, false},
                                                                {"", false, std::nullopt, true},
                                                                {"", false, "bar", false},
                                                                {"", false, "bar", true},
                                                                {std::nullopt, false, "", false},
                                                                {std::nullopt, true, "", false},
                                                                {"", true, "", false}};

    for (auto& range_spec : ranges_with_empty_string) {
        EXPECT_TRUE(range_spec.includes_empty_string());
    }
    for (auto& range_spec : ranges_without_empty_string) {
        EXPECT_FALSE(range_spec.includes_empty_string());
    }
}

} // namespace

GTEST_MAIN_RUN_ALL_TESTS()
