// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/base/documentid.h>
#include <vespa/document/base/globalid.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/vespalib/stllike/asciistream.h>

namespace document {

struct GlobalIdTest : public CppUnit::TestFixture {
    void testNormalUsage();
    void testBucketIdConversion();
    void testGidRangeConversion();
    void testBucketOrderCmp();

    void verifyGlobalIdRange(const std::vector<DocumentId>& ids,
                             uint32_t countBits);

    CPPUNIT_TEST_SUITE(GlobalIdTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST(testBucketIdConversion);
    CPPUNIT_TEST(testGidRangeConversion);
    CPPUNIT_TEST(testBucketOrderCmp);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(GlobalIdTest);

void
GlobalIdTest::testNormalUsage()
{
    const char* emptystring = "\0\0\0\0\0\0\0\0\0\0\0\0";
    const char* teststring = "1234567890ABCDEF";
    CPPUNIT_ASSERT(strlen(teststring) > GlobalId::LENGTH);

    { // Test empty global id
        GlobalId id;
        for (uint32_t i=0; i<GlobalId::LENGTH; ++i) {
            CPPUNIT_ASSERT_EQUAL((unsigned char) '\0', id.get()[i]);
        }
        GlobalId id2(emptystring);
        CPPUNIT_ASSERT_EQUAL(id, id2);
        CPPUNIT_ASSERT(!(id != id2));
        CPPUNIT_ASSERT(!(id < id2) && !(id2 < id));
        CPPUNIT_ASSERT_EQUAL(std::string(emptystring, GlobalId::LENGTH),
                             std::string((const char*) id.get(),
                                         GlobalId::LENGTH));
    }
    { // Test non-empty global id
        GlobalId empty;
        GlobalId initialempty;
        initialempty.set(teststring);
        GlobalId id(teststring);
        CPPUNIT_ASSERT_EQUAL(id, initialempty);
        CPPUNIT_ASSERT(!(id != initialempty));
        CPPUNIT_ASSERT(!(id < initialempty) && !(initialempty < id));

        CPPUNIT_ASSERT(id != empty);
        CPPUNIT_ASSERT(!(id == empty));
        CPPUNIT_ASSERT(!(id < empty) && (empty < id));

        CPPUNIT_ASSERT_EQUAL(
                std::string(teststring, GlobalId::LENGTH),
                std::string((const char*) id.get(), GlobalId::LENGTH));
        CPPUNIT_ASSERT_EQUAL(std::string(teststring, GlobalId::LENGTH),
                             std::string((const char*) initialempty.get(),
                                         GlobalId::LENGTH));
    }
    { // Test printing and parsing
        GlobalId id1("LIN!#LNKASD#!MYL#&NK");
        CPPUNIT_ASSERT_EQUAL(vespalib::string("gid(0x4c494e21234c4e4b41534423)"),
                             id1.toString());
        GlobalId id2 = GlobalId::parse(id1.toString());
        CPPUNIT_ASSERT_EQUAL(id1, id2);
            // Verify string representation too, to verify that operator== works
        CPPUNIT_ASSERT_EQUAL(vespalib::string("gid(0x4c494e21234c4e4b41534423)"),
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
            std::ostringstream ost;
            ost << "Document id " << s << " with gid " << gid
                << " belongs to bucket " << bid
                << ", but globalid convert function generated bucketid "
                << generated;
            CPPUNIT_FAIL(ost.str());
        }
    }
}

void
GlobalIdTest::testBucketIdConversion()
{
    verifyDocumentId("userdoc:ns:1:abc");
    verifyDocumentId("userdoc:ns:1000:abc");
    verifyDocumentId("userdoc:hsgf:18446744073700000000:dfdfsdfg");
    verifyDocumentId("groupdoc:ns:somegroup:hmm");
    verifyDocumentId("doc::test");
    verifyDocumentId("doc:myns:http://foo.bar");
    verifyDocumentId("doc:jsrthsdf:a234aleingzldkifvasdfgadf");
}

void
GlobalIdTest::verifyGlobalIdRange(const std::vector<DocumentId>& ids,
                                  uint32_t countBits)
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
                    CPPUNIT_ASSERT_MESSAGE(msg.str(),
                            gid.convertToBucketId().toKey() >=
                                   first.convertToBucketId().toKey());
                    CPPUNIT_ASSERT_MESSAGE(msg.str(),
                            gid.convertToBucketId().toKey() <=
                                   last.convertToBucketId().toKey());
                }
            } else {
                if ((gidKey >= first.convertToBucketId().toKey()) && (gidKey <= last.convertToBucketId().toKey())) {
                    std::ostringstream msg;
                    msg << gid << " should not be in the range " << first
                        << " - " << last;
                    CPPUNIT_ASSERT_MESSAGE(msg.str(),
                            (gid.convertToBucketId().toKey() <
                                first.convertToBucketId().toKey())
                            ||
                            (gid.convertToBucketId().toKey() >
                                last.convertToBucketId().toKey()));
                }
            }
        }
    }
}

