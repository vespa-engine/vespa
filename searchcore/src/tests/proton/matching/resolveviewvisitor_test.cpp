// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for resolveviewvisitor.

#include <vespa/log/log.h>
LOG_SETUP("resolveviewvisitor_test");

#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/resolveviewvisitor.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>

namespace fef_test = search::fef::test;
using search::fef::FieldInfo;
using search::fef::FieldType;
using search::fef::test::IndexEnvironment;
using search::query::Node;
using search::query::QueryBuilder;
using std::string;
using namespace proton::matching;
using CollectionType = FieldInfo::CollectionType;

namespace {

const string term = "term";
const string view = "view";
const string field1 = "field1";
const string field2 = "field2";
const uint32_t id = 1;
const search::query::Weight weight(2);

ViewResolver getResolver(const string &test_view) {
    ViewResolver resolver;
    resolver.add(test_view, field1);
    resolver.add(test_view, field2);
    return resolver;
}

class ResolveViewVisitorTest : public ::testing::Test {
protected:
    IndexEnvironment index_environment;
    ResolveViewVisitorTest();
    ~ResolveViewVisitorTest() override;
    void checkResolveAlias(const string &view_name, const string &alias) const;
};

ResolveViewVisitorTest::ResolveViewVisitorTest()
    : ::testing::Test(),
      index_environment()
{
    index_environment.getFields().emplace_back(FieldType::INDEX, CollectionType::SINGLE, field1, 0);
    index_environment.getFields().emplace_back(FieldType::INDEX, CollectionType::SINGLE, field2, 1);
}

ResolveViewVisitorTest::~ResolveViewVisitorTest() = default;

void
ResolveViewVisitorTest::checkResolveAlias(const string &view_name, const string &alias) const
{
    ViewResolver resolver = getResolver(view_name);

    QueryBuilder<ProtonNodeTypes> builder;
    ProtonTermData &base = builder.addStringTerm(term, alias, id, weight);
    Node::UP node = builder.build();

    ResolveViewVisitor visitor(resolver, index_environment);
    node->accept(visitor);

    ASSERT_EQ(2u, base.numFields());
    EXPECT_EQ(field1, base.field(0).getName());
    EXPECT_EQ(field2, base.field(1).getName());
}

TEST_F(ResolveViewVisitorTest, requireThatFieldsResolveToThemselves) {
    ViewResolver resolver = getResolver(view);

    QueryBuilder<ProtonNodeTypes> builder;
    ProtonTermData &base = builder.addStringTerm(term, field1, id, weight);
    Node::UP node = builder.build();

    ResolveViewVisitor visitor(resolver, index_environment);
    node->accept(visitor);

    EXPECT_EQ(1u, base.numFields());
    EXPECT_EQ(field1, base.field(0).getName());
}

TEST_F(ResolveViewVisitorTest, requireThatViewsCanResolveToMultipleFields) {
    checkResolveAlias(view, view);
}

TEST_F(ResolveViewVisitorTest, requireThatEmptyViewResolvesAsDefault) {
    const string default_view = "default";
    const string empty_view = "";
    checkResolveAlias(default_view, empty_view);
}

TEST_F(ResolveViewVisitorTest, requireThatWeCanForceFilterField) {
    ViewResolver resolver = getResolver(view);
    index_environment.getFields().back().setFilter(true);
    ResolveViewVisitor visitor(resolver, index_environment);

    { // use filter field settings from index environment
        QueryBuilder<ProtonNodeTypes> builder;
        ProtonStringTerm &sterm = builder.addStringTerm(term, view, id, weight);
        Node::UP node = builder.build();
        node->accept(visitor);
        ASSERT_EQ(2u, sterm.numFields());
        EXPECT_TRUE(!sterm.field(0).is_filter());
        EXPECT_TRUE(sterm.field(1).is_filter());
    }
    { // force filter on all fields
        QueryBuilder<ProtonNodeTypes> builder;
        ProtonStringTerm &sterm = builder.addStringTerm(term, view, id, weight);
        sterm.setPositionData(false); // force filter
        Node::UP node = builder.build();
        node->accept(visitor);
        ASSERT_EQ(2u, sterm.numFields());
        EXPECT_TRUE(sterm.field(0).is_filter());
        EXPECT_TRUE(sterm.field(1).is_filter());
    }
}

TEST_F(ResolveViewVisitorTest, require_that_equiv_nodes_resolve_view_from_children) {
    ViewResolver resolver;
    resolver.add(view, field1);

    QueryBuilder<ProtonNodeTypes> builder;
    ProtonTermData &base = builder.addEquiv(2, id, weight);
    builder.addStringTerm(term, view, 42, weight);
    builder.addStringTerm(term, field2, 43, weight);
    Node::UP node = builder.build();

    ResolveViewVisitor visitor(resolver, index_environment);
    node->accept(visitor);

    ASSERT_EQ(2u, base.numFields());
    EXPECT_EQ(field1, base.field(0).getName());
    EXPECT_EQ(field2, base.field(1).getName());
}

TEST_F(ResolveViewVisitorTest, require_that_view_is_resolved_for_SameElement_and_its_children) {
    ViewResolver resolver;
    resolver.add(view, field1);
    resolver.add("view2", field2);

    QueryBuilder<ProtonNodeTypes> builder;
    ProtonSameElement &same_elem = builder.addSameElement(2, "view2", 13, weight);
    ProtonStringTerm &my_term = builder.addStringTerm(term, view, 42, weight);
    builder.addStringTerm(term, field2, 43, weight);
    Node::UP node = builder.build();

    ResolveViewVisitor visitor(resolver, index_environment);
    node->accept(visitor);

    ASSERT_EQ(1u, same_elem.numFields());
    EXPECT_EQ(field2, same_elem.field(0).getName());

    ASSERT_EQ(1u, my_term.numFields());
    EXPECT_EQ(field1, my_term.field(0).getName());
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
