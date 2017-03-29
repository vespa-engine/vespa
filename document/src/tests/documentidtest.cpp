// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <vespa/document/serialization/vespadocumentdeserializer.h>
#include <vespa/document/serialization/vespadocumentserializer.h>
#include <vespa/vespalib/objects/nbostream.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/vespalib/util/md5.h>
#include <vespa/fastos/file.h>

using document::VespaDocumentDeserializer;
using document::VespaDocumentSerializer;
using vespalib::nbostream;

namespace document {

struct DocumentIdTest : public CppUnit::TestFixture {
    void generateJavaComplianceFile();
    void testOutput();
    void testEqualityOperator();
    void testCopying();
    void testParseId();
    void checkNtnuGlobalId();
    void testDocGlobalId();
    void freestandingLocationFromGroupNameFuncMatchesIdLocation();

    CPPUNIT_TEST_SUITE(DocumentIdTest);
    CPPUNIT_TEST(testEqualityOperator);
    CPPUNIT_TEST(testOutput);
    CPPUNIT_TEST(testCopying);
    CPPUNIT_TEST(generateJavaComplianceFile);
    CPPUNIT_TEST(testParseId);
    CPPUNIT_TEST(checkNtnuGlobalId);
    CPPUNIT_TEST(testDocGlobalId);
    CPPUNIT_TEST(freestandingLocationFromGroupNameFuncMatchesIdLocation);
    CPPUNIT_TEST_SUITE_END();

};

CPPUNIT_TEST_SUITE_REGISTRATION(DocumentIdTest);

namespace {
    void writeGlobalIdBucketId(std::ostream& out, const std::string& id) {
        BucketIdFactory factory;
        out << id << " - " << document::DocumentId(id).getGlobalId()
            << " - " << factory.getBucketId(document::DocumentId(id)).toString()
            << "\n";
    }
}

void DocumentIdTest::generateJavaComplianceFile()
{
    {  // Generate file with globalids and bucket ID of various document ids,
       // which java will use to ensure equal implementations.
        std::ostringstream ost;
        writeGlobalIdBucketId(ost, "doc:ns:specific");
        writeGlobalIdBucketId(ost, "doc:another:specific");
        writeGlobalIdBucketId(ost, "doc:ns:another");
        writeGlobalIdBucketId(ost, "userdoc:ns:100:specific");
        writeGlobalIdBucketId(ost, "userdoc:np:100:another");
        writeGlobalIdBucketId(ost, "userdoc:ns:101:specific");
        writeGlobalIdBucketId(ost, "groupdoc:ns:agroup:specific");
        writeGlobalIdBucketId(ost, "groupdoc:np:agroup:another");
        writeGlobalIdBucketId(ost, "groupdoc:ns:another:specific");
        for (uint32_t i=0; i<20; ++i) {
            std::ostringstream ost2;
            ost2 << i;
            writeGlobalIdBucketId(ost, "doc:ns:"+ost2.str());
        }
        writeGlobalIdBucketId(ost, "id:ns:type::specific");
        writeGlobalIdBucketId(ost, "id:another:type::specific");
        writeGlobalIdBucketId(ost, "id:ns:type::another");
        writeGlobalIdBucketId(ost, "id:ns:type:n=100:specific");
        writeGlobalIdBucketId(ost, "id:np:type:n=100:another");
        writeGlobalIdBucketId(ost, "id:ns:type:n=101:specific");
        writeGlobalIdBucketId(ost, "id:ns:type:g=agroup:specific");
        writeGlobalIdBucketId(ost, "id:np:type:g=agroup:another");
        writeGlobalIdBucketId(ost, "id:ns:type:g=another:specific");
        FastOS_File file;
        CPPUNIT_ASSERT(file.OpenWriteOnlyTruncate(TEST_PATH("cpp-globalidbucketids.txt").c_str()));
        std::string content(ost.str());
        CPPUNIT_ASSERT(file.CheckedWrite(content.c_str(), content.size()));
        CPPUNIT_ASSERT(file.Close());
    }
}


void DocumentIdTest::testOutput()
{
    DocumentId id(DocIdString("crawler", "http://www.yahoo.com"));

    std::ostringstream ost;
    ost << id;
    std::string expected("doc:crawler:http://www.yahoo.com");
    CPPUNIT_ASSERT_EQUAL(expected, ost.str());

    CPPUNIT_ASSERT_EQUAL(vespalib::string(expected), id.toString());

    expected = "DocumentId(id = doc:crawler:http://www.yahoo.com, "
                          "gid(0x928baffb39cf32004542fb60))";
    CPPUNIT_ASSERT_EQUAL(expected, static_cast<Printable&>(id).toString(true));
}

namespace {
    template<class T>
    std::string getNotEqualMessage(const T& t1, const T& t2) {
        std::ostringstream ost;
        ost << "Expected instances to be different. This was not the case:\n"
            << t1 << "\n" << t2 << "\n";
        return ost.str();
    }
}

void DocumentIdTest::testEqualityOperator()
{
    std::string uri(DocIdString("crawler", "http://www.yahoo.com").toString());

    DocumentId id1(uri);
    DocumentId id2(uri);
    DocumentId id3("doc:crawler:http://www.yahoo.no/");

    CPPUNIT_ASSERT_EQUAL(id1, id2);
    CPPUNIT_ASSERT_MESSAGE(getNotEqualMessage(id1, id3), !(id1 == id3));
}

void DocumentIdTest::testCopying()
{
    std::string uri(DocIdString("crawler", "http://www.yahoo.com/").toString());

    DocumentId id1(uri);
    DocumentId id2(id1);
    DocumentId id3("doc:ns:foo");
    id3 = id2;

    CPPUNIT_ASSERT_EQUAL(id1, id2);
    CPPUNIT_ASSERT_EQUAL(id1, id3);
}

void
DocumentIdTest::testParseId()
{
    // Moved to base/documentid_test.cpp
}

void
DocumentIdTest::checkNtnuGlobalId()
{
    DocumentId id("doc:crawler:http://www.ntnu.no/");
    CPPUNIT_ASSERT_EQUAL(vespalib::string("gid(0xb8863740be14221c0ac77896)"),
                         id.getGlobalId().toString());
}

void
DocumentIdTest::testDocGlobalId()
{
        // Test that location of doc scheme documents are set correctly, such
        // that the location is the first bytes of the original GID.
    std::string id("doc:crawler:http://www.ntnu.no/");
    DocumentId did(id);

    unsigned char key[16];
    fastc_md5sum(reinterpret_cast<const unsigned char*>(id.c_str()),
                 id.size(), key);

    CPPUNIT_ASSERT_EQUAL(GlobalId(key), did.getGlobalId());
}

void
DocumentIdTest::freestandingLocationFromGroupNameFuncMatchesIdLocation()
{
    CPPUNIT_ASSERT_EQUAL(
            DocumentId("id::foo:g=zoid:bar").getScheme().getLocation(),
            GroupDocIdString::locationFromGroupName("zoid"));
    CPPUNIT_ASSERT_EQUAL(
            DocumentId("id::bar:g=doink:baz").getScheme().getLocation(),
            GroupDocIdString::locationFromGroupName("doink"));
}

} // document