void
GlobalIdTest::testGidRangeConversion()
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
            case 0: ost << "doc:" << name_space.str() << ":";
                    break;
            case 1: ost << "userdoc:" << name_space.str() << ":";
                    ost << randomizer.nextUint32() << ":";
                    break;
            case 2: ost << "groupdoc:" << name_space.str() << ":";
                    for (uint32_t i=0, n=randomizer.nextUint32(1, 10); i<n; ++i)
                    {
                        ost << (char) ('a' + randomizer.nextUint32(0, 25));
                    }
                    ost << ":";
                    break;
            default: CPPUNIT_ASSERT(false);
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

void
GlobalIdTest::testBucketOrderCmp()
{
    typedef GlobalId::BucketOrderCmp C;
    CPPUNIT_ASSERT(C::compareRaw(0, 0) == 0);
    CPPUNIT_ASSERT(C::compareRaw(0, 1) == -1);
    CPPUNIT_ASSERT(C::compareRaw(1, 0) == 1);
    CPPUNIT_ASSERT(C::compareRaw(255, 255) == 0);
    CPPUNIT_ASSERT(C::compareRaw(0, 255) == -255);
    CPPUNIT_ASSERT(C::compareRaw(255, 0) == 255);
    CPPUNIT_ASSERT(C::compareRaw(254, 254) == 0);
    CPPUNIT_ASSERT(C::compareRaw(254, 255) == -1);
    CPPUNIT_ASSERT(C::compareRaw(255, 254) == 1);
    {
        // Test raw comparator object.
        GlobalId foo = GlobalId::parse("gid(0x000001103330333077700000)");
        GlobalId bar = GlobalId::parse("gid(0x000000100030003000700000)");
        GlobalId baz = GlobalId::parse("gid(0x000000103330333000700000)");

        GlobalId::BucketOrderCmp cmp;
        CPPUNIT_ASSERT(!cmp(foo, foo));
        CPPUNIT_ASSERT(!cmp(bar, bar));
        CPPUNIT_ASSERT(!cmp(baz, baz));
        CPPUNIT_ASSERT(!cmp(foo, bar));
        CPPUNIT_ASSERT( cmp(bar, foo));
        CPPUNIT_ASSERT(!cmp(foo, baz));
        CPPUNIT_ASSERT( cmp(baz, foo));
        CPPUNIT_ASSERT(!cmp(baz, bar));
        CPPUNIT_ASSERT( cmp(bar, baz));
    }
    {
        // Test sorting by bucket.
        GlobalId foo = GlobalId::parse("gid(0x000001103330333077700000)");
        GlobalId bar = GlobalId::parse("gid(0x000000100030003000700000)");
        GlobalId baz = GlobalId::parse("gid(0x000000103330333000700000)");

        typedef std::map<GlobalId, uint32_t, GlobalId::BucketOrderCmp> GidMap;
        GidMap gidMap;
        gidMap[foo] = 666;
        gidMap[bar] = 777;
        gidMap[baz] = 888;

        GidMap::iterator it = gidMap.begin();
        CPPUNIT_ASSERT(it->first == bar);
        CPPUNIT_ASSERT(it->second == 777);
        ++it;
        CPPUNIT_ASSERT(it->first == baz);
        CPPUNIT_ASSERT(it->second == 888);
        ++it;
        CPPUNIT_ASSERT(it->first == foo);
        CPPUNIT_ASSERT(it->second == 666);
    }
}

} // document
