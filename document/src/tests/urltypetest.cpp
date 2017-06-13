// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/datatype/urldatatype.h>
#include <cppunit/extensions/HelperMacros.h>

namespace document {

class UrlTypeTest : public CppUnit::TestFixture {
public:
    void requireThatNameIsCorrect();
    void requireThatExpectedFieldsAreThere();

    CPPUNIT_TEST_SUITE(UrlTypeTest);
    CPPUNIT_TEST(requireThatNameIsCorrect);
    CPPUNIT_TEST(requireThatExpectedFieldsAreThere);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(UrlTypeTest);

void
UrlTypeTest::requireThatNameIsCorrect()
{
    const StructDataType &type = UrlDataType::getInstance();
    CPPUNIT_ASSERT_EQUAL(vespalib::string("url"), type.getName());
}

void
UrlTypeTest::requireThatExpectedFieldsAreThere()
{
    const StructDataType &type = UrlDataType::getInstance();
    Field field = type.getField("all");
    CPPUNIT_ASSERT_EQUAL(*DataType::STRING, field.getDataType());

    field = type.getField("scheme");
    CPPUNIT_ASSERT_EQUAL(*DataType::STRING, field.getDataType());

    field = type.getField("host");
    CPPUNIT_ASSERT_EQUAL(*DataType::STRING, field.getDataType());

    field = type.getField("port");
    CPPUNIT_ASSERT_EQUAL(*DataType::STRING, field.getDataType());

    field = type.getField("path");
    CPPUNIT_ASSERT_EQUAL(*DataType::STRING, field.getDataType());

    field = type.getField("query");
    CPPUNIT_ASSERT_EQUAL(*DataType::STRING, field.getDataType());

    field = type.getField("fragment");
    CPPUNIT_ASSERT_EQUAL(*DataType::STRING, field.getDataType());
}

} // document
