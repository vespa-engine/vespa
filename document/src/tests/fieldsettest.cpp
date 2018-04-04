// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocman.h>
#include <vespa/document/fieldset/fieldsetrepo.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vdstestlib/cppunit/macros.h>

#include <vespa/document/datatype/annotationreferencedatatype.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <algorithm>

using vespalib::nbostream;

using namespace document::config_builder;

namespace document {

struct FieldSetTest : public CppUnit::TestFixture {
    void testParsing();
    void testContains();
    void testCopyDocumentFields();
    void testDocumentSubsetCopy();
    void testStripFields();
    void testSerialize();

    CPPUNIT_TEST_SUITE(FieldSetTest);
    CPPUNIT_TEST(testParsing);
    CPPUNIT_TEST(testSerialize);
    CPPUNIT_TEST(testContains);
    CPPUNIT_TEST(testCopyDocumentFields);
    CPPUNIT_TEST(testDocumentSubsetCopy);
    CPPUNIT_TEST(testStripFields);
    CPPUNIT_TEST_SUITE_END();

    std::string stringifyFields(const Document& doc) const;
    std::string doCopyFields(const Document& src,
                             const DocumentTypeRepo& docRepo,
                             const std::string& fieldSetStr,
                             Document* dest = 0) const;
    std::string doCopyDocument(const Document& src,
                               const DocumentTypeRepo& docRepo,
                               const std::string& fieldSetStr);
    std::string doStripFields(const Document& doc,
                              const DocumentTypeRepo& docRepo,
                              const std::string& fieldSetStr);
    Document::UP createTestDocument(const TestDocMan& testDocMan) const;
};

CPPUNIT_TEST_SUITE_REGISTRATION(FieldSetTest);

void FieldSetTest::testParsing()
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& docRepo = testDocMan.getTypeRepo();

    FieldSetRepo repo;

    dynamic_cast<AllFields&>(*repo.parse(docRepo, "[all]"));
    dynamic_cast<NoFields&>(*repo.parse(docRepo, "[none]"));
    dynamic_cast<DocIdOnly&>(*repo.parse(docRepo, "[id]"));
    dynamic_cast<HeaderFields&>(*repo.parse(docRepo, "[header]"));
    dynamic_cast<BodyFields&>(*repo.parse(docRepo, "[body]"));

    FieldSet::UP set = repo.parse(docRepo, "testdoctype1:headerval,content");
    FieldCollection& coll = dynamic_cast<FieldCollection&>(*set);

    std::ostringstream ost;
    for (Field::Set::const_iterator iter = coll.getFields().begin();
         iter != coll.getFields().end();
         ++iter) {
        ost << (*iter)->getName() << " ";
    }

    CPPUNIT_ASSERT_EQUAL(std::string("content headerval "), ost.str());
}

namespace {

bool checkContains(FieldSetRepo& r, const DocumentTypeRepo& repo,
                   const std::string& str1, const std::string str2) {
    FieldSet::UP set1 = r.parse(repo, str1);
    FieldSet::UP set2 = r.parse(repo, str2);

    return set1->contains(*set2);
}

bool checkError(FieldSetRepo& r, const DocumentTypeRepo& repo,
                const std::string& str) {
    try {
        r.parse(repo, str);
        return false;
    } catch (...) {
        return true;
    }
}

}

