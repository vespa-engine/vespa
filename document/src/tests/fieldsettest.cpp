// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <algorithm>
#include <gtest/gtest.h>

using vespalib::nbostream;

namespace document {

class FieldSetTest : public ::testing::Test {

protected:
    static std::string stringifyFields(const Document& doc);
    static std::string doCopyFields(const Document& src, const DocumentTypeRepo& docRepo,
                                    const std::string& fieldSetStr,Document* dest = nullptr);
    static std::string doCopyDocument(const Document& src, const DocumentTypeRepo& docRepo, const std::string& fieldSetStr);
    static std::string doStripFields(const Document& doc, const DocumentTypeRepo& docRepo, const std::string& fieldSetStr);
    static Document::UP createTestDocument(const TestDocMan& testDocMan);
};

TEST_F(FieldSetTest, testParsing)
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& docRepo = testDocMan.getTypeRepo();

    (void) dynamic_cast<AllFields&>(*FieldSetRepo::parse(docRepo, AllFields::NAME));
    (void) dynamic_cast<DocumentOnly&>(*FieldSetRepo::parse(docRepo, DocumentOnly::NAME));
    (void) dynamic_cast<NoFields&>(*FieldSetRepo::parse(docRepo, NoFields::NAME));
    (void) dynamic_cast<DocIdOnly&>(*FieldSetRepo::parse(docRepo, DocIdOnly::NAME));

    auto set = FieldSetRepo::parse(docRepo, "testdoctype1:headerval,content");
    auto & coll = dynamic_cast<FieldCollection&>(*set);

    std::ostringstream ost;
    for (const Field * field : coll.getFields()) {
        ost << field->getName() << " ";
    }

    EXPECT_EQ(std::string("content headerval "), ost.str());
}

namespace {

bool checkContains(const DocumentTypeRepo& repo,
                   const std::string& str1, const std::string & str2) {
    auto set1 = FieldSetRepo::parse(repo, str1);
    auto set2 = FieldSetRepo::parse(repo, str2);

    return set1->contains(*set2);
}

bool checkError(const DocumentTypeRepo& repo, const std::string& str) {
    try {
        FieldSetRepo::parse(repo, str);
        return false;
    } catch (...) {
        return true;
    }
}

}

TEST_F(FieldSetTest, testContains)
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& repo = testDocMan.getTypeRepo();
    const DocumentType& type = *repo.getDocumentType("testdoctype1");

    const Field& headerField = type.getField("headerval");

    NoFields none;
    AllFields all;
    DocumentOnly doconly;
    DocIdOnly id;

    EXPECT_EQ(false, headerField.contains(type.getField("headerlongval")));
    EXPECT_EQ(true, headerField.contains(headerField));
    EXPECT_EQ(true, headerField.contains(id));
    EXPECT_EQ(false, headerField.contains(all));
    EXPECT_EQ(true, headerField.contains(none));
    EXPECT_EQ(false, none.contains(headerField));
    EXPECT_EQ(true, all.contains(headerField));
    EXPECT_EQ(true, all.contains(none));
    EXPECT_EQ(false, none.contains(all));
    EXPECT_EQ(true, all.contains(id));
    EXPECT_EQ(false, none.contains(id));
    EXPECT_EQ(true, id.contains(none));
    EXPECT_EQ(true, doconly.contains(none));
    EXPECT_EQ(true, doconly.contains(id));

    EXPECT_EQ(true, checkContains(repo,
                                             "testdoctype1:content,headerval",
                                             "testdoctype1:content"));
    EXPECT_EQ(false, checkContains(repo,
                                              "testdoctype1:content",
                                              "testdoctype1:content,headerval"));
    EXPECT_EQ(true, checkContains(repo,
                                             "testdoctype1:headerval,content",
                                             "testdoctype1:content,headerval"));

    EXPECT_TRUE(checkError(repo, "nodoctype"));
    EXPECT_TRUE(checkError(repo, "unknowndoctype:foo"));
    EXPECT_TRUE(checkError(repo, "testdoctype1:unknownfield"));
    EXPECT_TRUE(checkError(repo, "[badid]"));
}

