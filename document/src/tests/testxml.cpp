// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/update/addvalueupdate.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/removevalueupdate.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/fieldvalues.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/document/util/bytebuffer.h>
#include <vespa/vespalib/testkit/test_kit.h>

using vespalib::StringTokenizer;

namespace document {

class TestXml : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(TestXml);
    CPPUNIT_TEST(testSimpleUsage);
    CPPUNIT_TEST(testDocumentUpdate);
    CPPUNIT_TEST_SUITE_END();

    void testSimpleUsage();
    void testDocumentUpdate();
};

CPPUNIT_TEST_SUITE_REGISTRATION(TestXml);

namespace {

Document::UP createTestDocument(const DocumentTypeRepo& repo)
{
    const DocumentType* type(repo.getDocumentType("testdoc"));
    Document::UP
        doc(new Document(*type,
                         DocumentId("doc:crawler/http://www.ntnu.no/")));
    doc->setRepo(repo);
    std::string s("humlepungens buffer");
    ByteBuffer bb(s.c_str(), s.size());

    doc->setValue(doc->getField("intattr"), IntFieldValue(50));
    doc->setValue(doc->getField("rawattr"), RawFieldValue("readable hei der", 7));
    doc->setValue(doc->getField("floatattr"), FloatFieldValue(3.56));
    doc->setValue(doc->getField("stringattr"), StringFieldValue("tjo hei"));

    doc->setValue(doc->getField("doubleattr"), DoubleFieldValue(17.78623142376453));
    doc->setValue(doc->getField("longattr"), LongFieldValue(346234765345239657LL));
    doc->setValue(doc->getField("byteattr"), ByteFieldValue('J'));

    ArrayFieldValue val(doc->getField("rawarrayattr").getDataType());
    RawFieldValue rawVal("readable hei", 3);
    val.add(rawVal);
    RawFieldValue rawVal2("readable hallo", 5);
    val.add(rawVal2);
    RawFieldValue rawVal3("readable hei der", 7);
    val.add(rawVal3);
    doc->setValue(doc->getField("rawarrayattr"), val);

    Document::UP doc2(new Document(*type, DocumentId(
                            "doc:crawler/http://www.ntnu.no/2")));
    doc2->setValue(doc2->getField("stringattr"), StringFieldValue("tjo hei paa du"));
    doc->setValue(doc->getField("docfield"), *doc2);

    return doc;
}

DocumentUpdate::UP
createTestDocumentUpdate(const DocumentTypeRepo& repo)
{
    const DocumentType* type(repo.getDocumentType("testdoc"));
    DocumentId id("doc:crawler/http://www.ntnu.no/");

    DocumentUpdate::UP up(new DocumentUpdate(*type, id));
    up->addUpdate(FieldUpdate(type->getField("intattr"))
		  .addUpdate(AssignValueUpdate(IntFieldValue(7))));
    up->addUpdate(FieldUpdate(type->getField("stringattr"))
		  .addUpdate(AssignValueUpdate(
                                  StringFieldValue("New value"))));
    up->addUpdate(FieldUpdate(type->getField("arrayattr"))
		  .addUpdate(AddValueUpdate(IntFieldValue(123)))
		  .addUpdate(AddValueUpdate(IntFieldValue(456))));
    up->addUpdate(FieldUpdate(type->getField("arrayattr"))
		  .addUpdate(RemoveValueUpdate(IntFieldValue(123)))
		  .addUpdate(RemoveValueUpdate(IntFieldValue(456)))
		  .addUpdate(RemoveValueUpdate(IntFieldValue(789))));
    return up;
}

} // anonymous ns

void TestXml::testSimpleUsage()
{
    DocumentTypeRepo repo(readDocumenttypesConfig(TEST_PATH("data/defaultdoctypes.cfg")));
    Document::UP doc1(createTestDocument(repo));
    doc1->setValue(doc1->getField("stringattr"), StringFieldValue("tjohei���"));

    std::string expected =
        "<document documenttype=\"testdoc\" documentid=\"doc:crawler/http://www.ntnu.no/\">\n"
        "  <doubleattr>17.7862</doubleattr>\n"
        "  <intattr>50</intattr>\n"
        "  <floatattr>3.56</floatattr>\n"
        "  <longattr>346234765345239657</longattr>\n"
        "  <byteattr>74</byteattr>\n"
        "  <rawarrayattr>\n"
        "    <item binaryencoding=\"base64\">cmVh</item>\n"
        "    <item binaryencoding=\"base64\">cmVhZGE=</item>\n"
        "    <item binaryencoding=\"base64\">cmVhZGFibA==</item>\n"
        "  </rawarrayattr>\n"
        "  <rawattr binaryencoding=\"base64\">cmVhZGFibA==</rawattr>\n"
        "  <stringattr>tjohei���</stringattr>\n"
        "  <docfield>\n"
        "    <document documenttype=\"testdoc\" documentid=\"doc:crawler/http://www.ntnu.no/2\">\n"
        "      <stringattr>tjo hei paa du</stringattr>\n"
        "    </document>\n"
        "  </docfield>\n"
        "  <content type=\"contenttype\" encoding=\"encoding\" language=\"language\">humlepungens buffer</content>\n"
        "</document>";
}

void TestXml::testDocumentUpdate()
{
    DocumentTypeRepo repo(readDocumenttypesConfig(TEST_PATH("data/defaultdoctypes.cfg")));
    DocumentUpdate::UP up1(createTestDocumentUpdate(repo));

    std::string expected =
        "<document type=\"testdoc\" id=\"doc:crawler/http://www.ntnu.no/\">\n"
        "  <alter field=\"intattr\">\n"
        "    <assign>7</assign>\n"
        "  </alter>\n"
        "  <alter field=\"stringattr\">\n"
        "    <assign>New value</assign>\n"
        "  </alter>\n"
        "  <alter field=\"arrayattr\">\n"
        "    <add weight=\"1\">123</add>\n"
        "    <add weight=\"1\">456</add>\n"
        "  </alter>\n"
        "  <alter field=\"arrayattr\">\n"
        "    <remove>123</remove>\n"
        "    <remove>456</remove>\n"
        "    <remove>789</remove>\n"
        "  </alter>\n"
        "</document>";
    CPPUNIT_ASSERT_EQUAL(expected, up1->toXml("  "));
}

} // document
