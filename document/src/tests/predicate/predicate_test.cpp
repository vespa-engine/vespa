// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate.

#include <vespa/document/predicate/predicate.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/document/predicate/predicate_slime_builder.h>
#include <string>
#include <climits>

#include <vespa/log/log.h>
LOG_SETUP("predicate_test");

using std::string;
using std::vector;
using vespalib::Slime;
using vespalib::slime::Cursor;
using namespace document;

namespace {

using SlimeUP = std::unique_ptr<Slime>;

TEST("require that predicate feature set slimes can be compared") {
    PredicateSlimeBuilder builder;
    builder.feature("foo").value("bar").value("baz");
    SlimeUP s1 = builder.build();
    builder.feature("foo").value("baz").value("bar");
    ASSERT_EQUAL(0, Predicate::compare(*s1, *builder.build()));

    builder.feature("bar").value("baz").value("bar");
    ASSERT_EQUAL(1, Predicate::compare(*s1, *builder.build()));
    builder.feature("qux").value("baz").value("bar");
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));

    builder.feature("foo").value("baz");
    ASSERT_EQUAL(1, Predicate::compare(*s1, *builder.build()));
    builder.feature("foo").value("baz").value("qux").value("quux");
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));

    builder.feature("foo").value("baz").value("qux");
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));
    builder.feature("foo").value("baz").value("aaa");
    ASSERT_EQUAL(1, Predicate::compare(*s1, *builder.build()));
}

TEST("require that predicate feature range slimes can be compared") {
    PredicateSlimeBuilder builder;
    builder.feature("foo").range(0, 10);
    SlimeUP s1 = builder.build();
    builder.feature("foo").range(0, 10);
    ASSERT_EQUAL(0, Predicate::compare(*s1, *builder.build()));

    builder.feature("foo").range(-1, 10);
    ASSERT_EQUAL(1, Predicate::compare(*s1, *builder.build()));
    builder.feature("foo").range(1, 10);
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));

    builder.feature("foo").range(0, 9);
    ASSERT_EQUAL(1, Predicate::compare(*s1, *builder.build()));
    builder.feature("foo").range(0, 11);
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));

    builder.feature("foo").greaterEqual(0);
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));
    builder.feature("foo").lessEqual(10);
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));
}

TEST("require that predicate open feature range slimes can be compared") {
    PredicateSlimeBuilder builder;
    builder.feature("foo").greaterEqual(10);
    SlimeUP s1 = builder.build();
    builder.feature("foo").greaterEqual(10);
    ASSERT_EQUAL(0, Predicate::compare(*s1, *builder.build()));

    builder.feature("foo").greaterEqual(9);
    ASSERT_EQUAL(1, Predicate::compare(*s1, *builder.build()));
    builder.feature("foo").greaterEqual(11);
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));

    builder.feature("foo").lessEqual(10);
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));
}

TEST("require that predicate 'not' slimes can be compared") {
    PredicateSlimeBuilder builder;
    builder.neg().feature("foo").range(0, 10);
    SlimeUP s1 = builder.build();
    builder.neg().feature("foo").range(0, 10);
    ASSERT_EQUAL(0, Predicate::compare(*s1, *builder.build()));

    builder.neg().feature("foo").range(0, 11);
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));

    builder.feature("foo").range(0, 10);
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));
}

TEST("require that predicate 'and' slimes can be compared") {
    PredicateSlimeBuilder builder;
    SlimeUP s1 = builder.feature("foo").value("bar").value("baz").build();
    SlimeUP s2 = builder.feature("foo").value("bar").value("qux").build();
    SlimeUP and_node = builder.and_node(std::move(s1), std::move(s2)).build();

    s1 = builder.feature("foo").value("bar").value("baz").build();
    s2 = builder.feature("foo").value("bar").value("qux").build();
    builder.and_node(std::move(s1), std::move(s2));
    ASSERT_EQUAL(0, Predicate::compare(*and_node, *builder.build()));

    s1 = builder.feature("foo").value("bar").value("baz").build();
    s2 = builder.feature("foo").value("bar").value("qux").build();
    builder.and_node(std::move(s2), std::move(s1));
    ASSERT_EQUAL(-1, Predicate::compare(*and_node, *builder.build()));
}