std::string
FieldSetTest::stringifyFields(const Document& doc)
{
    std::vector<std::string> output;
    const StructFieldValue& fields(doc.getFields());
    for (StructFieldValue::const_iterator
             it(fields.begin()), e(fields.end());
         it != e; ++it)
    {
        std::ostringstream ss;
        const Field& f(it.field());
        ss << f.getName() << ": ";
        FieldValue::UP val(fields.getValue(f));
        if (val.get()) {
            ss << val->toString();
        } else {
            ss << "(null)";
        }
        output.push_back(ss.str());
    }
    std::ostringstream ret;
    std::sort(output.begin(), output.end());
    std::copy(output.begin(), output.end(),
              std::ostream_iterator<std::string>(ret, "\n"));
    return ret.str();
}

std::string
FieldSetTest::doCopyFields(const Document& src,
                           const DocumentTypeRepo& docRepo,
                           const std::string& fieldSetStr,
                           Document* dest)
{
    Document destDoc(docRepo, src.getType(), DocumentId("id:ns:" + src.getType().getName() + "::fieldset"));
    if (!dest) {
        dest = &destDoc;
    }
    auto fset = FieldSetRepo::parse(docRepo, fieldSetStr);
    FieldSet::copyFields(*dest, src, *fset);
    return stringifyFields(*dest);
}

std::string
FieldSetTest::doStripFields(const Document& doc,
                            const DocumentTypeRepo& docRepo,
                            const std::string& fieldSetStr)
{
    Document::UP copy(doc.clone());
    auto fset = FieldSetRepo::parse(docRepo, fieldSetStr);
    FieldSet::stripFields(*copy, *fset);
    return stringifyFields(*copy);
}

Document::UP
FieldSetTest::createTestDocument(const TestDocMan& testDocMan)
{
    Document::UP doc(testDocMan.createDocument("megafoo megabar",
                                               "id:ns:testdoctype1::1",
                                               "testdoctype1"));
    doc->setValue(doc->getField("headerval"), IntFieldValue(5678));
    doc->setValue(doc->getField("hstringval"),
                  StringFieldValue("hello fantastic world"));
    return doc;
}

TEST_F(FieldSetTest, testCopyDocumentFields)
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& repo = testDocMan.getTypeRepo();
    Document::UP src(createTestDocument(testDocMan));

    EXPECT_EQ(std::string(""),
              doCopyFields(*src, repo, NoFields::NAME));
    EXPECT_EQ(std::string("content: megafoo megabar\n"
                          "headerval: 5678\n"
                          "hstringval: hello fantastic world\n"),
              doCopyFields(*src, repo, AllFields::NAME));
    EXPECT_EQ(std::string("headerval: 5678\n"
                          "hstringval: hello fantastic world\n"),
              doCopyFields(*src, repo, DocumentOnly::NAME));
    EXPECT_EQ(std::string("content: megafoo megabar\n"
                          "hstringval: hello fantastic world\n"),
              doCopyFields(*src, repo, "testdoctype1:hstringval,content"));
    // Test that we overwrite already set fields in destination document
    {
        Document dest(repo, src->getType(), DocumentId("id:ns:" + src->getType().getName() + "::bar"));
        dest.setValue(dest.getField("content"), StringFieldValue("overwriteme"));
        EXPECT_EQ(std::string("content: megafoo megabar\n"),
                  doCopyFields(*src, repo, src->getType().getName() + ":content", &dest));
    }
}

std::string
FieldSetTest::doCopyDocument(const Document& src,
                             const DocumentTypeRepo& docRepo,
                             const std::string& fieldSetStr)
{
    auto fset = FieldSetRepo::parse(docRepo, fieldSetStr);
    Document::UP doc(FieldSet::createDocumentSubsetCopy(docRepo, src, *fset));
    return stringifyFields(*doc);
}