void FieldSetTest::testContains()
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& repo = testDocMan.getTypeRepo();
    const DocumentType& type = *repo.getDocumentType("testdoctype1");

    const Field& headerField = type.getField("headerval");
    const Field& bodyField = type.getField("content");

    NoFields none;
    AllFields all;
    DocIdOnly id;
    HeaderFields h;
    BodyFields b;

    CPPUNIT_ASSERT_EQUAL(false, headerField.contains(
                                 type.getField("headerlongval")));
    CPPUNIT_ASSERT_EQUAL(true, headerField.contains(headerField));
    CPPUNIT_ASSERT_EQUAL(true, headerField.contains(id));
    CPPUNIT_ASSERT_EQUAL(false, headerField.contains(all));
    CPPUNIT_ASSERT_EQUAL(true, headerField.contains(none));
    CPPUNIT_ASSERT_EQUAL(false, none.contains(headerField));
    CPPUNIT_ASSERT_EQUAL(true, all.contains(headerField));
    CPPUNIT_ASSERT_EQUAL(true, all.contains(none));
    CPPUNIT_ASSERT_EQUAL(false, none.contains(all));
    CPPUNIT_ASSERT_EQUAL(true, all.contains(id));
    CPPUNIT_ASSERT_EQUAL(false, none.contains(id));
    CPPUNIT_ASSERT_EQUAL(true, id.contains(none));

    CPPUNIT_ASSERT_EQUAL(true, h.contains(headerField));
    CPPUNIT_ASSERT_EQUAL(false, h.contains(bodyField));

    CPPUNIT_ASSERT_EQUAL(false, b.contains(headerField));
    CPPUNIT_ASSERT_EQUAL(true, b.contains(bodyField));

    FieldSetRepo r;
    CPPUNIT_ASSERT_EQUAL(true, checkContains(r, repo, "[body]",
                                             "testdoctype1:content"));
    CPPUNIT_ASSERT_EQUAL(false, checkContains(r, repo, "[header]",
                                              "testdoctype1:content"));
    CPPUNIT_ASSERT_EQUAL(true, checkContains(r, repo,
                                             "testdoctype1:content,headerval",
                                             "testdoctype1:content"));
    CPPUNIT_ASSERT_EQUAL(false, checkContains(r, repo,
                                              "testdoctype1:content",
                                              "testdoctype1:content,headerval"));
    CPPUNIT_ASSERT_EQUAL(true, checkContains(r, repo,
                                             "testdoctype1:headerval,content",
                                             "testdoctype1:content,headerval"));

    CPPUNIT_ASSERT(checkError(r, repo, "nodoctype"));
    CPPUNIT_ASSERT(checkError(r, repo, "unknowndoctype:foo"));
    CPPUNIT_ASSERT(checkError(r, repo, "testdoctype1:unknownfield"));
    CPPUNIT_ASSERT(checkError(r, repo, "[badid]"));
}

std::string
FieldSetTest::stringifyFields(const Document& doc) const
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
                           Document* dest) const
{
    Document destDoc(src.getType(), DocumentId("doc:test:fieldsdest"));
    if (!dest) {
        dest = &destDoc;
    }
    FieldSetRepo repo;
    FieldSet::UP fset = repo.parse(docRepo, fieldSetStr);
    FieldSet::copyFields(*dest, src, *fset);
    return stringifyFields(*dest);
}

std::string
FieldSetTest::doStripFields(const Document& doc,
                            const DocumentTypeRepo& docRepo,
                            const std::string& fieldSetStr)
{
    Document::UP copy(doc.clone());
    FieldSetRepo repo;
    FieldSet::UP fset = repo.parse(docRepo, fieldSetStr);
    FieldSet::stripFields(*copy, *fset);
    return stringifyFields(*copy);
}

Document::UP
FieldSetTest::createTestDocument(const TestDocMan& testDocMan) const
{
    Document::UP doc(testDocMan.createDocument("megafoo megabar",
                                               "doc:test:fieldssrc",
                                               "testdoctype1"));
    doc->setValue(doc->getField("headerval"), IntFieldValue(5678));
    doc->setValue(doc->getField("hstringval"),
                  StringFieldValue("hello fantastic world"));
    return doc;
}

void
FieldSetTest::testCopyDocumentFields()
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& repo = testDocMan.getTypeRepo();
    Document::UP src(createTestDocument(testDocMan));

    CPPUNIT_ASSERT_EQUAL(std::string("content: megafoo megabar\n"),
                         doCopyFields(*src, repo, "[body]"));
    CPPUNIT_ASSERT_EQUAL(std::string(""),
                         doCopyFields(*src, repo, "[none]"));
    CPPUNIT_ASSERT_EQUAL(std::string("headerval: 5678\n"
                                     "hstringval: hello fantastic world\n"),
                         doCopyFields(*src, repo, "[header]"));
    CPPUNIT_ASSERT_EQUAL(std::string("content: megafoo megabar\n"
                                     "headerval: 5678\n"
                                     "hstringval: hello fantastic world\n"),
                         doCopyFields(*src, repo, "[all]"));
    CPPUNIT_ASSERT_EQUAL(std::string("content: megafoo megabar\n"
                                     "hstringval: hello fantastic world\n"),
                         doCopyFields(*src, repo, "testdoctype1:hstringval,content"));
    // Test that we overwrite already set fields in destination document
    {
        Document dest(src->getType(), DocumentId("doc:foo:bar"));
        dest.setValue(dest.getField("content"), StringFieldValue("overwriteme"));
        CPPUNIT_ASSERT_EQUAL(std::string("content: megafoo megabar\n"),
                         doCopyFields(*src, repo, "[body]", &dest));
    }
}

