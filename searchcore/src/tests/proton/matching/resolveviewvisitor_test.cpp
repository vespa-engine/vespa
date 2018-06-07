// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for resolveviewvisitor.

#include <vespa/log/log.h>
LOG_SETUP("resolveviewvisitor_test");

#include <vespa/searchlib/fef/test/indexenvironment.h>
#include <vespa/searchcore/proton/matching/querynodes.h>
#include <vespa/searchcore/proton/matching/resolveviewvisitor.h>
#include <vespa/searchcore/proton/matching/viewresolver.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/query/tree/querybuilder.h>
#include <vespa/vespalib/testkit/testapp.h>
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

struct Fixture {
    IndexEnvironment index_environment;

    Fixture() {
        index_environment.getFields().push_back(FieldInfo(
                        FieldType::INDEX, CollectionType::SINGLE, field1, 0));
        index_environment.getFields().push_back(FieldInfo(
                        FieldType::INDEX, CollectionType::SINGLE, field2, 1));
    }
};

TEST_F("requireThatFieldsResolveToThemselves", Fixture) {
    ViewResolver resolver = getResolver(view);

    QueryBuilder<ProtonNodeTypes> builder;
    ProtonTermData &base = builder.addStringTerm(term, field1, id, weight);
    Node::UP node = builder.build();

    ResolveViewVisitor visitor(resolver, f.index_environment);
    node->accept(visitor);

    EXPECT_EQUAL(1u, base.numFields());
    EXPECT_EQUAL(field1, base.field(0).field_name);
}

void checkResolveAlias(const string &view_name, const string &alias,
                       const Fixture &f) {
    ViewResolver resolver = getResolver(view_name);

    QueryBuilder<ProtonNodeTypes> builder;
    ProtonTermData &base = builder.addStringTerm(term, alias, id, weight);
    Node::UP node = builder.build();

    ResolveViewVisitor visitor(resolver, f.index_environment);
    node->accept(visitor);

    ASSERT_EQUAL(2u, base.numFields());
    EXPECT_EQUAL(field1, base.field(0).field_name);
    EXPECT_EQUAL(field2, base.field(1).field_name);
}

TEST_F("requireThatViewsCanResolveToMultipleFields", Fixture) {
    checkResolveAlias(view, view, f);
}

TEST_F("requireThatEmptyViewResolvesAsDefault", Fixture) {
    const string default_view = "default";
    const string empty_view = "";
    checkResolveAlias(default_view, empty_view, f);
}

TEST_F("requireThatWeCanForceFilterField", Fixture) {
    ViewResolver resolver = getResolver(view);
    f.index_environment.getFields().back().setFilter(true);
    ResolveViewVisitor visitor(resolver, f.index_environment);

    { // use filter field settings from index environment
        QueryBuilder<ProtonNodeTypes> builder;
        ProtonStringTerm &sterm =
            builder.addStringTerm(term, view, id, weight);
        Node::UP node = builder.build();
        node->accept(visitor);
        ASSERT_EQUAL(2u, sterm.numFields());
        EXPECT_TRUE(!sterm.field(0).filter_field);
        EXPECT_TRUE(sterm.field(1).filter_field);
    }
    { // force filter on all fields
        QueryBuilder<ProtonNodeTypes> builder;
        ProtonStringTerm &sterm =
            builder.addStringTerm(term, view, id, weight);
        sterm.setPositionData(false); // force filter
        Node::UP node = builder.build();
        node->accept(visitor);
        ASSERT_EQUAL(2u, sterm.numFields());
        EXPECT_TRUE(sterm.field(0).filter_field);
        EXPECT_TRUE(sterm.field(1).filter_field);
    }
}

TEST_F("require that equiv nodes resolve view from children", Fixture) {
    ViewResolver resolver;
    resolver.add(view, field1);

    QueryBuilder<ProtonNodeTypes> builder;
    ProtonTermData &base = builder.addEquiv(2, id, weight);
    builder.addStringTerm(term, view, 42, weight);
    builder.addStringTerm(term, field2, 43, weight);
    Node::UP node = builder.build();

    ResolveViewVisitor visitor(resolver, f.index_environment);
    node->accept(visitor);

    ASSERT_EQUAL(2u, base.numFields());
    EXPECT_EQUAL(field1, base.field(0).field_name);
    EXPECT_EQUAL(field2, base.field(1).field_name);
}

TEST_F("require that view is resolved for SameElement children", Fixture) {
    ViewResolver resolver;
    resolver.add(view, field1);

    QueryBuilder<ProtonNodeTypes> builder;
    builder.addSameElement(2, "");
    ProtonStringTerm &my_term = builder.addStringTerm(term, view, 42, weight);
    builder.addStringTerm(term, field2, 43, weight);
    Node::UP node = builder.build();

    ResolveViewVisitor visitor(resolver, f.index_environment);
    node->accept(visitor);

    ASSERT_EQUAL(1u, my_term.numFields());
    EXPECT_EQUAL(field1, my_term.field(0).field_name);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
