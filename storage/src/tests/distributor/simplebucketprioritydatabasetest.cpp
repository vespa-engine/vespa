// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <string>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>

namespace storage {

namespace distributor {

using document::BucketId;
typedef MaintenancePriority Priority;

class SimpleBucketPriorityDatabaseTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(SimpleBucketPriorityDatabaseTest);
    CPPUNIT_TEST(testIteratorRangeIsEqualOnEmptyDatabase);
    CPPUNIT_TEST(testCanGetPrioritizedBucket);
    CPPUNIT_TEST(testIterateOverMultiplePriorities);
    CPPUNIT_TEST(testMultipleSetPriorityForOneBucket);
    CPPUNIT_TEST(testIterateOverMultipleBucketsWithMultiplePriorities);
    CPPUNIT_TEST(testNoMaintenanceNeededClearsBucketFromDatabase);
    CPPUNIT_TEST_SUITE_END();

    typedef SimpleBucketPriorityDatabase::const_iterator const_iterator;

public:
    void testIteratorRangeIsEqualOnEmptyDatabase();
    void testCanGetPrioritizedBucket();
    void testIterateOverMultiplePriorities();
    void testMultipleSetPriorityForOneBucket();
    void testIterateOverMultipleBucketsWithMultiplePriorities();
    void testNoMaintenanceNeededClearsBucketFromDatabase();
};

CPPUNIT_TEST_SUITE_REGISTRATION(SimpleBucketPriorityDatabaseTest);

void
SimpleBucketPriorityDatabaseTest::testIteratorRangeIsEqualOnEmptyDatabase()
{
    SimpleBucketPriorityDatabase queue;
    const_iterator begin(queue.begin());
    const_iterator end(queue.end());

    CPPUNIT_ASSERT(begin == end);
    CPPUNIT_ASSERT(begin == begin);
    CPPUNIT_ASSERT(end == end);
}

void
SimpleBucketPriorityDatabaseTest::testCanGetPrioritizedBucket()
{
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket lowPriBucket(BucketId(16, 1234), Priority::VERY_LOW);
    queue.setPriority(lowPriBucket);

    PrioritizedBucket highest(*queue.begin());
    CPPUNIT_ASSERT_EQUAL(lowPriBucket, highest);
}

void
SimpleBucketPriorityDatabaseTest::testIterateOverMultiplePriorities()
{
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket lowPriBucket(BucketId(16, 1234), Priority::LOW);
    PrioritizedBucket highPriBucket(BucketId(16, 4321), Priority::HIGH);
    queue.setPriority(lowPriBucket);
    queue.setPriority(highPriBucket);

    const_iterator iter(queue.begin());
    CPPUNIT_ASSERT_EQUAL(highPriBucket, *iter);
    ++iter;
    CPPUNIT_ASSERT(iter != queue.end());
    CPPUNIT_ASSERT_EQUAL(lowPriBucket, *iter);
    ++iter;
    CPPUNIT_ASSERT(iter == queue.end());
}

void
SimpleBucketPriorityDatabaseTest::testMultipleSetPriorityForOneBucket()
{
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket lowPriBucket(BucketId(16, 1234), Priority::LOW);
    PrioritizedBucket highPriBucket(BucketId(16, 1234), Priority::HIGH);

    queue.setPriority(lowPriBucket);
    queue.setPriority(highPriBucket);

    const_iterator iter(queue.begin());
    CPPUNIT_ASSERT_EQUAL(highPriBucket, *iter);
    ++iter;
    CPPUNIT_ASSERT(iter == queue.end());
}

void
SimpleBucketPriorityDatabaseTest::testNoMaintenanceNeededClearsBucketFromDatabase()
{
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket highPriBucket(BucketId(16, 1234), Priority::HIGH);
    PrioritizedBucket noPriBucket(BucketId(16, 1234),
                                  Priority::NO_MAINTENANCE_NEEDED);
    queue.setPriority(highPriBucket);
    queue.setPriority(noPriBucket);

    const_iterator iter(queue.begin());
    CPPUNIT_ASSERT(iter == queue.end());
}

void
SimpleBucketPriorityDatabaseTest::testIterateOverMultipleBucketsWithMultiplePriorities()
{
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket lowPriBucket1(BucketId(16, 1), Priority::LOW);
    PrioritizedBucket lowPriBucket2(BucketId(16, 2), Priority::LOW);
    PrioritizedBucket mediumPriBucket(BucketId(16, 3), Priority::MEDIUM);
    PrioritizedBucket highPriBucket1(BucketId(16, 4), Priority::HIGH);
    PrioritizedBucket highPriBucket2(BucketId(16, 5), Priority::HIGH);

    queue.setPriority(highPriBucket1);
    queue.setPriority(lowPriBucket2);
    queue.setPriority(mediumPriBucket);
    queue.setPriority(highPriBucket2);
    queue.setPriority(lowPriBucket1);

    const_iterator iter(queue.begin());
    PrioritizedBucket lastBucket(BucketId(), Priority::PRIORITY_LIMIT);
    for (int i = 0; i < 5; ++i) {
        CPPUNIT_ASSERT(iter != queue.end());
        CPPUNIT_ASSERT(!iter->moreImportantThan(lastBucket));
        lastBucket = *iter;
        ++iter;
    }
    CPPUNIT_ASSERT(iter == queue.end());
}

}
}

