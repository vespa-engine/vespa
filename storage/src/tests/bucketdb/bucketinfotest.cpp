// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/fastos/fastos.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <boost/assign.hpp>
#include <boost/random.hpp>
#include <cppunit/extensions/HelperMacros.h>
#include <map>
#include <vector>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/storage/bucketdb/bucketinfo.h>

namespace storage {

namespace distributor {

struct BucketInfoTest : public CppUnit::TestFixture {
    void testBucketInfoEntriesWithNewestTimestampsAreKept();
    void testOrder();
    void testHasInvalidCopy();
    void testAddNodeSetsTrustedWhenConsistent();
    void testTrustedResetWhenCopiesBecomeInconsistent();
    void testTrustedResetWhenTrustedCopiesGoOutOfSync();
    void testTrustedNotResetWhenNonTrustedCopiesStillOutOfSync();
    void add_nodes_can_immediately_update_trusted_flag();
    void add_nodes_can_defer_update_of_trusted_flag();
    void remove_node_can_immediately_update_trusted_flag();
    void remove_node_can_defer_update_of_trusted_flag();

    CPPUNIT_TEST_SUITE(BucketInfoTest);
    CPPUNIT_TEST(testBucketInfoEntriesWithNewestTimestampsAreKept);
    CPPUNIT_TEST(testOrder);
    CPPUNIT_TEST(testHasInvalidCopy);
    CPPUNIT_TEST(testAddNodeSetsTrustedWhenConsistent);
    CPPUNIT_TEST_IGNORED(testTrustedResetWhenCopiesBecomeInconsistent);
    CPPUNIT_TEST(testTrustedResetWhenTrustedCopiesGoOutOfSync);
    CPPUNIT_TEST(testTrustedNotResetWhenNonTrustedCopiesStillOutOfSync);
    CPPUNIT_TEST(add_nodes_can_immediately_update_trusted_flag);
    CPPUNIT_TEST(add_nodes_can_defer_update_of_trusted_flag);
    CPPUNIT_TEST(remove_node_can_immediately_update_trusted_flag);
    CPPUNIT_TEST(remove_node_can_defer_update_of_trusted_flag);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(BucketInfoTest);

BucketInfo
getBucketInfo(std::string nodeList, std::string order) {
    BucketInfo info;

    std::vector<uint16_t> ordering;
    {
        vespalib::StringTokenizer tokenizer(order, ",");
        for (uint32_t i = 0; i < tokenizer.size(); i++) {
            ordering.push_back(atoi(tokenizer[i].c_str()));
        }
    }

    vespalib::StringTokenizer tokenizer(nodeList, ",");
    for (uint32_t i = 0; i < tokenizer.size(); i++) {
        info.addNode(BucketCopy(0,
                                atoi(tokenizer[i].c_str()),
                                api::BucketInfo(1,1,1)),
                     ordering);
    }

    return info;
}

std::string
nodeList(const BucketInfo& info) {
    std::ostringstream ost;
    for (uint32_t i = 0; i < info.getNodeCount(); i++) {
        if (i != 0) {
            ost << ",";
        }
        ost << (int)info.getNodeRef(i).getNode();
    }
    return ost.str();
}

// Since we keep bucket info in memory for a period of time before applying
// to bucket db, we maintain timestamps to prevent external load happening
// in the meantime from having their updates lost when we perform a batch
// insert. This also applies for when we postpone db updates in persistence
// message tracker until we've received a reply from all copies.
void
BucketInfoTest::testBucketInfoEntriesWithNewestTimestampsAreKept()
{
    BucketInfo bi;
    std::vector<uint16_t> idealState;
    idealState.push_back(0);

    bi.addNode(BucketCopy(5, 0, api::BucketInfo(1,1,1)), idealState);
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(1,1,1),
                         bi.getNode(0)->getBucketInfo());

    bi.addNode(BucketCopy(5, 0, api::BucketInfo(2,2,2)), idealState);
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(1,1,1),
                         bi.getNode(0)->getBucketInfo());

    bi.addNode(BucketCopy(4, 0, api::BucketInfo(3,3,3)), idealState);
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(1,1,1),
                         bi.getNode(0)->getBucketInfo());

    bi.addNode(BucketCopy(7, 0, api::BucketInfo(4,4,4)), idealState);
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(4,4,4),
                         bi.getNode(0)->getBucketInfo());

    bi.addNode(BucketCopy(2, 1, api::BucketInfo(4,4,4)), idealState);
    CPPUNIT_ASSERT_EQUAL(api::BucketInfo(4,4,4),
                         bi.getNode(1)->getBucketInfo());
}

void
BucketInfoTest::testOrder() {

    CPPUNIT_ASSERT_EQUAL(std::string("2,0,1"), nodeList(getBucketInfo("0,1,2", "2,0,1")));
    CPPUNIT_ASSERT_EQUAL(std::string("2,0,1"), nodeList(getBucketInfo("1,0,2", "2,0,1")));
    CPPUNIT_ASSERT_EQUAL(std::string("1,0,2"), nodeList(getBucketInfo("1,2,0", "1")));
    CPPUNIT_ASSERT_EQUAL(std::string("2,1,0,3,4"), nodeList(getBucketInfo("0,1,2,3,4", "2,1")));
}

