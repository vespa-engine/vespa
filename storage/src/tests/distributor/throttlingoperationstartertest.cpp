// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/distributor/throttlingoperationstarter.h>
#include <tests/distributor/maintenancemocks.h>

namespace storage {

namespace distributor {

using document::BucketId;

class ThrottlingOperationStarterTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(ThrottlingOperationStarterTest);
    CPPUNIT_TEST(testOperationNotThrottledWhenSlotAvailable);
    CPPUNIT_TEST(testOperationStartingIsForwardedToImplementation);
    CPPUNIT_TEST(testOperationThrottledWhenNoAvailableSlots);
    CPPUNIT_TEST(testThrottlingWithMaxPendingRange);
    CPPUNIT_TEST(testStartingOperationsFillsUpPendingWindow);
    CPPUNIT_TEST(testFinishingOperationsAllowsMoreToStart);
    CPPUNIT_TEST_SUITE_END();

    std::shared_ptr<Operation> createMockOperation() {
        return std::shared_ptr<Operation>(new MockOperation(BucketId(16, 1)));
    }

    std::unique_ptr<MockOperationStarter> _starterImpl;
    std::unique_ptr<ThrottlingOperationStarter> _operationStarter;

public:
    void testOperationNotThrottledWhenSlotAvailable();
    void testOperationStartingIsForwardedToImplementation();
    void testOperationThrottledWhenNoAvailableSlots();
    void testThrottlingWithMaxPendingRange();
    void testStartingOperationsFillsUpPendingWindow();
    void testFinishingOperationsAllowsMoreToStart();

    void setUp() override;
    void tearDown() override;
};

CPPUNIT_TEST_SUITE_REGISTRATION(ThrottlingOperationStarterTest);

void
ThrottlingOperationStarterTest::setUp()
{
    _starterImpl.reset(new MockOperationStarter());
    _operationStarter.reset(new ThrottlingOperationStarter(*_starterImpl));
}

void
ThrottlingOperationStarterTest::tearDown()
{
    // Must clear before _operationStarter goes out of scope, or operation
    // destructors will try to call method on destroyed object.
    _starterImpl->getOperations().clear();
}

void
ThrottlingOperationStarterTest::testOperationNotThrottledWhenSlotAvailable()
{
    CPPUNIT_ASSERT(_operationStarter->start(createMockOperation(),
                                            OperationStarter::Priority(0)));
}

void
ThrottlingOperationStarterTest::testOperationStartingIsForwardedToImplementation()
{
    CPPUNIT_ASSERT(_operationStarter->start(createMockOperation(),
                                            OperationStarter::Priority(0)));
    CPPUNIT_ASSERT_EQUAL(std::string("BucketId(0x4000000000000001), pri 0\n"),
                         _starterImpl->toString());
}

void
ThrottlingOperationStarterTest::testOperationThrottledWhenNoAvailableSlots()
{
    _operationStarter->setMaxPendingRange(0, 0);
    CPPUNIT_ASSERT(!_operationStarter->start(createMockOperation(),
                                             OperationStarter::Priority(0)));
}

void
ThrottlingOperationStarterTest::testThrottlingWithMaxPendingRange()
{
    _operationStarter->setMaxPendingRange(0, 1);
    CPPUNIT_ASSERT(!_operationStarter->canStart(0, OperationStarter::Priority(255)));
    CPPUNIT_ASSERT(_operationStarter->canStart(0, OperationStarter::Priority(0)));

    _operationStarter->setMaxPendingRange(1, 1);
    CPPUNIT_ASSERT(_operationStarter->canStart(0, OperationStarter::Priority(255)));
    CPPUNIT_ASSERT(_operationStarter->canStart(0, OperationStarter::Priority(0)));

    _operationStarter->setMaxPendingRange(1, 3);
    CPPUNIT_ASSERT(!_operationStarter->canStart(1, OperationStarter::Priority(255)));
    CPPUNIT_ASSERT(_operationStarter->canStart(1, OperationStarter::Priority(100)));
    CPPUNIT_ASSERT(_operationStarter->canStart(1, OperationStarter::Priority(0)));
    CPPUNIT_ASSERT(_operationStarter->canStart(2, OperationStarter::Priority(0)));
    CPPUNIT_ASSERT(!_operationStarter->canStart(3, OperationStarter::Priority(0)));
    CPPUNIT_ASSERT(!_operationStarter->canStart(4, OperationStarter::Priority(0)));
}

void
ThrottlingOperationStarterTest::testStartingOperationsFillsUpPendingWindow()
{
    _operationStarter->setMaxPendingRange(1, 3);
    CPPUNIT_ASSERT(_operationStarter->start(createMockOperation(),
                                            OperationStarter::Priority(255)));
    CPPUNIT_ASSERT(!_operationStarter->start(createMockOperation(),
                                             OperationStarter::Priority(255)));
    CPPUNIT_ASSERT(_operationStarter->start(createMockOperation(),
                                            OperationStarter::Priority(100)));
    CPPUNIT_ASSERT(!_operationStarter->start(createMockOperation(),
                                             OperationStarter::Priority(100)));
    CPPUNIT_ASSERT(_operationStarter->start(createMockOperation(),
                                            OperationStarter::Priority(0)));
    CPPUNIT_ASSERT(!_operationStarter->start(createMockOperation(),
                                             OperationStarter::Priority(0)));
}

void
ThrottlingOperationStarterTest::testFinishingOperationsAllowsMoreToStart()
{
    _operationStarter->setMaxPendingRange(1, 1);
    CPPUNIT_ASSERT(_operationStarter->start(createMockOperation(),
                                            OperationStarter::Priority(255)));
    CPPUNIT_ASSERT(!_operationStarter->start(createMockOperation(),
                                             OperationStarter::Priority(255)));
    CPPUNIT_ASSERT(!_starterImpl->getOperations().empty());

    _starterImpl->getOperations().pop_back();

    CPPUNIT_ASSERT(_operationStarter->start(createMockOperation(),
                                            OperationStarter::Priority(255)));
    CPPUNIT_ASSERT(!_starterImpl->getOperations().empty());
}

}
}