TEST_F(FieldSetTest, testDocumentSubsetCopy)
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& repo = testDocMan.getTypeRepo();
    Document::UP src(createTestDocument(testDocMan));

    {
        Document::UP doc(FieldSet::createDocumentSubsetCopy(repo, *src, AllFields()));
        // Test that document id and type are copied correctly.
        EXPECT_TRUE(doc.get());
        EXPECT_EQ(src->getId(), doc->getId());
        EXPECT_EQ(src->getType(), doc->getType());
        EXPECT_EQ(doCopyFields(*src, repo, AllFields::NAME),
                  stringifyFields(*doc));
    }
    {
        Document::UP doc(FieldSet::createDocumentSubsetCopy(repo, *src, DocumentOnly()));
        // Test that document id and type are copied correctly.
        EXPECT_TRUE(doc.get());
        EXPECT_EQ(src->getId(), doc->getId());
        EXPECT_EQ(src->getType(), doc->getType());
        EXPECT_EQ(doCopyFields(*src, repo, DocumentOnly::NAME),
                  stringifyFields(*doc));
    }

    const char* fieldSets[] = {
        AllFields::NAME,
        DocumentOnly::NAME,
        NoFields::NAME,
        "testdoctype1:hstringval,content"
    };
    for (const char * fieldSet : fieldSets) {
        EXPECT_EQ(doCopyFields(*src, repo, fieldSet),
                  doCopyDocument(*src, repo, fieldSet));
    }
}

TEST_F(FieldSetTest, testSerialize)
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& docRepo = testDocMan.getTypeRepo();

    const char* fieldSets[] = {
        AllFields::NAME,
        NoFields::NAME,
        DocumentOnly::NAME,
        DocIdOnly::NAME,
        "testdoctype1:content",
        "testdoctype1:content,hstringval"
    };

    for (const char * fieldSet : fieldSets) {
        auto fs = FieldSetRepo::parse(docRepo, fieldSet);
        EXPECT_EQ(vespalib::string(fieldSet), FieldSetRepo::serialize(*fs));
    }
}

TEST_F(FieldSetTest, testStripFields)
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& repo = testDocMan.getTypeRepo();
    Document::UP src(createTestDocument(testDocMan));

    EXPECT_EQ(std::string(""),
              doStripFields(*src, repo, NoFields::NAME));
    EXPECT_EQ(std::string(""),
              doStripFields(*src, repo, DocIdOnly::NAME));
    EXPECT_EQ(std::string("content: megafoo megabar\n"
                          "headerval: 5678\n"
                          "hstringval: hello fantastic world\n"),
              doStripFields(*src, repo, AllFields::NAME));
    EXPECT_EQ(std::string("headerval: 5678\n"
                          "hstringval: hello fantastic world\n"),
              doStripFields(*src, repo, DocumentOnly::NAME));
    EXPECT_EQ(std::string("content: megafoo megabar\n"
                          "hstringval: hello fantastic world\n"),
              doStripFields(*src, repo, "testdoctype1:hstringval,content"));
}

TEST(FieldCollectionTest, testHash ) {
    TestDocMan testDocMan;
    const DocumentTypeRepo& repo = testDocMan.getTypeRepo();
    const DocumentType & type = *repo.getDocumentType("testdoctype1");
    EXPECT_EQ(0ul, FieldCollection(type, Field::Set::Builder().build()).hash());
    EXPECT_EQ(0x548599858c77ef83ul, FieldCollection(type, Field::Set::Builder().add(&type.getField("headerval")).build()).hash());
    EXPECT_EQ(0x4a7ff2406d36a9b0ul, FieldCollection(type, Field::Set::Builder().add(&type.getField("headerval")).add(
            &type.getField("hstringval")).build()).hash());
    EXPECT_EQ(0x1e0918531b19734ul, FieldCollection(type, Field::Set::Builder().add(&type.getField("hstringval")).build()).hash());
}

TEST(FieldTest, testSizeOf) {
    EXPECT_EQ(sizeof(Field), 88);
}

} // document
