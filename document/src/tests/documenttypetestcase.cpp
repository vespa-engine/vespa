// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/exceptions.h>

using document::config_builder::Struct;
using document::config_builder::Wset;
using document::config_builder::Array;
using document::config_builder::Map;


namespace document {

struct DocumentTypeTest : public CppUnit::TestFixture {
    void setUp() override;
    void tearDown() override;

    void testSetGet();
    void testHeaderContent();
    void testFieldSetCanContainFieldsNotInDocType();
    void testInheritance();
    void testInheritanceConfig();
    void testMultipleInheritance();
    void testOutputOperator();

    CPPUNIT_TEST_SUITE( DocumentTypeTest);
    CPPUNIT_TEST(testSetGet);
    CPPUNIT_TEST(testFieldSetCanContainFieldsNotInDocType);
    CPPUNIT_TEST(testInheritance);
    CPPUNIT_TEST(testInheritanceConfig);
    CPPUNIT_TEST(testMultipleInheritance);
    CPPUNIT_TEST(testOutputOperator);
    CPPUNIT_TEST(testHeaderContent);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DocumentTypeTest);

void DocumentTypeTest::setUp()
{
}

void DocumentTypeTest::tearDown()
{
}

void DocumentTypeTest::testSetGet() {
  DocumentType docType("doctypetestdoc", 0);

  docType.addField(Field("stringattr", 3, *DataType::STRING, true));
  docType.addField(Field("nalle", 0, *DataType::INT, false));

  const Field& fetch1=docType.getField("stringattr");
  const Field& fetch2=docType.getField("stringattr");

  CPPUNIT_ASSERT(fetch1==fetch2);
  CPPUNIT_ASSERT(fetch1.getName() == "stringattr");

  const Field& fetch3 = docType.getField(3);

  CPPUNIT_ASSERT(fetch1==fetch3);

  const Field& fetch4=docType.getField(0);

  CPPUNIT_ASSERT(fetch4!=fetch1);
}

void
categorizeFields(const Field::Set& fields,
                 std::vector<const Field*>& headers,
                 std::vector<const Field*>& bodies)
{
    for (Field::Set::const_iterator it(fields.begin()), e(fields.end());
         it != e; ++it)
    {
        if ((*it)->isHeaderField()) {
            headers.push_back(*it);
        } else {
            bodies.push_back(*it);
        }
    }
}

void
DocumentTypeTest::testInheritanceConfig()
{
    DocumentTypeRepo
        repo(readDocumenttypesConfig(TEST_PATH("data/inheritancetest.cfg")));
    {
        const DocumentType* type(repo.getDocumentType("music"));
        CPPUNIT_ASSERT(type != NULL);
    }

    {
        const DocumentType* type(repo.getDocumentType("books"));
        CPPUNIT_ASSERT(type != NULL);
    }
}

void
DocumentTypeTest::testHeaderContent()
{
    DocumentTypeRepo
        repo(readDocumenttypesConfig(TEST_PATH("data/doctypesconfigtest.cfg")));

    const DocumentType* type(repo.getDocumentType("derived"));

    Field::Set fields = type->getFieldsType().getFieldSet();

    std::vector<const Field*> headers;
    std::vector<const Field*> bodies;
    categorizeFields(fields, headers, bodies);

    CPPUNIT_ASSERT(headers.size() == 4);
    CPPUNIT_ASSERT(headers[0]->getName() == "field1");
    CPPUNIT_ASSERT(headers[1]->getName() == "field2");
    CPPUNIT_ASSERT(headers[2]->getName() == "field4");
    CPPUNIT_ASSERT(headers[3]->getName() == "fieldarray1");

    CPPUNIT_ASSERT(bodies.size() == 2);
    CPPUNIT_ASSERT(bodies[0]->getName() == "field3");
    CPPUNIT_ASSERT(bodies[1]->getName() == "field5");
}

void DocumentTypeTest::testMultipleInheritance()
{
    config_builder::DocumenttypesConfigBuilderHelper builder;
    builder.document(42, "test1", Struct("test1.header"),
                     Struct("test1.body")
                     .addField("stringattr", DataType::T_STRING)
                     .addField("nalle", DataType::T_INT));
    builder.document(43, "test2", Struct("test2.header"),
                     Struct("test2.body")
                     .addField("stringattr", DataType::T_STRING)
                     .addField("tmp", DataType::T_STRING)
                     .addField("tall", DataType::T_INT));
    builder.document(44, "test3",
                     Struct("test3.header"), Struct("test3.body"))
        .inherit(42).inherit(43);
    DocumentTypeRepo repo(builder.config());
    const DocumentType* docType3(repo.getDocumentType("test3"));

    CPPUNIT_ASSERT(docType3->hasField("stringattr"));
    CPPUNIT_ASSERT(docType3->hasField("nalle"));
    CPPUNIT_ASSERT(docType3->hasField("tmp"));
    CPPUNIT_ASSERT(docType3->hasField("tall"));

    Document doc(*docType3, DocumentId(DocIdString("test", "test")));

    IntFieldValue intVal(3);
    doc.setValue(doc.getField("nalle"), intVal);

    StringFieldValue stringVal("tmp");
    doc.setValue(doc.getField("tmp"), stringVal);

    CPPUNIT_ASSERT(doc.getValue(doc.getField("nalle"))->getAsInt()==3);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("tmp"),
                         doc.getValue(doc.getField("tmp"))->getAsString());
}

