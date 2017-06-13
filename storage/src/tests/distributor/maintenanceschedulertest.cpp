// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/vdstestlib/cppunit/macros.h>
#include <string>
#include <sstream>
#include <memory>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>
#include <vespa/storage/distributor/maintenance/maintenancescheduler.h>
#include <vespa/storage/bucketdb/mapbucketdatabase.h>
#include <tests/distributor/maintenancemocks.h>

namespace storage {

namespace distributor {

using document::BucketId;
typedef MaintenancePriority Priority;
typedef MaintenanceScheduler::WaitTimeMs WaitTimeMs;

class MaintenanceSchedulerTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(MaintenanceSchedulerTest);
    CPPUNIT_TEST(testPriorityClearedAfterScheduled);
    CPPUNIT_TEST(testOperationIsScheduled);
    CPPUNIT_TEST(testNoOperationsToSchedule);
    CPPUNIT_TEST(testSuppressLowPrioritiesInEmergencyMode);
    CPPUNIT_TEST(testPriorityNotClearedIfOperationNotStarted);
    CPPUNIT_TEST_SUITE_END();

    std::unique_ptr<SimpleBucketPriorityDatabase> _priorityDb;
    std::unique_ptr<MockMaintenanceOperationGenerator> _operationGenerator;
    std::unique_ptr<MockOperationStarter> _operationStarter;
    std::unique_ptr<MaintenanceScheduler> _scheduler;

    void addBucketToDb(int bucketNum);
public:
    void testPriorityClearedAfterScheduled();
    void testOperationIsScheduled();
    void testNoOperationsToSchedule();
    void testSuppressLowPrioritiesInEmergencyMode();
    void testPriorityNotClearedIfOperationNotStarted();

    void setUp() override;
};

CPPUNIT_TEST_SUITE_REGISTRATION(MaintenanceSchedulerTest);

void
MaintenanceSchedulerTest::setUp()
{
    _priorityDb.reset(new SimpleBucketPriorityDatabase());
    _operationGenerator.reset(new MockMaintenanceOperationGenerator());
    _operationStarter.reset(new MockOperationStarter());
    _scheduler.reset(new MaintenanceScheduler(*_operationGenerator,
                                              *_priorityDb,
                                              *_operationStarter));
}

void
MaintenanceSchedulerTest::testPriorityClearedAfterScheduled()
{
    _priorityDb->setPriority(PrioritizedBucket(BucketId(16, 1), Priority::VERY_HIGH));
    _scheduler->tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE);
    CPPUNIT_ASSERT_EQUAL(std::string(), _priorityDb->toString());
}

void
MaintenanceSchedulerTest::testOperationIsScheduled()
{
    _priorityDb->setPriority(PrioritizedBucket(BucketId(16, 1), Priority::MEDIUM));
    _scheduler->tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE);
    CPPUNIT_ASSERT_EQUAL(std::string("BucketId(0x4000000000000001), pri 100\n"),
                         _operationStarter->toString());
}

void
MaintenanceSchedulerTest::testNoOperationsToSchedule()
{
    WaitTimeMs waitMs(_scheduler->tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE));
    CPPUNIT_ASSERT_EQUAL(WaitTimeMs(1), waitMs);
    CPPUNIT_ASSERT_EQUAL(std::string(), _operationStarter->toString());
}

void
MaintenanceSchedulerTest::testSuppressLowPrioritiesInEmergencyMode()
{
    _priorityDb->setPriority(PrioritizedBucket(BucketId(16, 1), Priority::HIGH));
    _priorityDb->setPriority(PrioritizedBucket(BucketId(16, 2), Priority::VERY_HIGH));
    CPPUNIT_ASSERT_EQUAL(WaitTimeMs(0), _scheduler->tick(MaintenanceScheduler::RECOVERY_SCHEDULING_MODE));
    CPPUNIT_ASSERT_EQUAL(WaitTimeMs(1), _scheduler->tick(MaintenanceScheduler::RECOVERY_SCHEDULING_MODE));
    CPPUNIT_ASSERT_EQUAL(std::string("BucketId(0x4000000000000002), pri 0\n"),
                         _operationStarter->toString());
    CPPUNIT_ASSERT_EQUAL(std::string("PrioritizedBucket(BucketId(0x4000000000000001), pri HIGH)\n"),
                         _priorityDb->toString());
}

void
MaintenanceSchedulerTest::testPriorityNotClearedIfOperationNotStarted()
{
    _priorityDb->setPriority(PrioritizedBucket(BucketId(16, 1), Priority::HIGH));
    _operationStarter->setShouldStartOperations(false);
    WaitTimeMs waitMs(_scheduler->tick(MaintenanceScheduler::NORMAL_SCHEDULING_MODE));
    CPPUNIT_ASSERT_EQUAL(WaitTimeMs(1), waitMs);
    CPPUNIT_ASSERT_EQUAL(std::string("PrioritizedBucket(BucketId(0x4000000000000001), pri HIGH)\n"),
                         _priorityDb->toString());
}

}
}
