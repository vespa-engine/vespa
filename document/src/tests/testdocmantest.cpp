// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

/* $Id$*/

#include <vespa/document/base/testdocman.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>

#include <gtest/gtest.h>

namespace document {

TEST(TestDocManTest, testSimpleUsage)
{
    TestDocMan testdm;
    Document::UP doc1(testdm.createRandomDocument());
    Document::UP doc2(testdm.createRandomDocument());
    Document::UP doc3(testdm.createRandomDocument(1));
    {
        FieldValue::UP v(doc1->getValue(doc1->getField("content")));
        StringFieldValue& sval(dynamic_cast<StringFieldValue&>(*v));
        EXPECT_EQ(std::string("To be, or "),
                  std::string(sval.getValue().c_str()));

        FieldValue::UP v2(doc2->getValue(doc2->getField("content")));
        StringFieldValue& sval2(dynamic_cast<StringFieldValue&>(*v));
        EXPECT_EQ(std::string(sval.getValue().c_str()),
                  std::string(sval2.getValue().c_str()));
    }
    {
        FieldValue::UP v(doc3->getValue(doc3->getField("content")));
        StringFieldValue& sval(dynamic_cast<StringFieldValue&>(*v));
        EXPECT_EQ(
                std::string("To be, or not to be: that is the question:\n"
                            "Whether 'tis nobler in the mind to suffer\n"
                            "The slings and a"),
                std::string(sval.getValue().c_str()));
    }
    EXPECT_EQ(
            vespalib::string("id:mail:testdoctype1:n=51019:192.html"),
            doc1->getId().toString());
    EXPECT_EQ(
            vespalib::string("id:mail:testdoctype1:n=51019:192.html"),
            doc2->getId().toString());
    EXPECT_EQ(
            vespalib::string("id:mail:testdoctype1:n=10744:245.html"),
            doc3->getId().toString());
}

} // document
