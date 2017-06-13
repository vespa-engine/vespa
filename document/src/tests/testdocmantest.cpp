// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/* $Id$*/

#include <iostream>
#include <set>
#include <sstream>
#include <vespa/document/base/testdocman.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace document {

class TestDocManTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(TestDocManTest);
    CPPUNIT_TEST(testSimpleUsage);
    CPPUNIT_TEST_SUITE_END();

protected:
    void testSimpleUsage();
};

CPPUNIT_TEST_SUITE_REGISTRATION(TestDocManTest);

void TestDocManTest::testSimpleUsage()
{
    TestDocMan testdm;
    Document::UP doc1(testdm.createRandomDocument());
    Document::UP doc2(testdm.createRandomDocument());
    Document::UP doc3(testdm.createRandomDocument(1));
    {
        FieldValue::UP v(doc1->getValue(doc1->getField("content")));
        StringFieldValue& sval(dynamic_cast<StringFieldValue&>(*v));
        CPPUNIT_ASSERT_EQUAL(std::string("To be, or "),
                             std::string(sval.getValue().c_str()));

        FieldValue::UP v2(doc2->getValue(doc2->getField("content")));
        StringFieldValue& sval2(dynamic_cast<StringFieldValue&>(*v));
        CPPUNIT_ASSERT_EQUAL(std::string(sval.getValue().c_str()),
                             std::string(sval2.getValue().c_str()));
    }
    {
        FieldValue::UP v(doc3->getValue(doc3->getField("content")));
        StringFieldValue& sval(dynamic_cast<StringFieldValue&>(*v));
        CPPUNIT_ASSERT_EQUAL(
                std::string("To be, or not to be: that is the question:\n"
                            "Whether 'tis nobler in the mind to suffer\n"
                            "The slings and a"),
                std::string(sval.getValue().c_str()));
    }
    CPPUNIT_ASSERT_EQUAL(
            vespalib::string("id:mail:testdoctype1:n=51019:192.html"),
            doc1->getId().toString());
    CPPUNIT_ASSERT_EQUAL(
            vespalib::string("id:mail:testdoctype1:n=51019:192.html"),
            doc2->getId().toString());
    CPPUNIT_ASSERT_EQUAL(
            vespalib::string("id:mail:testdoctype1:n=10744:245.html"),
            doc3->getId().toString());
}

} // document