void
BucketInfoTest::testHasInvalidCopy()
{
    std::vector<uint16_t> order;

    BucketInfo info;
    info.addNode(BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)), order);
    info.addNode(BucketCopy(0, 1, api::BucketInfo(10, 100, 1000)), order);
    CPPUNIT_ASSERT(!info.hasInvalidCopy());

    info.addNode(BucketCopy(0, 2, api::BucketInfo()), order);
    CPPUNIT_ASSERT(info.hasInvalidCopy());

}

void
BucketInfoTest::testAddNodeSetsTrustedWhenConsistent()
{
    std::vector<uint16_t> order;

    {
        BucketInfo info;
        info.addNode(BucketCopy(0, 0, api::BucketInfo(0x1, 2, 144)).setTrusted(), order);
        info.addNode(BucketCopy(0, 1, api::BucketInfo(0x1, 2, 144)), order);
        CPPUNIT_ASSERT(info.getNode(1)->trusted());
    }

    {
        BucketInfo info;
        info.addNode(BucketCopy(0, 0, api::BucketInfo(0x1, 1, 2)).setTrusted(), order);
        info.addNode(BucketCopy(0, 1, api::BucketInfo(0x2, 2, 3)), order);
        info.addNode(BucketCopy(0, 2, api::BucketInfo(0x3, 3, 4)), order);

        BucketCopy copy(1, 1, api::BucketInfo(0x1, 1, 2));
        info.updateNode(copy);
        CPPUNIT_ASSERT(info.getNode(1)->trusted());
        CPPUNIT_ASSERT(!info.getNode(2)->trusted());
    }
}

void
BucketInfoTest::testTrustedResetWhenCopiesBecomeInconsistent()
{
    CPPUNIT_FAIL("TODO: test this!");
}

void
BucketInfoTest::testTrustedResetWhenTrustedCopiesGoOutOfSync()
{
    std::vector<uint16_t> order;

    BucketInfo info;
    info.addNode(BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)).setTrusted(), order);
    info.addNode(BucketCopy(0, 1, api::BucketInfo(10, 100, 1000)), order);
    CPPUNIT_ASSERT(info.getNode(0)->trusted());
    CPPUNIT_ASSERT(info.getNode(1)->trusted());
    
    info.updateNode(BucketCopy(0, 1, api::BucketInfo(20, 200, 2000)).setTrusted());
    CPPUNIT_ASSERT(!info.getNode(0)->trusted());
    CPPUNIT_ASSERT(!info.getNode(1)->trusted());
}

void
BucketInfoTest::testTrustedNotResetWhenNonTrustedCopiesStillOutOfSync()
{
    std::vector<uint16_t> order;

    BucketInfo info;
    info.addNode(BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)).setTrusted(), order);
    info.addNode(BucketCopy(0, 1, api::BucketInfo(20, 200, 2000)), order);
    info.addNode(BucketCopy(0, 2, api::BucketInfo(30, 300, 3000)), order);
    CPPUNIT_ASSERT(info.getNode(0)->trusted());
    CPPUNIT_ASSERT(!info.getNode(1)->trusted());
    CPPUNIT_ASSERT(!info.getNode(2)->trusted());
    
    info.updateNode(BucketCopy(0, 1, api::BucketInfo(21, 201, 2001)));

    CPPUNIT_ASSERT(info.getNode(0)->trusted());
    CPPUNIT_ASSERT(!info.getNode(1)->trusted());
    CPPUNIT_ASSERT(!info.getNode(2)->trusted());
}

void BucketInfoTest::add_nodes_can_immediately_update_trusted_flag() {
    BucketInfo info;
    std::vector<uint16_t> order;
    info.addNodes({BucketCopy(0, 0, api::BucketInfo(10, 100, 1000))}, order, TrustedUpdate::UPDATE);
    // Only one replica, so implicitly trusted iff trusted flag update is invoked.
    CPPUNIT_ASSERT(info.getNode(0)->trusted());
}

void BucketInfoTest::add_nodes_can_defer_update_of_trusted_flag() {
    BucketInfo info;
    std::vector<uint16_t> order;
    info.addNodes({BucketCopy(0, 0, api::BucketInfo(10, 100, 1000))}, order, TrustedUpdate::DEFER);
    CPPUNIT_ASSERT(!info.getNode(0)->trusted());
}

void BucketInfoTest::remove_node_can_immediately_update_trusted_flag() {
    BucketInfo info;
    std::vector<uint16_t> order;
    info.addNodes({BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)),
                   BucketCopy(0, 1, api::BucketInfo(20, 200, 2000))},
                  order, TrustedUpdate::UPDATE);
    CPPUNIT_ASSERT(!info.getNode(0)->trusted());
    info.removeNode(1, TrustedUpdate::UPDATE);
    // Only one replica remaining after remove, so implicitly trusted iff trusted flag update is invoked.
    CPPUNIT_ASSERT(info.getNode(0)->trusted());
}

void BucketInfoTest::remove_node_can_defer_update_of_trusted_flag() {
    BucketInfo info;
    std::vector<uint16_t> order;
    info.addNodes({BucketCopy(0, 0, api::BucketInfo(10, 100, 1000)),
                   BucketCopy(0, 1, api::BucketInfo(20, 200, 2000))},
                  order, TrustedUpdate::UPDATE);
    info.removeNode(1, TrustedUpdate::DEFER);
    CPPUNIT_ASSERT(!info.getNode(0)->trusted());
}

}

} // storage