TEST("require that predicate 'or' slimes can be compared") {
    PredicateSlimeBuilder builder;
    SlimeUP s1 = builder.feature("foo").value("bar").value("baz").build();
    SlimeUP s2 = builder.feature("foo").value("bar").value("qux").build();
    SlimeUP or_node = builder.or_node(std::move(s1), std::move(s2)).build();

    s1 = builder.feature("foo").value("bar").value("baz").build();
    s2 = builder.feature("foo").value("bar").value("qux").build();
    builder.or_node(std::move(s1), std::move(s2));
    ASSERT_EQUAL(0, Predicate::compare(*or_node, *builder.build()));

    s1 = builder.feature("foo").value("bar").value("baz").build();
    s2 = builder.feature("foo").value("bar").value("qux").build();
    builder.or_node(std::move(s2), std::move(s1));
    ASSERT_EQUAL(-1, Predicate::compare(*or_node, *builder.build()));
}

TEST("require that predicate 'true' slimes can be compared") {
    PredicateSlimeBuilder builder;
    builder.true_predicate();
    SlimeUP s1 = builder.build();
    builder.true_predicate();
    ASSERT_EQUAL(0, Predicate::compare(*s1, *builder.build()));

    builder.false_predicate();
    ASSERT_EQUAL(-1, Predicate::compare(*s1, *builder.build()));
}

TEST("require that predicate 'false' slimes can be compared") {
    PredicateSlimeBuilder builder;
    builder.false_predicate();
    SlimeUP s1 = builder.build();
    builder.false_predicate();
    ASSERT_EQUAL(0, Predicate::compare(*s1, *builder.build()));

    builder.true_predicate();
    ASSERT_EQUAL(1, Predicate::compare(*s1, *builder.build()));
}

TEST("require that feature set can be created") {
    const string feature_name = "feature name";
    Slime input;
    Cursor &obj = input.setObject();
    obj.setString(Predicate::KEY, feature_name);
    Cursor &arr = obj.setArray(Predicate::SET);
    arr.addString("foo");
    arr.addString("bar");
    FeatureSet set(input.get());
    EXPECT_EQUAL(feature_name, set.getKey());
    ASSERT_EQUAL(2u, set.getSize());
    EXPECT_EQUAL("foo", set[0]);
    EXPECT_EQUAL("bar", set[1]);
}

TEST("require that feature range can be created") {
    const string feature_name = "feature name";
    const long min = 0;
    const long max = 42;
    Slime input;
    Cursor &obj = input.setObject();
    obj.setString(Predicate::KEY, feature_name);
    obj.setLong(Predicate::RANGE_MIN, min);
    obj.setLong(Predicate::RANGE_MAX, max);
    FeatureRange set(input.get());
    EXPECT_EQUAL(feature_name, set.getKey());
    EXPECT_TRUE(set.hasMin());
    EXPECT_TRUE(set.hasMax());
    EXPECT_EQUAL(min, set.getMin());
    EXPECT_EQUAL(max, set.getMax());
}

TEST("require that feature range can be open") {
    const string feature_name = "feature name";
    Slime input;
    Cursor &obj = input.setObject();
    obj.setString(Predicate::KEY, feature_name);
    FeatureRange set(input.get());
    EXPECT_EQUAL(feature_name, set.getKey());
    EXPECT_FALSE(set.hasMin());
    EXPECT_FALSE(set.hasMax());
    EXPECT_EQUAL(LLONG_MIN, set.getMin());
    EXPECT_EQUAL(LLONG_MAX, set.getMax());
}

PredicateNode::UP getPredicateNode() {
    const string feature_name = "feature name";
    Slime input;
    Cursor &obj = input.setObject();
    obj.setString(Predicate::KEY, feature_name);
    Cursor &arr = obj.setArray(Predicate::SET);
    arr.addString("foo");
    arr.addString("bar");

    PredicateNode::UP node(new FeatureSet(input.get()));
    return node;
}

TEST("require that negation nodes holds a child") {
    PredicateNode::UP node(getPredicateNode());
    PredicateNode *expected = node.get();
    Negation neg(std::move(node));

    EXPECT_EQUAL(expected, &neg.getChild());
}

TEST("require that conjunction nodes holds several children") {
    vector<PredicateNode *> nodes;
    nodes.push_back(getPredicateNode().release());
    nodes.push_back(getPredicateNode().release());
    Conjunction and_node(nodes);

    ASSERT_EQUAL(2u, and_node.getSize());
    EXPECT_EQUAL(nodes[0], and_node[0]);
    EXPECT_EQUAL(nodes[1], and_node[1]);
}

TEST("require that disjunction nodes holds several children") {
    vector<PredicateNode *> nodes;
    nodes.push_back(getPredicateNode().release());
    nodes.push_back(getPredicateNode().release());
    Disjunction or_node(nodes);

    ASSERT_EQUAL(2u, or_node.getSize());
    EXPECT_EQUAL(nodes[0], or_node[0]);
    EXPECT_EQUAL(nodes[1], or_node[1]);
}


}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
