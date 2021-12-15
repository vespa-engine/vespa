// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>

using document::test::makeDocumentBucket;
using namespace ::testing;

namespace storage::distributor {

using document::BucketId;
using Priority = MaintenancePriority;

struct SimpleBucketPriorityDatabaseTest : Test {
    SimpleBucketPriorityDatabase _queue;
};

TEST_F(SimpleBucketPriorityDatabaseTest, iterator_range_is_equal_on_empty_database) {
    auto begin = _queue.begin();
    auto end = _queue.end();

    EXPECT_TRUE(begin == end);
    EXPECT_TRUE(begin == begin);
    EXPECT_TRUE(end == end);
}

TEST_F(SimpleBucketPriorityDatabaseTest, can_get_prioritized_bucket) {
    PrioritizedBucket lowPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::VERY_LOW);
    _queue.setPriority(lowPriBucket);

    PrioritizedBucket highest(*_queue.begin());
    EXPECT_EQ(lowPriBucket, highest);
}

TEST_F(SimpleBucketPriorityDatabaseTest, iterate_over_multiple_priorities) {
    PrioritizedBucket lowPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::LOW);
    PrioritizedBucket highPriBucket(makeDocumentBucket(BucketId(16, 4321)), Priority::HIGH);
    _queue.setPriority(lowPriBucket);
    _queue.setPriority(highPriBucket);

    auto iter = _queue.begin();
    ASSERT_EQ(highPriBucket, *iter);
    ++iter;
    ASSERT_TRUE(iter != _queue.end());
    ASSERT_EQ(lowPriBucket, *iter);
    ++iter;
    ASSERT_TRUE(iter == _queue.end());
}

TEST_F(SimpleBucketPriorityDatabaseTest, multiple_set_priority_for_one_bucket) {
    PrioritizedBucket lowPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::LOW);
    PrioritizedBucket highPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::HIGH);

    _queue.setPriority(lowPriBucket);
    _queue.setPriority(highPriBucket);

    auto iter = _queue.begin();
    ASSERT_EQ(highPriBucket, *iter);
    ++iter;
    ASSERT_TRUE(iter == _queue.end());
}

TEST_F(SimpleBucketPriorityDatabaseTest, no_maintenance_needed_clears_bucket_from_database) {
    PrioritizedBucket highPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::HIGH);
    PrioritizedBucket noPriBucket(makeDocumentBucket(BucketId(16, 1234)),
                                  Priority::NO_MAINTENANCE_NEEDED);
    _queue.setPriority(highPriBucket);
    _queue.setPriority(noPriBucket);

    auto iter = _queue.begin();
    ASSERT_TRUE(iter == _queue.end());
}

TEST_F(SimpleBucketPriorityDatabaseTest, iterate_over_multiple_buckets_with_multiple_priorities) {
    PrioritizedBucket lowPriBucket1(makeDocumentBucket(BucketId(16, 1)), Priority::LOW);
    PrioritizedBucket lowPriBucket2(makeDocumentBucket(BucketId(16, 2)), Priority::LOW);
    PrioritizedBucket mediumPriBucket(makeDocumentBucket(BucketId(16, 3)), Priority::MEDIUM);
    PrioritizedBucket highPriBucket1(makeDocumentBucket(BucketId(16, 4)), Priority::HIGH);
    PrioritizedBucket highPriBucket2(makeDocumentBucket(BucketId(16, 5)), Priority::HIGH);

    _queue.setPriority(highPriBucket1);
    _queue.setPriority(lowPriBucket2);
    _queue.setPriority(mediumPriBucket);
    _queue.setPriority(highPriBucket2);
    _queue.setPriority(lowPriBucket1);

    auto iter = _queue.begin();
    PrioritizedBucket lastBucket(makeDocumentBucket(BucketId()), Priority::PRIORITY_LIMIT);
    for (int i = 0; i < 5; ++i) {
        ASSERT_TRUE(iter != _queue.end());
        ASSERT_FALSE(iter->moreImportantThan(lastBucket));
        lastBucket = *iter;
        ++iter;
    }
    ASSERT_TRUE(iter == _queue.end());
}

TEST_F(SimpleBucketPriorityDatabaseTest, buckets_within_same_priority_class_are_fifo_ordered) {
    // We want FIFO order (2, 1) within the same priority class, not bucket ID order (1, 2).
    PrioritizedBucket first_bucket(makeDocumentBucket(BucketId(16, 2)), Priority::LOW);
    PrioritizedBucket second_bucket(makeDocumentBucket(BucketId(16, 1)), Priority::LOW);

    _queue.setPriority(first_bucket);
    _queue.setPriority(second_bucket);

    auto iter = _queue.begin();
    EXPECT_EQ(first_bucket, *iter);
    ++iter;
    EXPECT_EQ(second_bucket, *iter);
}

}
