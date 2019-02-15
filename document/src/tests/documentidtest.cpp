// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/base/documentid.h>
#include <vespa/vespalib/util/md5.h>
#include <vespa/fastos/file.h>
#include <gtest/gtest.h>

namespace document {

namespace {
    void writeGlobalIdBucketId(std::ostream& out, const std::string& id) {
        BucketIdFactory factory;
        out << id << " - " << DocumentId(id).getGlobalId()
            << " - " << factory.getBucketId(DocumentId(id)).toString()
            << "\n";
    }
}

TEST(DocumentIdTest, generateJavaComplianceFile)
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
        ASSERT_TRUE(file.OpenWriteOnlyTruncate(TEST_PATH("cpp-globalidbucketids.txt").c_str()));
        std::string content(ost.str());
        ASSERT_TRUE(file.CheckedWrite(content.c_str(), content.size()));
        ASSERT_TRUE(file.Close());
    }
}


TEST(DocumentIdTest, testOutput)
{
    DocumentId id(DocIdString("crawler", "http://www.yahoo.com"));

    std::ostringstream ost;
    ost << id;
    std::string expected("doc:crawler:http://www.yahoo.com");
    EXPECT_EQ(expected, ost.str());

    EXPECT_EQ(vespalib::string(expected), id.toString());

    expected = "DocumentId(id = doc:crawler:http://www.yahoo.com, "
                          "gid(0x928baffb39cf32004542fb60))";
    EXPECT_EQ(expected, static_cast<Printable&>(id).toString(true));
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

TEST(DocumentIdTest, testEqualityOperator)
{
    std::string uri(DocIdString("crawler", "http://www.yahoo.com").toString());

    DocumentId id1(uri);
    DocumentId id2(uri);
    DocumentId id3("doc:crawler:http://www.yahoo.no/");

    EXPECT_EQ(id1, id2);
    EXPECT_NE(id1, id3);
}

TEST(DocumentIdTest, testCopying)
{
    std::string uri(DocIdString("crawler", "http://www.yahoo.com/").toString());

    DocumentId id1(uri);
    DocumentId id2(id1);
    DocumentId id3("doc:ns:foo");
    id3 = id2;

    EXPECT_EQ(id1, id2);
    EXPECT_EQ(id1, id3);
}

TEST(DocumentIdTest, checkNtnuGlobalId)
{
    DocumentId id("doc:crawler:http://www.ntnu.no/");
    EXPECT_EQ(vespalib::string("gid(0xb8863740be14221c0ac77896)"), id.getGlobalId().toString());
}

TEST(DocumentIdTest, testDocGlobalId)
{
        // Test that location of doc scheme documents are set correctly, such
        // that the location is the first bytes of the original GID.
    std::string id("doc:crawler:http://www.ntnu.no/");
    DocumentId did(id);

    unsigned char key[16];
    fastc_md5sum(reinterpret_cast<const unsigned char*>(id.c_str()),
                 id.size(), key);

    EXPECT_EQ(GlobalId(key), did.getGlobalId());
}

TEST(DocumentIdTest, freestandingLocationFromGroupNameFuncMatchesIdLocation)
{
    EXPECT_EQ(
            DocumentId("id::foo:g=zoid:bar").getScheme().getLocation(),
            GroupDocIdString::locationFromGroupName("zoid"));
    EXPECT_EQ(
            DocumentId("id::bar:g=doink:baz").getScheme().getLocation(),
            GroupDocIdString::locationFromGroupName("doink"));
}

} // document
