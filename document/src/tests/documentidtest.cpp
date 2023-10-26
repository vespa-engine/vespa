// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
    DocumentId id("id:ns:news::crawler:http://www.yahoo.com");
    vespalib::string expected("id:ns:news::crawler:http://www.yahoo.com");
    EXPECT_EQ(expected, id.toString());
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
    std::string uri("id:ns:news::crawler:http://www.yahoo.com");

    DocumentId id1(uri);
    DocumentId id2(uri);
    DocumentId id3("id:ns:news::crawler:http://www.yahoo.no/");

    EXPECT_EQ(id1, id2);
    EXPECT_NE(id1, id3);
}

TEST(DocumentIdTest, testCopying)
{
    std::string uri("id:crawler:news::http://www.yahoo.com");

    DocumentId id1(uri);
    DocumentId id2(id1);
    DocumentId id3("id:ns:foo::");
    id3 = id2;

    EXPECT_EQ(id1, id2);
    EXPECT_EQ(id1, id3);
}

TEST(DocumentIdTest, checkNtnuGlobalId)
{
    DocumentId id("id:ns:news::crawler:http://www.ntnu.no/");
    EXPECT_EQ(vespalib::string("gid(0x1e9d7fc69ac6c1da44dd87e0)"), id.getGlobalId().toString());
}

TEST(DocumentIdTest, freestandingLocationFromGroupNameFuncMatchesIdLocation)
{
    EXPECT_EQ(
            DocumentId("id::foo:g=zoid:bar").getScheme().getLocation(),
            IdString::makeLocation("zoid"));
    EXPECT_EQ(
            DocumentId("id::bar:g=doink:baz").getScheme().getLocation(),
            IdString::makeLocation("doink"));
}

} // document
