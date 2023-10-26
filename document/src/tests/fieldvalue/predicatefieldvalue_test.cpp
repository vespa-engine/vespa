// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
// Unit tests for predicatefieldvalue.

#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/predicatefieldvalue.h>
#include <vespa/document/predicate/predicate_slime_builder.h>
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
    ASSERT_EQUAL(o1.str(), o2.str());
}

TEST("require that PredicateFieldValue can be cloned, assigned") {
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

TEST("require that PredicateFieldValue can be created from datatype") {
    FieldValue::UP val = DataType::PREDICATE->createFieldValue();
    ASSERT_TRUE(dynamic_cast<PredicateFieldValue *>(val.get()));
}

TEST("require that PredicateFieldValue can be cloned") {
    PredicateSlimeBuilder builder;
    builder.neg().feature("foo").value("bar").value("baz");
    PredicateFieldValue val(builder.build());
    FieldValue::UP val2(val.clone());
    ostringstream o1;
    val.print(o1, false, "");
    ostringstream o2;
    val2->print(o2, false, "");
    ASSERT_EQUAL(o1.str(), o2.str());
}


}  // namespace

TEST_MAIN() { TEST_RUN_ALL(); }
