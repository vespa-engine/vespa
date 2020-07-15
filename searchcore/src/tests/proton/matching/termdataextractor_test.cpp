// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for TermDataExtractor.

#include <vespa/log/log.h>
LOG_SETUP("termdataextractor_test");

#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/resolveviewvisitor.h>
#include <vespa/searchcore/proton/matching/termdataextractor.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchlib/fef/tablemanager.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchlib/query/tree/location.h>
#include <vespa/searchlib/query/tree/point.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/searchlib/query/weight.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <string>
#include <vector>

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
using std::string;
using std::vector;
using namespace proton::matching;
using CollectionType = FieldInfo::CollectionType;

namespace search { class AttributeManager; }

namespace {

class Test : public vespalib::TestApp {
    void requireThatTermsAreAdded();
    void requireThatAViewWithTwoFieldsGivesOneTermDataPerTerm();
    void requireThatUnrankedTermsAreSkipped();
    void requireThatNegativeTermsAreSkipped();
    void requireThatSameElementIsSkipped();

public:
    int Main() override;
};

int
Test::Main()
{
    TEST_INIT("termdataextractor_test");

    TEST_DO(requireThatTermsAreAdded());
    TEST_DO(requireThatAViewWithTwoFieldsGivesOneTermDataPerTerm());
    TEST_DO(requireThatUnrankedTermsAreSkipped());
    TEST_DO(requireThatNegativeTermsAreSkipped());
    TEST_DO(requireThatSameElementIsSkipped());

    TEST_DONE();
}

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
    query_builder.addWeightedSetTerm(1, field, id[6], Weight(0));
    {
        // weighted token
        query_builder.addStringTerm("bar", field, id[3], Weight(0));
    }

    query_builder.addLocationTerm(Location(Point(10, 10), 3, 0),
                                  field, id[7], Weight(0));
    Node::UP node = query_builder.build();

    fef_test::IndexEnvironment index_environment;
    index_environment.getFields().push_back(FieldInfo(FieldType::INDEX, CollectionType::SINGLE, field, 0));
    index_environment.getFields().push_back(FieldInfo(FieldType::INDEX, CollectionType::SINGLE, "foo", 1));
    index_environment.getFields().push_back(FieldInfo(FieldType::INDEX, CollectionType::SINGLE, "bar", 2));

    ResolveViewVisitor visitor(resolver, index_environment);
    node->accept(visitor);

    return node;
}

void Test::requireThatTermsAreAdded() {
    Node::UP node = getQuery(ViewResolver());

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    EXPECT_EQUAL(7u, term_data.size());
    for (int i = 0; i < 7; ++i) {
        EXPECT_EQUAL(id[i], term_data[i]->getUniqueId());
        EXPECT_EQUAL(1u, term_data[i]->numFields());
    }
}

void Test::requireThatAViewWithTwoFieldsGivesOneTermDataPerTerm() {
    ViewResolver resolver;
    resolver.add(field, "foo");
    resolver.add(field, "bar");
    Node::UP node = getQuery(resolver);

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    EXPECT_EQUAL(7u, term_data.size());
    for (int i = 0; i < 7; ++i) {
        EXPECT_EQUAL(id[i], term_data[i]->getUniqueId());
        EXPECT_EQUAL(2u, term_data[i]->numFields());
    }
}

void
Test::requireThatUnrankedTermsAreSkipped()
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addAnd(2);
    query_builder.addStringTerm("term1", field, id[0], Weight(0));
    query_builder.addStringTerm("term2", field, id[1], Weight(0))
        .setRanked(false);
    Node::UP node = query_builder.build();

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    EXPECT_EQUAL(1u, term_data.size());
    ASSERT_TRUE(term_data.size() >= 1);
    EXPECT_EQUAL(id[0], term_data[0]->getUniqueId());
}

void
Test::requireThatNegativeTermsAreSkipped()
{
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
    EXPECT_EQUAL(2u, term_data.size());
    ASSERT_TRUE(term_data.size() >= 2);
    EXPECT_EQUAL(id[0], term_data[0]->getUniqueId());
    EXPECT_EQUAL(id[1], term_data[1]->getUniqueId());
}

void
Test::requireThatSameElementIsSkipped()
{
    QueryBuilder<ProtonNodeTypes> query_builder;
    query_builder.addAnd(2);
    query_builder.addSameElement(2, field);
    query_builder.addStringTerm("term1", field, id[0], Weight(1));
    query_builder.addStringTerm("term2", field, id[1], Weight(1));
    query_builder.addStringTerm("term3", field, id[2], Weight(1));
    Node::UP node = query_builder.build();

    vector<const ITermData *> term_data;
    TermDataExtractor::extractTerms(*node, term_data);
    EXPECT_EQUAL(1u, term_data.size());
    ASSERT_TRUE(term_data.size() >= 1);
    EXPECT_EQUAL(id[2], term_data[0]->getUniqueId());
}

}  // namespace

TEST_APPHOOK(Test);
