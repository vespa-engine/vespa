// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/datatype/urldatatype.h>
#include <gtest/gtest.h>

namespace document {

TEST(UrlTypeTest, requireThatNameIsCorrect)
{
    const StructDataType &type = UrlDataType::getInstance();
    EXPECT_EQ(vespalib::string("url"), type.getName());
}

TEST(UrlTypeTest, requireThatExpectedFieldsAreThere)
{
    const StructDataType &type = UrlDataType::getInstance();
    Field field = type.getField("all");
    EXPECT_EQ(*DataType::STRING, field.getDataType());

    field = type.getField("scheme");
    EXPECT_EQ(*DataType::STRING, field.getDataType());

    field = type.getField("host");
    EXPECT_EQ(*DataType::STRING, field.getDataType());

    field = type.getField("port");
    EXPECT_EQ(*DataType::STRING, field.getDataType());

    field = type.getField("path");
    EXPECT_EQ(*DataType::STRING, field.getDataType());

    field = type.getField("query");
    EXPECT_EQ(*DataType::STRING, field.getDataType());

    field = type.getField("fragment");
    EXPECT_EQ(*DataType::STRING, field.getDataType());
}

} // document