std::string
FieldSetTest::doCopyDocument(const Document& src,
                             const DocumentTypeRepo& docRepo,
                             const std::string& fieldSetStr)
{
    FieldSetRepo repo;
    FieldSet::UP fset = repo.parse(docRepo, fieldSetStr);
    Document::UP doc(FieldSet::createDocumentSubsetCopy(src, *fset));
    return stringifyFields(*doc);
}


void
FieldSetTest::testDocumentSubsetCopy()
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& repo = testDocMan.getTypeRepo();
    Document::UP src(createTestDocument(testDocMan));

    {
        Document::UP doc(FieldSet::createDocumentSubsetCopy(*src, AllFields()));
        // Test that document id and type are copied correctly.
        CPPUNIT_ASSERT(doc.get());
        CPPUNIT_ASSERT_EQUAL(src->getId(), doc->getId());
        CPPUNIT_ASSERT_EQUAL(src->getType(), doc->getType());
        CPPUNIT_ASSERT_EQUAL(doCopyFields(*src, repo, "[all]"),
                             stringifyFields(*doc));
    }

    const char* fieldSets[] = {
        "[all]",
        "[none]",
        "[header]",
        "[body]",
        "testdoctype1:hstringval,content"
    };
    for (size_t i = 0; i < sizeof(fieldSets) / sizeof(fieldSets[0]); ++i) {
        CPPUNIT_ASSERT_EQUAL(doCopyFields(*src, repo, fieldSets[i]),
                             doCopyDocument(*src, repo, fieldSets[i]));
    }
}

void
FieldSetTest::testSerialize()
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& docRepo = testDocMan.getTypeRepo();

    const char* fieldSets[] = {
        "[all]",
        "[none]",
        "[header]",
        "[docid]",
        "[body]",
        "testdoctype1:content",
        "testdoctype1:content,hstringval"
    };

    FieldSetRepo repo;
    for (size_t i = 0; i < sizeof(fieldSets) / sizeof(fieldSets[0]); ++i) {
        FieldSet::UP fs = repo.parse(docRepo, fieldSets[i]);
        CPPUNIT_ASSERT_EQUAL(vespalib::string(fieldSets[i]), repo.serialize(*fs));
    }
}

void
FieldSetTest::testStripFields()
{
    TestDocMan testDocMan;
    const DocumentTypeRepo& repo = testDocMan.getTypeRepo();
    Document::UP src(createTestDocument(testDocMan));

    CPPUNIT_ASSERT_EQUAL(std::string("content: megafoo megabar\n"),
                         doStripFields(*src, repo, "[body]"));
    CPPUNIT_ASSERT_EQUAL(std::string(""),
                         doStripFields(*src, repo, "[none]"));
    CPPUNIT_ASSERT_EQUAL(std::string(""),
                         doStripFields(*src, repo, "[id]"));
    CPPUNIT_ASSERT_EQUAL(std::string("headerval: 5678\n"
                                     "hstringval: hello fantastic world\n"),
                         doStripFields(*src, repo, "[header]"));
    CPPUNIT_ASSERT_EQUAL(std::string("content: megafoo megabar\n"
                                     "headerval: 5678\n"
                                     "hstringval: hello fantastic world\n"),
                         doStripFields(*src, repo, "[all]"));
    CPPUNIT_ASSERT_EQUAL(std::string("content: megafoo megabar\n"
                                     "hstringval: hello fantastic world\n"),
                         doStripFields(*src, repo, "testdoctype1:hstringval,content"));
}

} // document
