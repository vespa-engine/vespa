// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storage/distributor/blockingoperationstarter.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <tests/distributor/maintenancemocks.h>

namespace storage {

namespace distributor {

using document::BucketId;

class BlockingOperationStarterTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(BlockingOperationStarterTest);
    CPPUNIT_TEST(testOperationNotBlockedWhenNoMessagesPending);
    CPPUNIT_TEST(testOperationBlockedWhenMessagesPending);
    CPPUNIT_TEST_SUITE_END();

    std::shared_ptr<Operation> createMockOperation() {
        return std::shared_ptr<Operation>(new MockOperation(BucketId(16, 1)));
    }
    std::shared_ptr<Operation> createBlockingMockOperation() {
        std::shared_ptr<MockOperation> op(new MockOperation(BucketId(16, 1)));
        op->setShouldBlock(true);
        return op;
    }

    framework::defaultimplementation::FakeClock _clock;
    std::unique_ptr<MockOperationStarter> _starterImpl;
    std::unique_ptr<StorageComponentRegisterImpl> _compReg;
    std::unique_ptr<PendingMessageTracker> _messageTracker;
    std::unique_ptr<BlockingOperationStarter> _operationStarter;

public:
    void testOperationNotBlockedWhenNoMessagesPending();
    void testOperationBlockedWhenMessagesPending();

    void setUp() override;
};

CPPUNIT_TEST_SUITE_REGISTRATION(BlockingOperationStarterTest);

void
BlockingOperationStarterTest::setUp()
{
    _starterImpl.reset(new MockOperationStarter());
    _compReg.reset(new StorageComponentRegisterImpl());
    _compReg->setClock(_clock);
    _clock.setAbsoluteTimeInSeconds(1);
    _messageTracker.reset(new PendingMessageTracker(*_compReg));
    _operationStarter.reset(new BlockingOperationStarter(*_messageTracker, *_starterImpl));
}

void
BlockingOperationStarterTest::testOperationNotBlockedWhenNoMessagesPending()
{
    CPPUNIT_ASSERT(_operationStarter->start(createMockOperation(),
                                            OperationStarter::Priority(0)));
    CPPUNIT_ASSERT_EQUAL(std::string("BucketId(0x4000000000000001), pri 0\n"),
                         _starterImpl->toString());
}

void
BlockingOperationStarterTest::testOperationBlockedWhenMessagesPending()
{
    // start should return true but not forward message to underlying starter.
    CPPUNIT_ASSERT(_operationStarter->start(createBlockingMockOperation(),
                                            OperationStarter::Priority(0)));
    CPPUNIT_ASSERT_EQUAL(std::string(""), _starterImpl->toString());
}

}
}
