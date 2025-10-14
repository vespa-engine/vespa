// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/searchlib/query/tree/simplequery.h>
#include <vespa/searchlib/queryeval/get_weight_from_node.h>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP("getweight_test");

using namespace search::query;
using namespace search::queryeval;

namespace {
    int32_t
    getWeight(const Node &node) {
        return getWeightFromNode(node).percent();
    }
}

TEST(GetNodeWeightTest, test_variations_of_getWeight)
{
    EXPECT_EQ(0, getWeight(SimpleAnd()));
    EXPECT_EQ(0, getWeight(SimpleAndNot()));
    EXPECT_EQ(42, getWeight(SimpleEquiv(0, Weight(42))));
    EXPECT_EQ(42, getWeight(SimpleNumberTerm("foo", "bar", 1, Weight(42))));
    EXPECT_EQ(42, getWeight(SimpleLocationTerm(Location(), "bar", 1, Weight(42))));
    EXPECT_EQ(0, getWeight(SimpleNear(5, 0, 0)));
    EXPECT_EQ(0, getWeight(SimpleONear(5, 0, 0)));
    EXPECT_EQ(0, getWeight(SimpleOr()));
    EXPECT_EQ(42, getWeight(SimplePhrase("bar", 1, Weight(42))));
    EXPECT_EQ(42, getWeight(SimplePrefixTerm("foo", "bar", 1, Weight(42))));
    EXPECT_EQ(42, getWeight(SimpleRangeTerm(Range(), "bar", 1, Weight(42))));
    EXPECT_EQ(0, getWeight(SimpleRank()));
    EXPECT_EQ(42, getWeight(SimpleStringTerm("foo", "bar", 1, Weight(42))));
    EXPECT_EQ(42, getWeight(SimpleSubstringTerm("foo", "bar", 1, Weight(42))));
    EXPECT_EQ(42, getWeight(SimpleSuffixTerm("foo", "bar", 1, Weight(42))));
    EXPECT_EQ(42, getWeight(SimpleWeightedSetTerm(0, "bar", 1, Weight(42))));
    EXPECT_EQ(42, getWeight(SimpleDotProduct(0, "bar", 1, Weight(42))));
    EXPECT_EQ(42, getWeight(SimpleWandTerm(0, "bar", 1, Weight(42), 57, 67, 77.7)));
}

GTEST_MAIN_RUN_ALL_TESTS()
