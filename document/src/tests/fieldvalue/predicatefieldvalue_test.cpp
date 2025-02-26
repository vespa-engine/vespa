// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicatefieldvalue.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>

#include <vespa/log/log.h>
LOG_SETUP("predicatefieldvalue_test");

using std::ostringstream;
using std::string;
using vespalib::Slime;
using namespace document;

namespace {

void verifyEqual(const FieldValue & a, const FieldValue & b) {
    ostringstream o1;
    a.print(o1, false, "");
    ostringstream o2;
    b.print(o2, false, "");
    ASSERT_EQ(o1.str(), o2.str());
}

TEST(PredicateFieldValueTest, require_that_PredicateFieldValue_can_be_cloned_and_assigned)
{
    PredicateSlimeBuilder builder;
    builder.neg().feature("foo").value("bar").value("baz");
    PredicateFieldValue val(builder.build());

    FieldValue::UP val2(val.clone());
    verifyEqual(val, *val2);

    PredicateFieldValue assigned;
    assigned.assign(val);
    verifyEqual(val, assigned);

    PredicateFieldValue operatorAssigned;
    operatorAssigned = std::move(assigned);
    verifyEqual(val, operatorAssigned);
}

TEST(PredicateFieldValueTest, require_that_PredicateFieldValue_can_be_created_from_datatype)
{
    FieldValue::UP val = DataType::PREDICATE->createFieldValue();
    ASSERT_TRUE(dynamic_cast<PredicateFieldValue *>(val.get()));
}

TEST(PredicateFieldValueTest, require_that_PredicateFieldValue_can_be_cloned)
{
    PredicateSlimeBuilder builder;
    builder.neg().feature("foo").value("bar").value("baz");
    PredicateFieldValue val(builder.build());
    FieldValue::UP val2(val.clone());
    ostringstream o1;
    val.print(o1, false, "");
    ostringstream o2;
    val2->print(o2, false, "");
    ASSERT_EQ(o1.str(), o2.str());
}

}  // namespace

GTEST_MAIN_RUN_ALL_TESTS()
