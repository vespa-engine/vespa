// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/datatype/positiondatatype.h>
#include <gtest/gtest.h>

namespace document {

TEST(PositionTypeTest, requireThatNameIsCorrect)
{
    const StructDataType &type = PositionDataType::getInstance();
    EXPECT_EQ(vespalib::string("position"), type.getName());
}

TEST(PositionTypeTest, requireThatExpectedFieldsAreThere)
{
    const StructDataType &type = PositionDataType::getInstance();
    Field field = type.getField("x");
    EXPECT_EQ(*DataType::INT, field.getDataType());

    field = type.getField("y");
    EXPECT_EQ(*DataType::INT, field.getDataType());
}

TEST(PositionTypeTest, requireThatZCurveFieldMatchesJava)
{
    EXPECT_EQ(vespalib::string("foo_zcurve"), PositionDataType::getZCurveFieldName("foo"));
    EXPECT_TRUE( ! PositionDataType::isZCurveFieldName("foo"));
    EXPECT_TRUE( ! PositionDataType::isZCurveFieldName("_zcurve"));
    EXPECT_TRUE( PositionDataType::isZCurveFieldName("x_zcurve"));
    EXPECT_TRUE( ! PositionDataType::isZCurveFieldName("x_zcurvex"));
    EXPECT_EQ(vespalib::stringref("x"), PositionDataType::cutZCurveFieldName("x_zcurve"));
}

} // document
