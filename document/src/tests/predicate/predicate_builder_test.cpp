// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicate_builder.

#include <vespa/log/log.h>
LOG_SETUP("predicate_builder_test");

#include <vespa/document/predicate/predicate.h>
#include <vespa/document/predicate/predicate_builder.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/testkit/testapp.h>

using std::string;
using vespalib::Slime;
using vespalib::slime::Cursor;
using namespace document;

namespace {

TEST("require that a predicate tree can be built from a slime object") {
    const string feature_name = "feature name";
    Slime input;
    Cursor &obj = input.setObject();
    obj.setLong(Predicate::NODE_TYPE, Predicate::TYPE_DISJUNCTION);
    Cursor &children = obj.setArray(Predicate::CHILDREN);
    {
        Cursor &child = children.addObject();
        child.setLong(Predicate::NODE_TYPE, Predicate::TYPE_FEATURE_SET);
        child.setString(Predicate::KEY, feature_name);
        Cursor &arr = child.setArray(Predicate::SET);
        arr.addString("foo");
        arr.addString("bar");
    }
    {
        Cursor &child = children.addObject();
        child.setLong(Predicate::NODE_TYPE, Predicate::TYPE_CONJUNCTION);
        Cursor &and_children = child.setArray(Predicate::CHILDREN);
        {
            Cursor &and_child = and_children.addObject();
            and_child.setLong(Predicate::NODE_TYPE,
                              Predicate::TYPE_FEATURE_RANGE);
            and_child.setString(Predicate::KEY, feature_name);
            and_child.setLong(Predicate::RANGE_MIN, 42);
        }
        {
            Cursor &and_child = and_children.addObject();
            and_child.setLong(Predicate::NODE_TYPE, Predicate::TYPE_NEGATION);
            Cursor &not_child =
                and_child.setArray(Predicate::CHILDREN).addObject();
            {
                not_child.setLong(Predicate::NODE_TYPE,
                                  Predicate::TYPE_FEATURE_SET);
                not_child.setString(Predicate::KEY, feature_name);
                Cursor &arr = not_child.setArray(Predicate::SET);
                arr.addString("baz");
                arr.addString("qux");
            }
        }
    }

    PredicateNode::UP node = PredicateBuilder().build(input.get());
    Disjunction *disjunction = dynamic_cast<Disjunction *>(node.get());
    ASSERT_TRUE(disjunction);
    ASSERT_EQUAL(2u, disjunction->getSize());
    FeatureSet *feature_set = dynamic_cast<FeatureSet *>((*disjunction)[0]);
    ASSERT_TRUE(feature_set);
    Conjunction *conjunction = dynamic_cast<Conjunction *>((*disjunction)[1]);
    ASSERT_TRUE(conjunction);
    ASSERT_EQUAL(2u, conjunction->getSize());
    FeatureRange *feature_range =
        dynamic_cast<FeatureRange *>((*conjunction)[0]);
    ASSERT_TRUE(feature_range);
    Negation *negation = dynamic_cast<Negation *>((*conjunction)[1]);
    ASSERT_TRUE(negation);
    feature_set = dynamic_cast<FeatureSet *>(&negation->getChild());
    ASSERT_TRUE(feature_set);
}

}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
