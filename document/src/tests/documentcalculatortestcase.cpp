// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/document/base/documentcalculator.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/bytefieldvalue.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/longfieldvalue.h>
#include <vespa/document/fieldvalue/floatfieldvalue.h>
#include <vespa/document/select/variablemap.h>
#include <vespa/vespalib/util/exceptions.h>

namespace document {

class DocumentCalculatorTest : public CppUnit::TestFixture {
    TestDocRepo _testRepo;

public:
    const DocumentTypeRepo &getRepo() { return _testRepo.getTypeRepo(); }

    void setUp() override {}
    void tearDown() override {}

    void testConstant();
    void testSimple();
    void testVariables();
    void testFields();
    void testDivideByZero();
    void testModByZero();
    void testFieldsDivZero();
    void testFieldNotSet();
    void testFieldNotFound();
    void testByteSubtractionZeroResult();

    CPPUNIT_TEST_SUITE(DocumentCalculatorTest);
    CPPUNIT_TEST(testConstant);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testVariables);
    CPPUNIT_TEST(testFields);
    CPPUNIT_TEST(testDivideByZero);
    CPPUNIT_TEST(testModByZero);
    CPPUNIT_TEST(testFieldsDivZero);
    CPPUNIT_TEST(testFieldNotSet);
    CPPUNIT_TEST(testFieldNotFound);
    CPPUNIT_TEST(testByteSubtractionZeroResult);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DocumentCalculatorTest);


void
DocumentCalculatorTest::testConstant() {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "4.0");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    CPPUNIT_ASSERT_EQUAL(4.0, calc.evaluate(doc, std::move(variables)));
}

void
DocumentCalculatorTest::testSimple() {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "(3 + 5) / 2");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    CPPUNIT_ASSERT_EQUAL(4.0, calc.evaluate(doc, std::move(variables)));
}

void
DocumentCalculatorTest::testVariables() {
    auto variables = std::make_unique<select::VariableMap>();
    (*variables)["x"] = 3.0;
    (*variables)["y"] = 5.0;
    DocumentCalculator calc(getRepo(), "($x + $y) / 2");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    CPPUNIT_ASSERT_EQUAL(4.0, calc.evaluate(doc, std::move(variables)));
}

void
DocumentCalculatorTest::testFields() {
    auto variables = std::make_unique<select::VariableMap>();
    (*variables)["x"] = 3.0;
    (*variables)["y"] = 5.0;
    DocumentCalculator calc(getRepo(), "(testdoctype1.headerval + testdoctype1"
                            ".hfloatval) / testdoctype1.headerlongval");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    doc.setValue(doc.getField("headerval"), IntFieldValue(5));
    doc.setValue(doc.getField("hfloatval"), FloatFieldValue(3.0));
    doc.setValue(doc.getField("headerlongval"), LongFieldValue(2));
    CPPUNIT_ASSERT_EQUAL(4.0, calc.evaluate(doc, std::move(variables)));
}

void
DocumentCalculatorTest::testFieldsDivZero() {
    auto variables = std::make_unique<select::VariableMap>();
    (*variables)["x"] = 3.0;
    (*variables)["y"] = 5.0;
    DocumentCalculator calc(getRepo(), "(testdoctype1.headerval + testdoctype1"
                            ".hfloatval) / testdoctype1.headerlongval");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    doc.setValue(doc.getField("headerval"), IntFieldValue(5));
    doc.setValue(doc.getField("hfloatval"), FloatFieldValue(3.0));
    doc.setValue(doc.getField("headerlongval"), LongFieldValue(0));
    try {
        calc.evaluate(doc, std::move(variables));
        CPPUNIT_ASSERT(false);
    } catch (const vespalib::IllegalArgumentException& e) {
        // OK
    }
}

void
DocumentCalculatorTest::testDivideByZero() {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "(3 + 5) / 0");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    try {
        calc.evaluate(doc, std::move(variables));
        CPPUNIT_ASSERT(false);
    } catch (const vespalib::IllegalArgumentException& e) {
        // OK
    }
}

void
DocumentCalculatorTest::testModByZero() {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "(3 + 5) % 0");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    try {
        calc.evaluate(doc, std::move(variables));
        CPPUNIT_ASSERT(false);
    } catch (const vespalib::IllegalArgumentException& e) {
        // OK
    }
}

void
DocumentCalculatorTest::testFieldNotSet() {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "(testdoctype1.headerval + testdoctype1"
                            ".hfloatval) / testdoctype1.headerlongval");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    doc.setValue(doc.getField("hfloatval"), FloatFieldValue(3.0));
    doc.setValue(doc.getField("headerlongval"), LongFieldValue(2));
    try {
        calc.evaluate(doc, std::move(variables));
        CPPUNIT_ASSERT(false);
    } catch (const vespalib::IllegalArgumentException&) {
        // OK
    }
}

void
DocumentCalculatorTest::testFieldNotFound() {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(),
                            "(testdoctype1.mynotfoundfield + testdoctype1"
                            ".hfloatval) / testdoctype1.headerlongval");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    doc.setValue(doc.getField("hfloatval"), FloatFieldValue(3.0));
    doc.setValue(doc.getField("headerlongval"), LongFieldValue(2));
    try {
        calc.evaluate(doc, std::move(variables));
        CPPUNIT_ASSERT(false);
    } catch (const vespalib::IllegalArgumentException&) {
        // OK
    }
}

void
DocumentCalculatorTest::testByteSubtractionZeroResult() {
    auto variables = std::make_unique<select::VariableMap>();
    DocumentCalculator calc(getRepo(), "testdoctype1.byteval - 3");

    Document doc(*_testRepo.getDocumentType("testdoctype1"), DocumentId("doc:test:foo"));
    doc.setValue(doc.getField("byteval"), ByteFieldValue(3));
    CPPUNIT_ASSERT_EQUAL(0.0, calc.evaluate(doc, std::move(variables)));
}

}
