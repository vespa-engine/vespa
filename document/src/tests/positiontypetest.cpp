// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/datatype/positiondatatype.h>
#include <cppunit/extensions/HelperMacros.h>

namespace document {

class PositionTypeTest : public CppUnit::TestFixture {
public:
    void requireThatNameIsCorrect();
    void requireThatExpectedFieldsAreThere();
    void requireThatZCurveFieldMatchesJava();

    CPPUNIT_TEST_SUITE(PositionTypeTest);
    CPPUNIT_TEST(requireThatNameIsCorrect);
    CPPUNIT_TEST(requireThatExpectedFieldsAreThere);
    CPPUNIT_TEST(requireThatZCurveFieldMatchesJava);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(PositionTypeTest);

void
PositionTypeTest::requireThatNameIsCorrect()
{
    const StructDataType &type = PositionDataType::getInstance();
    CPPUNIT_ASSERT_EQUAL(vespalib::string("position"), type.getName());
}

void
PositionTypeTest::requireThatExpectedFieldsAreThere()
{
    const StructDataType &type = PositionDataType::getInstance();
    Field field = type.getField("x");
    CPPUNIT_ASSERT_EQUAL(*DataType::INT, field.getDataType());

    field = type.getField("y");
    CPPUNIT_ASSERT_EQUAL(*DataType::INT, field.getDataType());
}

void
PositionTypeTest::requireThatZCurveFieldMatchesJava()
{
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo_zcurve"),
                         PositionDataType::getZCurveFieldName("foo"));
    CPPUNIT_ASSERT( ! PositionDataType::isZCurveFieldName("foo"));
    CPPUNIT_ASSERT( ! PositionDataType::isZCurveFieldName("_zcurve"));
    CPPUNIT_ASSERT( PositionDataType::isZCurveFieldName("x_zcurve"));
    CPPUNIT_ASSERT( ! PositionDataType::isZCurveFieldName("x_zcurvex"));
    CPPUNIT_ASSERT_EQUAL(vespalib::stringref("x"), PositionDataType::cutZCurveFieldName("x_zcurve"));
}

} // document
