// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <iomanip>
#include <iostream>
#include <memory>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageframework/defaultimplementation/clock/fakeclock.h>
#include <vespa/storage/distributor/bucketdbupdater.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storage/distributor/pendingclusterstate.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/storage/distributor/nodeinfo.h>

#include <iostream>
#include <fstream>
#include <string>

namespace storage {
namespace distributor {

class NodeInfoTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(NodeInfoTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST_SUITE_END();
public:
    void testSimple();
};

CPPUNIT_TEST_SUITE_REGISTRATION(NodeInfoTest);

void
NodeInfoTest::testSimple()
{
    framework::defaultimplementation::FakeClock clock;
    NodeInfo info(clock);

    CPPUNIT_ASSERT_EQUAL(0, (int)info.getPendingCount(3));
    CPPUNIT_ASSERT_EQUAL(0, (int)info.getPendingCount(9));

    info.incPending(3);
    info.incPending(3);
    info.incPending(3);
    info.incPending(3);
    info.decPending(3);
    info.decPending(4);
    info.incPending(7);
    info.incPending(4);
    info.decPending(3);

    CPPUNIT_ASSERT_EQUAL(2, (int)info.getPendingCount(3));
    CPPUNIT_ASSERT_EQUAL(1, (int)info.getPendingCount(4));
    CPPUNIT_ASSERT_EQUAL(1, (int)info.getPendingCount(7));
    CPPUNIT_ASSERT_EQUAL(0, (int)info.getPendingCount(5));

    info.setBusy(5);
    clock.addSecondsToTime(10);
    info.setBusy(1);
    clock.addSecondsToTime(20);
    info.setBusy(42);

    CPPUNIT_ASSERT_EQUAL(true, info.isBusy(5));
    CPPUNIT_ASSERT_EQUAL(true, info.isBusy(1));
    CPPUNIT_ASSERT_EQUAL(true, info.isBusy(42));
    CPPUNIT_ASSERT_EQUAL(false, info.isBusy(7));

    clock.addSecondsToTime(42);

    CPPUNIT_ASSERT_EQUAL(false, info.isBusy(5));
    CPPUNIT_ASSERT_EQUAL(false, info.isBusy(1));
    CPPUNIT_ASSERT_EQUAL(true, info.isBusy(42));
    CPPUNIT_ASSERT_EQUAL(false, info.isBusy(7));

}

}

}
