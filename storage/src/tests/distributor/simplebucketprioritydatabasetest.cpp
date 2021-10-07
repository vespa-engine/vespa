// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/maintenance/simplebucketprioritydatabase.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <string>

using document::test::makeDocumentBucket;

namespace storage::distributor {

using document::BucketId;
using Priority = MaintenancePriority;

TEST(SimpleBucketPriorityDatabaseTest, iterator_range_is_equal_on_empty_database) {
    SimpleBucketPriorityDatabase queue;
    auto begin = queue.begin();
    auto end = queue.end();

    EXPECT_TRUE(begin == end);
    EXPECT_TRUE(begin == begin);
    EXPECT_TRUE(end == end);
}

TEST(SimpleBucketPriorityDatabaseTest, can_get_prioritized_bucket) {
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket lowPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::VERY_LOW);
    queue.setPriority(lowPriBucket);

    PrioritizedBucket highest(*queue.begin());
    EXPECT_EQ(lowPriBucket, highest);
}

TEST(SimpleBucketPriorityDatabaseTest, iterate_over_multiple_priorities) {
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket lowPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::LOW);
    PrioritizedBucket highPriBucket(makeDocumentBucket(BucketId(16, 4321)), Priority::HIGH);
    queue.setPriority(lowPriBucket);
    queue.setPriority(highPriBucket);

    auto iter = queue.begin();
    ASSERT_EQ(highPriBucket, *iter);
    ++iter;
    ASSERT_TRUE(iter != queue.end());
    ASSERT_EQ(lowPriBucket, *iter);
    ++iter;
    ASSERT_TRUE(iter == queue.end());
}

TEST(SimpleBucketPriorityDatabaseTest, multiple_set_priority_for_one_bucket) {
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket lowPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::LOW);
    PrioritizedBucket highPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::HIGH);

    queue.setPriority(lowPriBucket);
    queue.setPriority(highPriBucket);

    auto iter = queue.begin();
    ASSERT_EQ(highPriBucket, *iter);
    ++iter;
    ASSERT_TRUE(iter == queue.end());
}

TEST(SimpleBucketPriorityDatabaseTest, no_maintenance_needed_clears_bucket_from_database) {
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket highPriBucket(makeDocumentBucket(BucketId(16, 1234)), Priority::HIGH);
    PrioritizedBucket noPriBucket(makeDocumentBucket(BucketId(16, 1234)),
                                  Priority::NO_MAINTENANCE_NEEDED);
    queue.setPriority(highPriBucket);
    queue.setPriority(noPriBucket);

    auto iter = queue.begin();
    ASSERT_TRUE(iter == queue.end());
}

TEST(SimpleBucketPriorityDatabaseTest, iterate_over_multiple_buckets_with_multiple_priorities) {
    SimpleBucketPriorityDatabase queue;

    PrioritizedBucket lowPriBucket1(makeDocumentBucket(BucketId(16, 1)), Priority::LOW);
    PrioritizedBucket lowPriBucket2(makeDocumentBucket(BucketId(16, 2)), Priority::LOW);
    PrioritizedBucket mediumPriBucket(makeDocumentBucket(BucketId(16, 3)), Priority::MEDIUM);
    PrioritizedBucket highPriBucket1(makeDocumentBucket(BucketId(16, 4)), Priority::HIGH);
    PrioritizedBucket highPriBucket2(makeDocumentBucket(BucketId(16, 5)), Priority::HIGH);

    queue.setPriority(highPriBucket1);
    queue.setPriority(lowPriBucket2);
    queue.setPriority(mediumPriBucket);
    queue.setPriority(highPriBucket2);
    queue.setPriority(lowPriBucket1);

    auto iter = queue.begin();
    PrioritizedBucket lastBucket(makeDocumentBucket(BucketId()), Priority::PRIORITY_LIMIT);
    for (int i = 0; i < 5; ++i) {
        ASSERT_TRUE(iter != queue.end());
        ASSERT_FALSE(iter->moreImportantThan(lastBucket));
        lastBucket = *iter;
        ++iter;
    }
    ASSERT_TRUE(iter == queue.end());
}

}