namespace {

bool containsField(const DocumentType::FieldSet &fieldSet, const vespalib::string &field) {
    return fieldSet.getFields().find(field) != fieldSet.getFields().end();
}

}

void DocumentTypeTest::testFieldSetCanContainFieldsNotInDocType() {
    DocumentType docType("test1");
    docType.addField(Field("stringattr", 3, *DataType::STRING, false));
    docType.addField(Field("nalle", 0, *DataType::INT, false));
    {
        DocumentType::FieldSet::Fields tmp;
        tmp.insert("nalle");
        tmp.insert("nulle");
        docType.addFieldSet("a", tmp);
    }
    auto fieldSet = docType.getFieldSet("a");
    CPPUNIT_ASSERT_EQUAL((size_t)2, fieldSet->getFields().size());
    CPPUNIT_ASSERT(containsField(*fieldSet, "nalle"));
    CPPUNIT_ASSERT(containsField(*fieldSet, "nulle"));
}

void DocumentTypeTest::testInheritance()
{
        // Inheritance of conflicting but equal datatype ok
    DocumentType docType("test1");
    docType.addField(Field("stringattr", 3, *DataType::STRING, false));
    docType.addField(Field("nalle", 0, *DataType::INT, false));

    DocumentType docType2("test2");
    docType2.addField(Field("stringattr", 3, *DataType::STRING, false));
    docType2.addField(Field("tmp", 5, *DataType::STRING, false));
    docType2.addField(Field("tall", 10, *DataType::INT, false));

    docType.inherit(docType2);
    CPPUNIT_ASSERT(docType.hasField("stringattr"));
    CPPUNIT_ASSERT(docType.hasField("nalle"));
    CPPUNIT_ASSERT(docType.hasField("tmp"));
    CPPUNIT_ASSERT(docType.hasField("tall"));

    DocumentType docType3("test3");
    docType3.addField(Field("stringattr", 3, *DataType::RAW, false));
    docType3.addField(Field("tall", 10, *DataType::INT, false));

    try{
        docType2.inherit(docType3);
        //CPPUNIT_FAIL("Supposed to fail");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_EQUAL(std::string("foo"), std::string(e.what()));
    }

    try{
        docType.inherit(docType3);
        //CPPUNIT_FAIL("Supposed to fail");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_EQUAL(std::string("foo"), std::string(e.what()));
    }

    DocumentType docType4("test4");
    docType4.inherit(docType3);

    CPPUNIT_ASSERT(docType4.hasField("stringattr"));
    CPPUNIT_ASSERT(docType4.hasField("tall"));

    try{
        docType3.inherit(docType4);
        CPPUNIT_FAIL("Accepted cyclic inheritance");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_CONTAIN("Cannot add cyclic dependencies", e.what());
    }

    DocumentType docType5("test5");
    docType5.addField(Field("stringattr", 20, *DataType::RAW, false));

    try{
        docType4.inherit(docType5);
        //CPPUNIT_FAIL("Supposed to fail");
    } catch (std::exception& e) {
        CPPUNIT_ASSERT_EQUAL(std::string("foo"), std::string(e.what()));
    }
}

void DocumentTypeTest::testOutputOperator() {
    DocumentType docType("test1");
    std::ostringstream ost;
    ost << docType;
    std::string expected("DocumentType(test1)");
    CPPUNIT_ASSERT_EQUAL(expected, ost.str());
}

} // document
