// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentcalculator.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/select/variablemap.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/exceptions.h>

namespace document {

class DocumentCalculatorTest : public ::testing::Test {
public:
    TestDocRepo _testRepo;
    const DocumentTypeRepo &getRepo() { return _testRepo.getTypeRepo(); }
};

TEST_F(DocumentCalculatorTest, testConstant) {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "4.0");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    EXPECT_EQ(4.0, calc.evaluate(doc, std::move(variables)));
}

TEST_F(DocumentCalculatorTest, testSimple) {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "(3 + 5) / 2");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    EXPECT_EQ(4.0, calc.evaluate(doc, std::move(variables)));
}

TEST_F(DocumentCalculatorTest, testVariables) {
    auto variables = std::make_unique<select::VariableMap>();
    (*variables)["x"] = 3.0;
    (*variables)["y"] = 5.0;
    DocumentCalculator calc(getRepo(), "($x + $y) / 2");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    EXPECT_EQ(4.0, calc.evaluate(doc, std::move(variables)));
}

TEST_F(DocumentCalculatorTest, testFields) {
    auto variables = std::make_unique<select::VariableMap>();
    (*variables)["x"] = 3.0;
    (*variables)["y"] = 5.0;
    DocumentCalculator calc(getRepo(), "(testdoctype1.headerval + testdoctype1"
                            ".hfloatval) / testdoctype1.headerlongval");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    doc.setValue(doc.getField("headerval"), IntFieldValue(5));
    doc.setValue(doc.getField("hfloatval"), FloatFieldValue(3.0));
    doc.setValue(doc.getField("headerlongval"), LongFieldValue(2));
    EXPECT_EQ(4.0, calc.evaluate(doc, std::move(variables)));
}

TEST_F(DocumentCalculatorTest, testFieldsDivZero) {
    auto variables = std::make_unique<select::VariableMap>();
    (*variables)["x"] = 3.0;
    (*variables)["y"] = 5.0;
    DocumentCalculator calc(getRepo(), "(testdoctype1.headerval + testdoctype1"
                            ".hfloatval) / testdoctype1.headerlongval");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    doc.setValue(doc.getField("headerval"), IntFieldValue(5));
    doc.setValue(doc.getField("hfloatval"), FloatFieldValue(3.0));
    doc.setValue(doc.getField("headerlongval"), LongFieldValue(0));
    EXPECT_THROW(calc.evaluate(doc, std::move(variables)),
                 vespalib::IllegalArgumentException);
}

TEST_F(DocumentCalculatorTest, testDivideByZero) {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "(3 + 5) / 0");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    EXPECT_THROW(calc.evaluate(doc, std::move(variables)),
                 vespalib::IllegalArgumentException);
}

TEST_F(DocumentCalculatorTest, testModByZero) {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "(3 + 5) % 0");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    EXPECT_THROW(calc.evaluate(doc, std::move(variables)),
                 vespalib::IllegalArgumentException);
}

TEST_F(DocumentCalculatorTest, testFieldNotSet) {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "(testdoctype1.headerval + testdoctype1"
                            ".hfloatval) / testdoctype1.headerlongval");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    doc.setValue(doc.getField("hfloatval"), FloatFieldValue(3.0));
    doc.setValue(doc.getField("headerlongval"), LongFieldValue(2));
    EXPECT_THROW(calc.evaluate(doc, std::move(variables)),
                 vespalib::IllegalArgumentException);
}

TEST_F(DocumentCalculatorTest, testFieldNotFound) {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(),
                            "(testdoctype1.mynotfoundfield + testdoctype1"
                            ".hfloatval) / testdoctype1.headerlongval");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    doc.setValue(doc.getField("hfloatval"), FloatFieldValue(3.0));
    doc.setValue(doc.getField("headerlongval"), LongFieldValue(2));
    EXPECT_THROW(calc.evaluate(doc, std::move(variables)),
                 vespalib::IllegalArgumentException);
}

TEST_F(DocumentCalculatorTest, testByteSubtractionZeroResult) {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "testdoctype1.byteval - 3");

    Document doc(_testRepo.getTypeRepo(), *_testRepo.getDocumentType("testdoctype1"), DocumentId("id:ns:testdoctype1::foo"));
    doc.setValue(doc.getField("byteval"), ByteFieldValue(3));
    EXPECT_EQ(0.0, calc.evaluate(doc, std::move(variables)));
}

}
