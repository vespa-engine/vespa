// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/vespalib/stllike/asciistream.h>
#include <gtest/gtest.h>

namespace document {

class GlobalIdTest : public ::testing::Test {

protected:
    void verifyGlobalIdRange(const std::vector<DocumentId>& ids,
                             uint32_t countBits);
};

TEST_F(GlobalIdTest, testNormalUsage)
{
    const char* emptystring = "\0\0\0\0\0\0\0\0\0\0\0\0";
    const char* teststring = "1234567890ABCDEF";
    EXPECT_TRUE(strlen(teststring) > GlobalId::LENGTH);

    { // Test empty global id
        GlobalId id;
        for (uint32_t i=0; i<GlobalId::LENGTH; ++i) {
            EXPECT_EQ((unsigned char) '\0', id.get()[i]);
        }
        GlobalId id2(emptystring);
        EXPECT_EQ(id, id2);
        EXPECT_TRUE(!(id != id2));
        EXPECT_TRUE(!(id < id2) && !(id2 < id));
        EXPECT_EQ(std::string(emptystring, GlobalId::LENGTH),
                  std::string((const char*) id.get(), GlobalId::LENGTH));
    }
    { // Test non-empty global id
        GlobalId empty;
        GlobalId initialempty;
        initialempty.set(teststring);
        GlobalId id(teststring);
        EXPECT_EQ(id, initialempty);
        EXPECT_TRUE(!(id != initialempty));
        EXPECT_TRUE(!(id < initialempty) && !(initialempty < id));

        EXPECT_TRUE(id != empty);
        EXPECT_TRUE(!(id == empty));
        EXPECT_TRUE(!(id < empty) && (empty < id));

        EXPECT_EQ(
                std::string(teststring, GlobalId::LENGTH),
                std::string((const char*) id.get(), GlobalId::LENGTH));
        EXPECT_EQ(std::string(teststring, GlobalId::LENGTH),
                             std::string((const char*) initialempty.get(),
                                         GlobalId::LENGTH));
    }
    { // Test printing and parsing
        GlobalId id1("LIN!#LNKASD#!MYL#&NK");
        EXPECT_EQ(vespalib::string("gid(0x4c494e21234c4e4b41534423)"),
                             id1.toString());
        GlobalId id2 = GlobalId::parse(id1.toString());
        EXPECT_EQ(id1, id2);
            // Verify string representation too, to verify that operator== works
        EXPECT_EQ(vespalib::string("gid(0x4c494e21234c4e4b41534423)"),
                             id2.toString());
    }
}

namespace {
    void verifyDocumentId(const std::string& s) {
        document::DocumentId did(s);
        BucketIdFactory factory;
        BucketId bid = factory.getBucketId(did);
        GlobalId gid = did.getGlobalId();
        BucketId generated = gid.convertToBucketId();
        //std::cerr << bid << ", " << generated << "\n";
        if (bid != generated) {
            FAIL() << "Document id " << s << " with gid " << gid
                   << " belongs to bucket " << bid
                   << ", but globalid convert function generated bucketid "
                   << generated;
        }
    }
}

TEST_F(GlobalIdTest, testBucketIdConversion)
{
    verifyDocumentId("id:ns:test:n=1:abc");
    verifyDocumentId("id:ns:test:n=1000:abc");
    verifyDocumentId("id:hsgf:test:n=18446744073700000000:dfdfsdfg");
    verifyDocumentId("id:ns:mytype:g=somegroup:hmm");
    verifyDocumentId("id:ns:test::");
    verifyDocumentId("id:myns:test::http://foo.bar");
    verifyDocumentId("id:ns:test::jsrthsdf:a234aleingzldkifvasdfgadf");
}

void
GlobalIdTest::verifyGlobalIdRange(const std::vector<DocumentId>& ids, uint32_t countBits)
{
    BucketIdFactory factory;
    for (uint32_t i=0, n=ids.size(); i<n; ++i) {
            // Create the bucket this document would be in with given
            // countbits
        BucketId bucket(factory.getBucketId(ids[i]));
        bucket.setUsedBits(countBits);
        bucket = bucket.stripUnused();
            // Get the min and max GIDs for this bucket
        GlobalId first = GlobalId::calculateFirstInBucket(bucket);
        GlobalId last = GlobalId::calculateLastInBucket(bucket);
            // For each document in set, verify that they are within
            // limits if they are contained in bucket.
        for (uint32_t j=0; j<n; ++j) {
            BucketId bid(factory.getBucketId(ids[j]));
            GlobalId gid(ids[j].getGlobalId());
            uint64_t gidKey = gid.convertToBucketId().toKey();
            if (bucket.contains(bid)) {
                if ((gidKey < last.convertToBucketId().toKey()) || (gidKey > last.convertToBucketId().toKey())) {
                    std::ostringstream msg;
                    msg << gid << " should be in the range " << first
                        << " - " << last;
                    msg << ", as bucket " << gid.convertToBucketId()
                        << " should be in the range "
                        << first.convertToBucketId() << " - "
                        << last.convertToBucketId() << "\n";
                    msg << ", reverted " << std::hex
                        << gid.convertToBucketId().toKey()
                        << " should be in the range "
                        << first.convertToBucketId().toKey() << " - "
                        << last.convertToBucketId().toKey() << "\n";
                    EXPECT_TRUE(gid.convertToBucketId().toKey() >= first.convertToBucketId().toKey()) << msg.str();
                    EXPECT_TRUE(gid.convertToBucketId().toKey() <= last.convertToBucketId().toKey()) << msg.str();
                }
            } else {
                if ((gidKey >= first.convertToBucketId().toKey()) && (gidKey <= last.convertToBucketId().toKey())) {
                    std::ostringstream msg;
                    msg << gid << " should not be in the range " << first
                        << " - " << last;
                    EXPECT_TRUE((gid.convertToBucketId().toKey() < first.convertToBucketId().toKey()) ||
                                (gid.convertToBucketId().toKey() > last.convertToBucketId().toKey())) << msg.str();
                }
            }
        }
    }
}

TEST_F(GlobalIdTest, testGidRangeConversion)
{
        // Generate a lot of random document ids used for test
    std::vector<DocumentId> docIds;
    vespalib::RandomGen randomizer(0xdeadbabe);
    for (uint32_t j=0; j<100; ++j) {
        vespalib::asciistream name_space;
        vespalib::asciistream ost;
        for (uint32_t i=0, n=randomizer.nextUint32(1, 5); i<n; ++i) {
            name_space << (char) ('a' + randomizer.nextUint32(0, 25));
        }
        uint32_t scheme = randomizer.nextUint32(0, 2);
        switch (scheme) {
            case 0: ost << "id:" << name_space.str() << ":mytype::";
                    break;
            case 1: ost << "id:" << name_space.str() << ":mytype:n=";
                    ost << randomizer.nextUint32() << ":";
                    break;
            case 2: ost << "id:" << name_space.str() << ":mytype:g=";
                    for (uint32_t i=0, n=randomizer.nextUint32(1, 10); i<n; ++i) {
                        ost << (char) ('a' + randomizer.nextUint32(0, 25));
                    }
                    ost << ":";
                    break;
            default: EXPECT_TRUE(false);
        }
        ost << "http://";
        for (uint32_t i=0, n=randomizer.nextUint32(1, 20); i<n; ++i) {
            ost << (char) ('a' + randomizer.nextUint32(0, 25));
        }
        docIds.push_back(DocumentId(ost.str()));
    }
    //std::cerr << "\nDoing " << ((58 - 16) * docIds.size() * docIds.size())
    //          << " tests for whether global id calculation is correct.\n";
    for (uint32_t i=1; i<=58; ++i) {
        //std::cerr << "Verifying with " << i << " countbits\n";
        verifyGlobalIdRange(docIds, i);
    }
}

TEST_F(GlobalIdTest, testBucketOrderCmp)
{
    using C = GlobalId::BucketOrderCmp;
    EXPECT_TRUE(C::compareRaw(0, 0) == 0);
    EXPECT_TRUE(C::compareRaw(0, 1) == -1);
    EXPECT_TRUE(C::compareRaw(1, 0) == 1);
    EXPECT_TRUE(C::compareRaw(255, 255) == 0);
    EXPECT_TRUE(C::compareRaw(0, 255) == -255);
    EXPECT_TRUE(C::compareRaw(255, 0) == 255);
    EXPECT_TRUE(C::compareRaw(254, 254) == 0);
    EXPECT_TRUE(C::compareRaw(254, 255) == -1);
    EXPECT_TRUE(C::compareRaw(255, 254) == 1);
    {
        // Test raw comparator object.
        GlobalId foo = GlobalId::parse("gid(0x000001103330333077700000)");
        GlobalId bar = GlobalId::parse("gid(0x000000100030003000700000)");
        GlobalId baz = GlobalId::parse("gid(0x000000103330333000700000)");

        GlobalId::BucketOrderCmp cmp;
        EXPECT_TRUE(!cmp(foo, foo));
        EXPECT_TRUE(!cmp(bar, bar));
        EXPECT_TRUE(!cmp(baz, baz));
        EXPECT_TRUE(!cmp(foo, bar));
        EXPECT_TRUE( cmp(bar, foo));
        EXPECT_TRUE(!cmp(foo, baz));
        EXPECT_TRUE( cmp(baz, foo));
        EXPECT_TRUE(!cmp(baz, bar));
        EXPECT_TRUE( cmp(bar, baz));
    }
    {
        // Test sorting by bucket.
        GlobalId foo = GlobalId::parse("gid(0x000001103330333077700000)");
        GlobalId bar = GlobalId::parse("gid(0x000000100030003000700000)");
        GlobalId baz = GlobalId::parse("gid(0x000000103330333000700000)");

        using GidMap = std::map<GlobalId, uint32_t, GlobalId::BucketOrderCmp>;
        GidMap gidMap;
        gidMap[foo] = 666;
        gidMap[bar] = 777;
        gidMap[baz] = 888;

        GidMap::iterator it = gidMap.begin();
        EXPECT_TRUE(it->first == bar);
        EXPECT_TRUE(it->second == 777);
        ++it;
        EXPECT_TRUE(it->first == baz);
        EXPECT_TRUE(it->second == 888);
        ++it;
        EXPECT_TRUE(it->first == foo);
        EXPECT_TRUE(it->second == 666);
    }
}

}
