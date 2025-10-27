// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for TermDataExtractor.

#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/resolveviewvisitor.h>
#include <vespa/searchcore/proton/matching/sameelementmodifier.h>
#include <vespa/searchcore/proton/matching/termdataextractor.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchlib/fef/tablemanager.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/tree/location.h>
#include <vespa/searchlib/query/tree/point.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/searchlib/queryeval/same_element_flags.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>
#include <vector>

#include <vespa/log/log.h>
LOG_SETUP("termdataextractor_test");

namespace fef_test = search::fef::test;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::ITermData;
using search::fef::IIndexEnvironment;
using search::query::Location;
using search::query::Node;
using search::query::Point;
using search::query::QueryBuilder;
using search::query::Range;
using search::query::Weight;
using search::queryeval::SameElementFlags;
using std::string;
using std::vector;
using namespace proton::matching;
using CollectionType = FieldInfo::CollectionType;

namespace search { class AttributeManager; }

namespace {

const string field = "field";
const uint32_t id[] = { 10, 11, 12, 13, 14, 15, 16, 17, 18 };

Node::UP getQuery(const ViewResolver &resolver)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addAnd(8);
    query_builder.addNumberTerm("0.0", field, id[0], Weight(0));
    query_builder.addPrefixTerm("foo", field, id[1], Weight(0));
    query_builder.addStringTerm("bar", field, id[2], Weight(0));
    query_builder.addSubstringTerm("baz", field, id[3], Weight(0));
    query_builder.addSuffixTerm("qux", field, id[4], Weight(0));
    query_builder.addRangeTerm(Range(), field, id[5], Weight(0));
    query_builder.addWeightedSetTerm(1, field, id[6], Weight(0))
                 .addTerm("bar", Weight(0));

    query_builder.addLocationTerm(Location(Point{10, 10}, 3, 0), field, id[7], Weight(0));
    Node::UP node = query_builder.build();

    fef_test::IndexEnvironment index_environment;
    index_environment.getFields().push_back(FieldInfo(FieldType::INDEX, CollectionType::SINGLE, field, 0));
    index_environment.getFields().push_back(FieldInfo(FieldType::INDEX, CollectionType::SINGLE, "foo", 1));
    index_environment.getFields().push_back(FieldInfo(FieldType::INDEX, CollectionType::SINGLE, "bar", 2));

    ResolveViewVisitor visitor(resolver, index_environment);
    node->accept(visitor);

    return node;
}

TEST(TermDataExtractorTest, requireThatTermsAreAdded) {
    Node::UP node = getQuery(ViewResolver());

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    EXPECT_EQ(8u, term_data.size());
    for (int i = 0; i < 8; ++i) {
        EXPECT_EQ(id[i], term_data[i]->getUniqueId());
        EXPECT_EQ(1u, term_data[i]->numFields());
    }
}

TEST(TermDataExtractorTest, requireThatAViewWithTwoFieldsGivesOneTermDataPerTerm) {
    ViewResolver resolver;
    resolver.add(field, "foo");
    resolver.add(field, "bar");
    Node::UP node = getQuery(resolver);

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    EXPECT_EQ(8u, term_data.size());
    for (int i = 0; i < 8; ++i) {
        EXPECT_EQ(id[i], term_data[i]->getUniqueId());
        EXPECT_EQ(2u, term_data[i]->numFields());
    }
}

TEST(TermDataExtractorTest, requireThatUnrankedTermsAreSkipped) {
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addAnd(2);
    query_builder.addStringTerm("term1", field, id[0], Weight(0));
    query_builder.addStringTerm("term2", field, id[1], Weight(0))
        .setRanked(false);
    Node::UP node = query_builder.build();

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    EXPECT_EQ(1u, term_data.size());
    ASSERT_TRUE(term_data.size() >= 1);
    EXPECT_EQ(id[0], term_data[0]->getUniqueId());
}

