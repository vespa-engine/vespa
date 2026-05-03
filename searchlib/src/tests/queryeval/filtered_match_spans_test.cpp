// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchcommon/common/element_ids.h>
#include <vespa/searchlib/queryeval/filtered_match_spans.h>
#include <vespa/vespalib/gtest/gtest.h>

using search::common::ElementIds;
using search::queryeval::FilteredMatchSpans;
using search::queryeval::MatchSpan;
using search::queryeval::MatchSpanPos;

class FilteredMatchSpansTest : public testing::Test {
protected:
    FilteredMatchSpans _filtered_spans;

    FilteredMatchSpansTest();
    ~FilteredMatchSpansTest() override;
    static std::vector<MatchSpan> match_spans(std::vector<MatchSpan> match_spans_in) { return match_spans_in; }
    static std::vector<MatchSpan> as_vector(std::span<const MatchSpan> match_spans_in) {
        return {match_spans_in.begin(), match_spans_in.end()};
    }
    static ElementIds make_element_ids(std::vector<uint32_t> element_ids) { return ElementIds(element_ids); }
    static constexpr uint32_t pos_limit = std::numeric_limits<uint32_t>::max();
};

FilteredMatchSpansTest::FilteredMatchSpansTest() : testing::Test(), _filtered_spans() {
}

FilteredMatchSpansTest::~FilteredMatchSpansTest() = default;

TEST_F(FilteredMatchSpansTest, single_field_intersections) {
    auto sample_spans = match_spans({{7, {1, 9}, {2, 1}}, {7, {2, 8}, {2, 10}}});
    EXPECT_EQ(sample_spans, as_vector(_filtered_spans.intersection(sample_spans, ElementIds::select_all())));
    EXPECT_EQ(match_spans({}), as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({}))));
    EXPECT_EQ(match_spans({}), as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({0}))));
    EXPECT_EQ(match_spans({{7, {1, 9}, {1, pos_limit}}}),
              as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({1}))));
    EXPECT_EQ(match_spans({{7, {2, 0}, {2, 1}}, {7, {2, 8}, {2, 10}}}),
              as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({2}))));
    EXPECT_EQ(match_spans({}), as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({3}))));
    EXPECT_EQ(sample_spans, as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({1, 2}))));
}

TEST_F(FilteredMatchSpansTest, multiple_fields_intersections) {
    auto sample_spans = match_spans({{7, {2, 8}, {2, 10}}, {7, {5, 9}, {5, 12}}, {13, {1, 9}, {2, 1}}});
    EXPECT_EQ(sample_spans, as_vector(_filtered_spans.intersection(sample_spans, ElementIds::select_all())));
    EXPECT_EQ(match_spans({}), as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({}))));
    EXPECT_EQ(match_spans({}), as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({0}))));
    EXPECT_EQ(match_spans({{13, {1, 9}, {1, pos_limit}}}),
              as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({1}))));
    EXPECT_EQ(match_spans({{7, {2, 8}, {2, 10}}, {13, {2, 0}, {2, 1}}}),
              as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({2}))));
    EXPECT_EQ(match_spans({}), as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({3}))));
    EXPECT_EQ(sample_spans, as_vector(_filtered_spans.intersection(sample_spans, make_element_ids({1, 2, 5}))));
}
