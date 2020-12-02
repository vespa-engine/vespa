// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::distributor {

using document::DocumentId;
using namespace ::testing;

namespace {

constexpr document::BucketSpace default_space() {
    return document::FixedBucketSpaces::default_space();
}

constexpr document::BucketSpace global_space() {
    return document::FixedBucketSpaces::global_space();
}

}

struct OperationSequencerTest : Test {
    OperationSequencer sequencer;
};

TEST_F(OperationSequencerTest, can_get_sequencing_handle_for_id_without_existing_handle) {
    auto handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test::abcd"));
    EXPECT_TRUE(handle.valid());
    EXPECT_FALSE(handle.is_blocked());
}

TEST_F(OperationSequencerTest, cannot_get_sequencing_handle_for_id_with_existing_handle) {
    auto first_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test::abcd"));
    auto second_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test::abcd"));
    EXPECT_FALSE(second_handle.valid());
    ASSERT_TRUE(second_handle.is_blocked());
    EXPECT_EQ(second_handle.blocked_by(), SequencingHandle::BlockedBy::PendingOperation);
}

TEST_F(OperationSequencerTest, can_get_sequencing_handle_for_different_ids) {
    auto first_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test::abcd"));
    auto second_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test::efgh"));
    EXPECT_TRUE(first_handle.valid());
    EXPECT_TRUE(second_handle.valid());
}

TEST_F(OperationSequencerTest, releasing_handle_allows_for_getting_new_handles_for_id) {
    auto first_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test::abcd"));
    // Explicit release
    first_handle.release();
    {
        auto second_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test::abcd"));
        EXPECT_TRUE(second_handle.valid());
        // Implicit release by scope exit
    }
    auto third_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test::abcd"));
    EXPECT_TRUE(third_handle.valid());
}

TEST_F(OperationSequencerTest, cannot_get_handle_for_gid_contained_in_locked_bucket) {
    auto bucket_handle = sequencer.try_acquire(document::Bucket(default_space(), document::BucketId(16, 1)));
    EXPECT_TRUE(bucket_handle.valid());
    auto doc_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test:n=1:abcd"));
    EXPECT_FALSE(doc_handle.valid());
    ASSERT_TRUE(doc_handle.is_blocked());
    EXPECT_EQ(doc_handle.blocked_by(), SequencingHandle::BlockedBy::LockedBucket);
}

TEST_F(OperationSequencerTest, can_get_handle_for_gid_not_contained_in_active_bucket) {
    auto bucket_handle = sequencer.try_acquire(document::Bucket(default_space(), document::BucketId(16, 1)));
    EXPECT_TRUE(bucket_handle.valid());
    // Note: different sub-bucket than the lock
    auto doc_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test:n=2:abcd"));
    EXPECT_TRUE(doc_handle.valid());
}

TEST_F(OperationSequencerTest, releasing_bucket_lock_allows_gid_handles_to_be_acquired) {
    auto bucket_handle = sequencer.try_acquire(document::Bucket(default_space(), document::BucketId(16, 1)));
    bucket_handle.release();
    auto doc_handle = sequencer.try_acquire(default_space(), DocumentId("id:foo:test:n=1:abcd"));
    EXPECT_TRUE(doc_handle.valid());
}

TEST_F(OperationSequencerTest, can_get_handle_for_gid_when_locked_bucket_is_in_separate_bucket_space) {
    auto bucket_handle = sequencer.try_acquire(document::Bucket(default_space(), document::BucketId(16, 1)));
    EXPECT_TRUE(bucket_handle.valid());
    auto doc_handle = sequencer.try_acquire(global_space(), DocumentId("id:foo:test:n=1:abcd"));
    EXPECT_TRUE(doc_handle.valid());
}

} // storage::distributor