TEST(TermDataExtractorTest, requireThatNegativeNearTermsAreSkipped) {
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addNear(3, 2, 2, 0);
    query_builder.addStringTerm("term1", field, id[0], Weight(0));
    query_builder.addStringTerm("term2", field, id[1], Weight(0));
    query_builder.addStringTerm("term3", field, id[2], Weight(0));
    Node::UP node = query_builder.build();

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    ASSERT_EQ(1u, term_data.size());
    EXPECT_EQ(id[0], term_data[0]->getUniqueId());
}

TEST(TermDataExtractorTest, requireThatNegativeONearTermsAreSkipped) {
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addONear(3, 2, 2, 0);
    query_builder.addStringTerm("term1", field, id[0], Weight(0));
    query_builder.addStringTerm("term2", field, id[1], Weight(0));
    query_builder.addStringTerm("term3", field, id[2], Weight(0));
    Node::UP node = query_builder.build();

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    ASSERT_EQ(1u, term_data.size());
    EXPECT_EQ(id[0], term_data[0]->getUniqueId());
}

TEST(TermDataExtractorTest, requireThatNegativeTermsAreSkipped) {
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addAnd(2);
    query_builder.addStringTerm("term1", field, id[0], Weight(0));
    query_builder.addAndNot(2);
    query_builder.addStringTerm("term2", field, id[1], Weight(0));
    query_builder.addAndNot(2);
    query_builder.addStringTerm("term3", field, id[2], Weight(0));
    query_builder.addStringTerm("term4", field, id[3], Weight(0));
    Node::UP node = query_builder.build();

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    EXPECT_EQ(2u, term_data.size());
    ASSERT_TRUE(term_data.size() >= 2);
    EXPECT_EQ(id[0], term_data[0]->getUniqueId());
    EXPECT_EQ(id[1], term_data[1]->getUniqueId());
}

std::vector<uint32_t>
same_element_query_ids(bool structured, bool ranked, bool negative)
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addAnd(2);
    query_builder.addSameElement(negative ? 1 : 2, field, id[3], Weight(7));
    if (negative) {
        query_builder.addAndNot(2);
    }
    query_builder.addStringTerm("term1", field, id[0], Weight(1));
    query_builder.addStringTerm("term2", structured ? field : "", id[1], Weight(1)).setRanked(ranked);
    query_builder.addStringTerm("term3", field, id[2], Weight(1));
    auto node = query_builder.build();
    SameElementModifier same_element_modifier;
    node->accept(same_element_modifier); // Determine if match data from same element node should be exposed
    vector<const ITermData *> terms;
    TermDataExtractor::extractTerms(*node, terms);
    std::vector<uint32_t> result_ids;
    for (auto td : terms) {
        result_ids.push_back(td->getUniqueId());
    }
    return result_ids;
}

TEST(TermDataExtractorTest, requireThatSameElementIsExtractedAsExpectedNumberOfTerms)
{
    {
        SameElementFlags::ExposeDescendantsTweak expose_descendants_tweak(false);
        EXPECT_EQ((std::vector<uint32_t>{id[3], id[2]}), same_element_query_ids(true, true, false));
        EXPECT_EQ((std::vector<uint32_t>{id[3], id[2]}), same_element_query_ids(false, true, false));
        EXPECT_EQ((std::vector<uint32_t>{id[3], id[2]}), same_element_query_ids(false, true, true));
        EXPECT_EQ((std::vector<uint32_t>{id[3], id[2]}), same_element_query_ids(false, false, false));
    }
    {
        SameElementFlags::ExposeDescendantsTweak expose_descendants_tweak(true);
        EXPECT_EQ((std::vector<uint32_t>{id[3], id[0], id[1], id[2]}), same_element_query_ids(true, true, false));
        EXPECT_EQ((std::vector<uint32_t>{id[0], id[1], id[2]}), same_element_query_ids(false, true, false));
        EXPECT_EQ((std::vector<uint32_t>{id[3], id[0], id[2]}), same_element_query_ids(false, true, true));
        EXPECT_EQ((std::vector<uint32_t>{id[3], id[0], id[2]}), same_element_query_ids(false, false, false));
    }
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
