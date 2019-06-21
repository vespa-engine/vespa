// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/documentid.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <vespa/vespalib/gtest/gtest.h>

namespace storage::distributor {

using document::DocumentId;

TEST(OperationSequencerTest, can_get_sequencing_handle_for_id_without_existing_handle) {
    OperationSequencer sequencer;
    auto handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    EXPECT_TRUE(handle.valid());
}

TEST(OperationSequencerTest, cannot_get_sequencing_handle_for_id_with_existing_handle) {
    OperationSequencer sequencer;
    auto first_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    auto second_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    EXPECT_FALSE(second_handle.valid());
}

TEST(OperationSequencerTest, can_get_sequencing_handle_for_different_ids) {
    OperationSequencer sequencer;
    auto first_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    auto second_handle = sequencer.try_acquire(DocumentId("id:foo:test::efgh"));
    EXPECT_TRUE(first_handle.valid());
    EXPECT_TRUE(second_handle.valid());
}

TEST(OperationSequencerTest, releasing_handle_allows_for_getting_new_handles_for_id) {
    OperationSequencer sequencer;
    auto first_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    // Explicit release
    first_handle.release();
    {
        auto second_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
        EXPECT_TRUE(second_handle.valid());
        // Implicit release by scope exit
    }
    auto third_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    EXPECT_TRUE(third_handle.valid());
}

} // storage::distributor
