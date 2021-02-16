// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>
#include <vespa/storage/distributor/maintenance/maintenancescheduler.h>
#include <tests/distributor/maintenancemocks.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <memory>
#include <string>
#include <sstream>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

using document::BucketId;
using Priority = MaintenancePriority;
using WaitTimeMs = MaintenanceScheduler::WaitTimeMs;

struct MaintenanceSchedulerTest : Test {
    std::unique_ptr<SimpleBucketPriorityDatabase> _priorityDb;
    std::unique_ptr<MockMaintenanceOperationGenerator> _operationGenerator;
    std::unique_ptr<MockOperationStarter> _operationStarter;
    std::unique_ptr<MaintenanceScheduler> _scheduler;

    void SetUp() override;
};

void
MaintenanceSchedulerTest::SetUp()
{
    _priorityDb = std::make_unique<SimpleBucketPriorityDatabase>();
    _operationGenerator = std::make_unique<MockMaintenanceOperationGenerator>();
    _operationStarter = std::make_unique<MockOperationStarter>();
    _scheduler = std::make_unique<MaintenanceScheduler>(*_operationGenerator, *_priorityDb, *_operationStarter);
}

TEST_F(MaintenanceSchedulerTest, priority_cleared_after_scheduled) {
    _priorityDb->setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::HIGHEST));
    _scheduler->tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE);
    EXPECT_EQ("", _priorityDb->toString());
}

TEST_F(MaintenanceSchedulerTest, operation_is_scheduled) {
    _priorityDb->setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::MEDIUM));
    _scheduler->tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE);
    EXPECT_EQ("Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri 100\n",
              _operationStarter->toString());
}

TEST_F(MaintenanceSchedulerTest, no_operations_to_schedule) {
    WaitTimeMs waitMs(_scheduler->tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE));
    EXPECT_EQ(WaitTimeMs(1), waitMs);
    EXPECT_EQ("", _operationStarter->toString());
}

TEST_F(MaintenanceSchedulerTest, suppress_low_priorities_in_emergency_mode) {
    _priorityDb->setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::VERY_HIGH));
    _priorityDb->setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 2)), Priority::HIGHEST));
    EXPECT_EQ(WaitTimeMs(0), _scheduler->tick(MaintenanceScheduler::RECOVERY_SCHEDULING_MODE));
    EXPECT_EQ(WaitTimeMs(1), _scheduler->tick(MaintenanceScheduler::RECOVERY_SCHEDULING_MODE));
    EXPECT_EQ("Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000002)), pri 0\n",
              _operationStarter->toString());
    EXPECT_EQ("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri VERY_HIGH)\n",
              _priorityDb->toString());
}

TEST_F(MaintenanceSchedulerTest, priority_not_cleared_if_operation_not_started) {
    _priorityDb->setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::HIGH));
    _operationStarter->setShouldStartOperations(false);
    WaitTimeMs waitMs(_scheduler->tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE));
    EXPECT_EQ(WaitTimeMs(1), waitMs);
    EXPECT_EQ("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri HIGH)\n",
              _priorityDb->toString());
}

}
