// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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

struct MaintenanceSchedulerTest : TestWithParam<bool> {
    SimpleBucketPriorityDatabase      _priority_db;
    MockMaintenanceOperationGenerator _operation_generator;
    MockOperationStarter              _operation_starter;
    MockPendingWindowChecker          _pending_window_checker;
    MaintenanceScheduler              _scheduler;

    MaintenanceSchedulerTest()
        : _priority_db(),
          _operation_generator(),
          _operation_starter(),
          _pending_window_checker(),
          _scheduler(_operation_generator, _priority_db, _pending_window_checker, _operation_starter)
    {}

    void SetUp() override {
        _scheduler.set_implicitly_clear_priority_on_schedule(GetParam());
    }
};

TEST_P(MaintenanceSchedulerTest, priority_cleared_after_scheduled) {
    _priority_db.setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::HIGHEST));
    _scheduler.tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE);
    EXPECT_EQ("", _priority_db.toString());
}

TEST_P(MaintenanceSchedulerTest, operation_is_scheduled) {
    _priority_db.setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::MEDIUM));
    _scheduler.tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE);
    EXPECT_EQ("Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri 100\n",
              _operation_starter.toString());
}

TEST_P(MaintenanceSchedulerTest, operation_is_not_scheduled_if_pending_ops_not_accepted) {
    if (!GetParam()) {
        return; // Only works when implicit clearing is enabled
    }
    _priority_db.setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::MEDIUM));
    _pending_window_checker.allow_operations(false);
    _scheduler.tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE);
    EXPECT_EQ("", _operation_starter.toString());
    // Priority DB entry is not cleared
    EXPECT_EQ("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri MEDIUM)\n",
              _priority_db.toString());
}

TEST_P(MaintenanceSchedulerTest, no_operations_to_schedule) {
    WaitTimeMs waitMs(_scheduler.tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE));
    EXPECT_EQ(WaitTimeMs(1), waitMs);
    EXPECT_EQ("", _operation_starter.toString());
}

TEST_P(MaintenanceSchedulerTest, suppress_low_priorities_in_emergency_mode) {
    _priority_db.setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::VERY_HIGH));
    _priority_db.setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 2)), Priority::HIGHEST));
    EXPECT_EQ(WaitTimeMs(0), _scheduler.tick(MaintenanceScheduler::RECOVERY_SCHEDULING_MODE));
    EXPECT_EQ(WaitTimeMs(1), _scheduler.tick(MaintenanceScheduler::RECOVERY_SCHEDULING_MODE));
    EXPECT_EQ("Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000002)), pri 0\n",
              _operation_starter.toString());
    EXPECT_EQ("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri VERY_HIGH)\n",
              _priority_db.toString());
}

TEST_P(MaintenanceSchedulerTest, priority_not_cleared_if_operation_not_started) {
    if (GetParam()) {
        return; // Only works when implicit clearing is NOT enabled
    }
    _priority_db.setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::HIGH));
    _operation_starter.setShouldStartOperations(false);
    WaitTimeMs waitMs(_scheduler.tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE));
    EXPECT_EQ(WaitTimeMs(1), waitMs);
    EXPECT_EQ("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri HIGH)\n",
              _priority_db.toString());
}

TEST_P(MaintenanceSchedulerTest, priority_cleared_if_operation_not_started_inside_pending_window) {
    if (!GetParam()) {
        return; // Only works when implicit clearing is enabled
    }
    _priority_db.setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::HIGH));
    _operation_starter.setShouldStartOperations(false);
    WaitTimeMs waitMs(_scheduler.tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE));
    EXPECT_EQ(WaitTimeMs(1), waitMs);
    EXPECT_EQ("", _priority_db.toString());
}

TEST_P(MaintenanceSchedulerTest, priority_not_cleared_if_operation_not_started_inside_pending_window_for_highest_pri) {
    _priority_db.setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::HIGHEST));
    _operation_starter.setShouldStartOperations(false);
    WaitTimeMs waitMs(_scheduler.tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE));
    EXPECT_EQ(WaitTimeMs(1), waitMs);
    EXPECT_EQ("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri HIGHEST)\n",
              _priority_db.toString());
}

TEST_P(MaintenanceSchedulerTest, priority_not_cleared_if_operation_not_started_outside_pending_window) {
    _priority_db.setPriority(PrioritizedBucket(makeDocumentBucket(BucketId(16, 1)), Priority::HIGH));
    _operation_starter.setShouldStartOperations(false);
    _pending_window_checker.allow_operations(false);
    WaitTimeMs waitMs(_scheduler.tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE));
    EXPECT_EQ(WaitTimeMs(1), waitMs);
    EXPECT_EQ("PrioritizedBucket(Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000000001)), pri HIGH)\n",
              _priority_db.toString());
}

INSTANTIATE_TEST_SUITE_P(ImplicitClearOfDbPri, MaintenanceSchedulerTest, ::testing::Values(false, true));

}
