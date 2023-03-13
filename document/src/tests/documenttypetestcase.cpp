// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using document::config_builder::Struct;
using document::config_builder::Wset;
using document::config_builder::Array;
using document::config_builder::Map;
using namespace ::testing;


namespace document {

TEST(DocumentTypeTest, testSetGet)
{
  DocumentType docType("doctypetestdoc", 0);

  docType.addField(Field("stringattr", 3, *DataType::STRING));
  docType.addField(Field("nalle", 0, *DataType::INT));

  const Field& fetch1 = docType.getField("stringattr");
  const Field& fetch2 = docType.getField("stringattr");

  EXPECT_TRUE(fetch1 == fetch2);
  EXPECT_TRUE(fetch1.getName() == "stringattr");

  const Field& fetch3 = docType.getField(3);

  EXPECT_TRUE(fetch1 == fetch3);

  const Field& fetch4 = docType.getField(0);

  EXPECT_TRUE(fetch4 != fetch1);
}

void
categorizeFields(const Field::Set& fields,
                 std::vector<const Field*>& headers)
{
    for (const Field * field : fields)
    {
        headers.push_back(field);
    }
}

TEST(DocumentTypeTest, testInheritanceConfig)
{
    DocumentTypeRepo
        repo(readDocumenttypesConfig(TEST_PATH("data/inheritancetest.cfg")));
    {
        const DocumentType* type(repo.getDocumentType("music"));
        EXPECT_TRUE(type != NULL);
    }

    {
        const DocumentType* type(repo.getDocumentType("books"));
        EXPECT_TRUE(type != NULL);
    }
}

TEST(DocumentTypeTest, testHeaderContent)
{
    DocumentTypeRepo
        repo(readDocumenttypesConfig(TEST_PATH("data/doctypesconfigtest.cfg")));

    const DocumentType* type(repo.getDocumentType("derived"));

    Field::Set fields = type->getFieldsType().getFieldSet();

    std::vector<const Field*> headers;
    categorizeFields(fields, headers);

    EXPECT_TRUE(headers.size() == 6);
    EXPECT_TRUE(headers[0]->getName() == "field1");
    EXPECT_TRUE(headers[1]->getName() == "field2");
    EXPECT_TRUE(headers[2]->getName() == "field3");
    EXPECT_TRUE(headers[3]->getName() == "field4");
    EXPECT_TRUE(headers[4]->getName() == "field5");
    EXPECT_TRUE(headers[5]->getName() == "fieldarray1");
}

TEST(DocumentTypeTest, testMultipleInheritance)
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

    EXPECT_TRUE(docType3->hasField("stringattr"));
    EXPECT_TRUE(docType3->hasField("nalle"));
    EXPECT_TRUE(docType3->hasField("tmp"));
    EXPECT_TRUE(docType3->hasField("tall"));

    Document doc(repo, *docType3, DocumentId("id:ns:test3::1"));

    IntFieldValue intVal(3);
    doc.setValue(doc.getField("nalle"), intVal);

    StringFieldValue stringVal("tmp");
    doc.setValue(doc.getField("tmp"), stringVal);

    EXPECT_TRUE(doc.getValue(doc.getField("nalle"))->getAsInt()==3);
    EXPECT_EQ(vespalib::string("tmp"),
              doc.getValue(doc.getField("tmp"))->getAsString());
}

namespace {

bool containsField(const DocumentType::FieldSet &fieldSet, const vespalib::string &field) {
    return fieldSet.getFields().find(field) != fieldSet.getFields().end();
}

}

TEST(DocumentTypeTest, testFieldSetCanContainFieldsNotInDocType)
{
    DocumentType docType("test1");
    docType.addField(Field("stringattr", 3, *DataType::STRING));
    docType.addField(Field("nalle", 0, *DataType::INT));
    {
        DocumentType::FieldSet::Fields tmp;
        tmp.insert("nalle");
        tmp.insert("nulle");
        docType.addFieldSet("a", tmp);
    }
    auto fieldSet = docType.getFieldSet("a");
    EXPECT_EQ((size_t)2, fieldSet->getFields().size());
    EXPECT_TRUE(containsField(*fieldSet, "nalle"));
    EXPECT_TRUE(containsField(*fieldSet, "nulle"));
}

TEST(DocumentTypeTest, testInheritance)
{
        // Inheritance of conflicting but equal datatype ok
    DocumentType docType("test1");
    docType.addField(Field("stringattr", 3, *DataType::STRING));
    docType.addField(Field("nalle", 0, *DataType::INT));

    DocumentType docType2("test2");
    docType2.addField(Field("stringattr", 3, *DataType::STRING));
    docType2.addField(Field("tmp", 5, *DataType::STRING));
    docType2.addField(Field("tall", 10, *DataType::INT));

    docType.inherit(docType2);
    EXPECT_TRUE(docType.hasField("stringattr"));
    EXPECT_TRUE(docType.hasField("nalle"));
    EXPECT_TRUE(docType.hasField("tmp"));
    EXPECT_TRUE(docType.hasField("tall"));

    DocumentType docType3("test3");
    docType3.addField(Field("stringattr", 3, *DataType::RAW));
    docType3.addField(Field("tall", 10, *DataType::INT));

    try{
        docType2.inherit(docType3);
        // FAIL() << "Supposed to fail";
    } catch (std::exception& e) {
        EXPECT_EQ(std::string("foo"), std::string(e.what()));
    }

    try{
        docType.inherit(docType3);
        // FAIL() << "Supposed to fail";
    } catch (std::exception& e) {
        EXPECT_EQ(std::string("foo"), std::string(e.what()));
    }

    DocumentType docType4("test4");
    docType4.inherit(docType3);

    EXPECT_TRUE(docType4.hasField("stringattr"));
    EXPECT_TRUE(docType4.hasField("tall"));

    try{
        docType3.inherit(docType4);
        FAIL() << "Accepted cyclic inheritance";
    } catch (std::exception& e) {
        EXPECT_THAT(e.what(), HasSubstr("Cannot add cyclic dependencies"));
    }

    DocumentType docType5("test5");
    docType5.addField(Field("stringattr", 20, *DataType::RAW));

    try{
        docType4.inherit(docType5);
        // FAIL() << "Supposed to fail";
    } catch (std::exception& e) {
        EXPECT_EQ(std::string("foo"), std::string(e.what()));
    }
}

TEST(DocumentTypeTest,testOutputOperator)
{
    DocumentType docType("test1");
    std::ostringstream ost;
    ost << docType;
    std::string expected("DocumentType(test1)");
    EXPECT_EQ(expected, ost.str());
}

} // document
