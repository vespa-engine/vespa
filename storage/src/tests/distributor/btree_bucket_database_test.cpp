// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "bucketdatabasetest.h"
#include <vespa/storage/bucketdb/btree_bucket_database.h>
#include <gtest/gtest.h>
#include <gmock/gmock.h>

using namespace ::testing;

namespace storage::distributor {

VESPA_GTEST_INSTANTIATE_TEST_SUITE_P(BTreeDatabase, BucketDatabaseTest,
                                     ::testing::Values(std::make_shared<BTreeBucketDatabase>()));

using document::BucketId;

namespace {

BucketCopy BC(uint32_t node_idx, uint32_t state) {
    api::BucketInfo info(0x123, state, state);
    return BucketCopy(0, node_idx, info);
}


BucketInfo BI(uint32_t node_idx, uint32_t state) {
    BucketInfo bi;
    bi.addNode(BC(node_idx, state), toVector<uint16_t>(0));
    return bi;
}

}

struct BTreeReadGuardTest : Test {
    BTreeBucketDatabase _db;
};

TEST_F(BTreeReadGuardTest, guard_does_not_observe_new_entries) {
    auto guard = _db.acquire_read_guard();
    _db.update(BucketDatabase::Entry(BucketId(16, 16), BI(1, 1234)));

    auto entries = guard->find_parents_and_self(BucketId(16, 16));
    EXPECT_EQ(entries.size(), 0U);
    entries = guard->find_parents_self_and_children(BucketId(16, 16));
    EXPECT_EQ(entries.size(), 0U);
}

TEST_F(BTreeReadGuardTest, guard_observes_entries_alive_at_acquire_time) {
    BucketId bucket(16, 16);
    _db.update(BucketDatabase::Entry(bucket, BI(1, 1234)));
    auto guard = _db.acquire_read_guard();
    _db.remove(bucket);

    auto entries = guard->find_parents_and_self(bucket);
    ASSERT_EQ(entries.size(), 1U);
    EXPECT_EQ(entries[0].getBucketInfo(), BI(1, 1234));

    entries = guard->find_parents_self_and_children(bucket);
    ASSERT_EQ(entries.size(), 1U);
    EXPECT_EQ(entries[0].getBucketInfo(), BI(1, 1234));
}

}

