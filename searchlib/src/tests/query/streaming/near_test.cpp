// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/streaming/near_query_node.h>
#include <vespa/searchlib/query/streaming/onear_query_node.h>
#include <vespa/searchlib/query/streaming/queryterm.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/query/tree/stackdumpcreator.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <ostream>
#include <tuple>

using TestHit = std::tuple<uint32_t, uint32_t, int32_t, uint32_t>;

using search::query::QueryBuilder;
using search::query::Node;
using search::query::SimpleQueryNodeTypes;
using search::query::StackDumpCreator;
using search::query::Weight;
using search::streaming::NearQueryNode;
using search::streaming::ONearQueryNode;
using search::streaming::Query;
using search::streaming::QueryNodeResultFactory;
using search::streaming::QueryTerm;
using search::streaming::QueryTermList;

class TestParam {
    bool _ordered;
public:
    TestParam(bool ordered_in)
        : _ordered(ordered_in)
    {
    }
    bool ordered() const noexcept { return _ordered; }
};

std::ostream& operator<<(std::ostream& os, const TestParam& param)
{
    os << (param.ordered() ? "onear" : "near");
    return os;

}
class NearTest : public ::testing::TestWithParam<TestParam> {
public:
    NearTest();
    ~NearTest();
    bool evaluate_query(uint32_t distance, const std::vector<std::vector<TestHit>> &hitsvv);
};

NearTest::NearTest()
    : ::testing::TestWithParam<TestParam>()
{
}

NearTest::~NearTest() = default;

bool
NearTest::evaluate_query(uint32_t distance, const std::vector<std::vector<TestHit>> &hitsvv)
{
    QueryBuilder<SimpleQueryNodeTypes> builder;
    if (GetParam().ordered()) {
        builder.addONear(hitsvv.size(), distance);
    } else {
        builder.addNear(hitsvv.size(), distance);
    }
    for (uint32_t idx = 0; idx < hitsvv.size(); ++idx) {
        vespalib::asciistream s;
        s << "s" << idx;
        builder.addStringTerm(s.str(), "field", idx, Weight(0));
    }
    auto node = builder.build();
    std::string stackDump = StackDumpCreator::create(*node);
    QueryNodeResultFactory empty;
    auto q = std::make_unique<Query>(empty, stackDump);
    if (GetParam().ordered()) {
        auto& top = dynamic_cast<ONearQueryNode&>(q->getRoot());
        EXPECT_EQ(hitsvv.size(), top.size());
    } else {
        auto& top = dynamic_cast<NearQueryNode&>(q->getRoot());
        EXPECT_EQ(hitsvv.size(), top.size());
    }
    QueryTermList terms;
    q->getLeaves(terms);
    EXPECT_EQ(hitsvv.size(), terms.size());
    for (QueryTerm * qt : terms) {
        qt->resizeFieldId(1);
    }
    for (uint32_t idx = 0; idx < hitsvv.size(); ++idx) {
        auto& hitsv = hitsvv[idx];
        auto& term = terms[idx];
        for (auto& hit : hitsv) {
            term->add(std::get<0>(hit), std::get<1>(hit), std::get<2>(hit), std::get<3>(hit));
        }
    }
    return q->getRoot().evaluate();
}

TEST_P(NearTest, test_empty_near)
{
    EXPECT_FALSE(evaluate_query(4, { }));
}

TEST_P(NearTest, test_near_success)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 0} },
                                    { { 0, 0, 10, 2} },
                                    { { 0, 0, 10, 4} } }));
}

TEST_P(NearTest, test_near_fail_distance_exceeded_first_term)
{
    EXPECT_FALSE(evaluate_query(4, { { { 0, 0, 10, 0} },
                                     { { 0, 0, 10, 2} },
                                     { { 0, 0, 10, 5} } }));
}

TEST_P(NearTest, test_near_fail_distance_exceeded_second_term)
{
    EXPECT_FALSE(evaluate_query(4, { { { 0, 0, 10, 2} },
                                     { { 0, 0, 10, 0} },
                                     { { 0, 0, 10, 5} } }));
}

TEST_P(NearTest, test_near_fail_element)
{
    EXPECT_FALSE(evaluate_query(4, { { { 0, 0, 10, 0} },
                                     { { 0, 0, 10, 2} },
                                     { { 0, 1, 10, 4} } }));
}

TEST_P(NearTest, test_near_fail_field)
{
    EXPECT_FALSE(evaluate_query(4, { { { 0, 0, 10, 0} },
                                     { { 0, 0, 10, 2} },
                                     { { 1, 0, 10, 4} } }));
}

TEST_P(NearTest, test_near_success_after_step_first_term)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 0}, { 0, 0, 10, 2} },
                                    { { 0, 0, 10, 3} },
                                    { { 0, 0, 10, 5} } }));
}

TEST_P(NearTest, test_near_success_after_step_second_term)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 2} },
                                    { { 0, 0, 10, 0}, {0, 0, 10, 3} },
                                    { { 0, 0, 10, 5} } }));
}

TEST_P(NearTest, test_near_success_in_second_element)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 0}, { 0, 1, 10, 0} },
                                    { { 0, 0, 10, 2}, { 0, 1, 10, 2} },
                                    { { 0, 0, 10, 5}, { 0, 1, 10, 4} } }));
}

TEST_P(NearTest, test_near_success_in_second_field)
{
    EXPECT_TRUE(evaluate_query(4, { { { 0, 0, 10, 0}, { 1, 0, 10, 0} },
                                    { { 0, 0, 10, 2}, { 1, 0, 10, 2} },
                                    { { 0, 0, 10, 5}, { 1, 0, 10, 4} } }));
}

TEST_P(NearTest, test_order_might_matter)
{
    EXPECT_EQ(!GetParam().ordered(), evaluate_query(4, { { { 0, 0, 10, 2} },
                                                         { { 0, 0, 10, 0} },
                                                         { { 0, 0, 10, 4} } }));
}

TEST_P(NearTest, test_overlap_might_matter)
{
    EXPECT_EQ(!GetParam().ordered(), evaluate_query(4, { { { 0, 0, 10, 0} },
                                                         { { 0, 0, 10, 0} },
                                                         { { 0, 0, 10, 4} } }));
}

auto test_values = ::testing::Values(TestParam(false), TestParam(true));

INSTANTIATE_TEST_SUITE_P(NearTests, NearTest, test_values, testing::PrintToStringParamName());

GTEST_MAIN_RUN_ALL_TESTS()
