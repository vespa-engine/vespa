// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/distributor/throttlingoperationstarter.h>
#include <tests/distributor/maintenancemocks.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::distributor {

using document::BucketId;
using document::test::makeDocumentBucket;
using namespace ::testing;

namespace {

const MockOperation& as_mock_operation(const Operation& operation) {
    return dynamic_cast<const MockOperation&>(operation);
}

}

struct ThrottlingOperationStarterTest : Test {
    std::shared_ptr<Operation> createMockOperation() {
        return std::make_shared<MockOperation>(makeDocumentBucket(BucketId(16, 1)));
    }

    std::unique_ptr<MockOperationStarter> _starterImpl;
    std::unique_ptr<ThrottlingOperationStarter> _operationStarter;

    void SetUp() override;
    void TearDown() override;
};

void
ThrottlingOperationStarterTest::SetUp()
{
    _starterImpl = std::make_unique<MockOperationStarter>();
    _operationStarter = std::make_unique<ThrottlingOperationStarter>(*_starterImpl);
}

void
ThrottlingOperationStarterTest::TearDown()
{
    // Must clear before _operationStarter goes out of scope, or operation
    // destructors will try to call method on destroyed object.
    _starterImpl->getOperations().clear();
}

TEST_F(ThrottlingOperationStarterTest, operation_not_throttled_when_slot_available) {
    auto operation = createMockOperation();
    EXPECT_TRUE(_operationStarter->start(operation, OperationStarter::Priority(0)));
    EXPECT_FALSE(as_mock_operation(*operation).get_was_throttled());
}

TEST_F(ThrottlingOperationStarterTest, operation_starting_is_forwarded_to_implementation) {
    ASSERT_TRUE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(0)));
    EXPECT_EQ("Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri 0\n",
              _starterImpl->toString());
}

TEST_F(ThrottlingOperationStarterTest, operation_throttled_when_no_available_slots) {
    _operationStarter->setMaxPendingRange(0, 0);
    auto operation = createMockOperation();
    EXPECT_FALSE(_operationStarter->may_allow_operation_with_priority(OperationStarter::Priority(0)));
    EXPECT_FALSE(_operationStarter->start(operation, OperationStarter::Priority(0)));
    EXPECT_TRUE(as_mock_operation(*operation).get_was_throttled());
}

TEST_F(ThrottlingOperationStarterTest, throttling_with_max_pending_range) {
    _operationStarter->setMaxPendingRange(0, 1);
    EXPECT_FALSE(_operationStarter->canStart(0, OperationStarter::Priority(255)));
    EXPECT_TRUE(_operationStarter->canStart(0, OperationStarter::Priority(0)));

    _operationStarter->setMaxPendingRange(1, 1);
    EXPECT_TRUE(_operationStarter->canStart(0, OperationStarter::Priority(255)));
    EXPECT_TRUE(_operationStarter->canStart(0, OperationStarter::Priority(0)));

    _operationStarter->setMaxPendingRange(1, 3);
    EXPECT_FALSE(_operationStarter->canStart(1, OperationStarter::Priority(255)));
    EXPECT_TRUE(_operationStarter->canStart(1, OperationStarter::Priority(100)));
    EXPECT_TRUE(_operationStarter->canStart(1, OperationStarter::Priority(0)));
    EXPECT_TRUE(_operationStarter->canStart(2, OperationStarter::Priority(0)));
    EXPECT_FALSE(_operationStarter->canStart(3, OperationStarter::Priority(0)));
    EXPECT_FALSE(_operationStarter->canStart(4, OperationStarter::Priority(0)));
}

TEST_F(ThrottlingOperationStarterTest, starting_operations_fills_up_pending_window) {
    _operationStarter->setMaxPendingRange(1, 3);
    EXPECT_TRUE(_operationStarter->may_allow_operation_with_priority(OperationStarter::Priority(255)));
    EXPECT_TRUE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(255)));

    EXPECT_FALSE(_operationStarter->may_allow_operation_with_priority(OperationStarter::Priority(255)));
    EXPECT_FALSE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(255)));

    EXPECT_TRUE(_operationStarter->may_allow_operation_with_priority(OperationStarter::Priority(100)));
    EXPECT_TRUE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(100)));

    EXPECT_FALSE(_operationStarter->may_allow_operation_with_priority(OperationStarter::Priority(255)));
    EXPECT_FALSE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(100)));

    EXPECT_TRUE(_operationStarter->may_allow_operation_with_priority(OperationStarter::Priority(0)));
    EXPECT_TRUE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(0)));

    EXPECT_FALSE(_operationStarter->may_allow_operation_with_priority(OperationStarter::Priority(0)));
    EXPECT_FALSE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(0)));
}

TEST_F(ThrottlingOperationStarterTest, finishing_operations_allows_more_to_start) {
    _operationStarter->setMaxPendingRange(1, 1);
    EXPECT_TRUE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(255)));
    EXPECT_FALSE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(255)));
    EXPECT_FALSE(_starterImpl->getOperations().empty());

    _starterImpl->getOperations().pop_back();

    EXPECT_TRUE(_operationStarter->may_allow_operation_with_priority(OperationStarter::Priority(255)));
    EXPECT_TRUE(_operationStarter->start(createMockOperation(), OperationStarter::Priority(255)));
    EXPECT_FALSE(_starterImpl->getOperations().empty());
}

}
