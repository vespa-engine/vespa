// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/storage/frameworkimpl/component/storagecomponentregisterimpl.h>
#include <vespa/storage/distributor/blockingoperationstarter.h>
#include <vespa/storage/distributor/pendingmessagetracker.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <tests/distributor/maintenancemocks.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using document::BucketId;
using namespace ::testing;

namespace storage::distributor {

struct BlockingOperationStarterTest : Test {
    std::shared_ptr<Operation> createMockOperation() {
        return std::make_shared<MockOperation>(makeDocumentBucket(BucketId(16, 1)));
    }
    std::shared_ptr<Operation> createBlockingMockOperation() {
        auto op = std::make_shared<MockOperation>(makeDocumentBucket(BucketId(16, 1)));
        op->setShouldBlock(true);
        return op;
    }

    framework::defaultimplementation::FakeClock _clock;
    std::unique_ptr<MockOperationStarter> _starterImpl;
    std::unique_ptr<StorageComponentRegisterImpl> _compReg;
    std::unique_ptr<PendingMessageTracker> _messageTracker;
    std::unique_ptr<OperationSequencer> _operation_sequencer;
    std::unique_ptr<BlockingOperationStarter> _operationStarter;

    void SetUp() override;
};

void
BlockingOperationStarterTest::SetUp()
{
    _starterImpl = std::make_unique<MockOperationStarter>();
    _compReg = std::make_unique<StorageComponentRegisterImpl>();
    _compReg->setClock(_clock);
    _clock.setAbsoluteTimeInSeconds(1);
    _messageTracker = std::make_unique<PendingMessageTracker>(*_compReg);
    _operation_sequencer = std::make_unique<OperationSequencer>();
    _operationStarter = std::make_unique<BlockingOperationStarter>(*_messageTracker, *_operation_sequencer, *_starterImpl);
}

TEST_F(BlockingOperationStarterTest, operation_not_blocked_when_no_messages_pending) {
    ASSERT_TRUE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(0)));
    EXPECT_EQ("Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri 0\n",
              _starterImpl->toString());
}

TEST_F(BlockingOperationStarterTest, operation_blocked_when_messages_pending) {
    // start should return true but not forward message to underlying starter.
    ASSERT_TRUE(_operationStarter->start(createBlockingMockOperation(), OperationStarter::Priority(0)));
    EXPECT_EQ("", _starterImpl->toString());
}

}
