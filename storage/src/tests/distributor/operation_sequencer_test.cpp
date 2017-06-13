// Copyright 2017 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/distributor/operation_sequencer.h>
#include <vespa/document/base/documentid.h>

namespace storage::distributor {

using document::DocumentId;

class OperationSequencerTest : public CppUnit::TestFixture {
    CPPUNIT_TEST_SUITE(OperationSequencerTest);
    CPPUNIT_TEST(can_get_sequencing_handle_for_id_without_existing_handle);
    CPPUNIT_TEST(can_get_sequencing_handle_for_different_ids);
    CPPUNIT_TEST(cannot_get_sequencing_handle_for_id_with_existing_handle);
    CPPUNIT_TEST(releasing_handle_allows_for_getting_new_handles_for_id);
    CPPUNIT_TEST_SUITE_END();

    void can_get_sequencing_handle_for_id_without_existing_handle();
    void can_get_sequencing_handle_for_different_ids();
    void cannot_get_sequencing_handle_for_id_with_existing_handle();
    void releasing_handle_allows_for_getting_new_handles_for_id();
};

CPPUNIT_TEST_SUITE_REGISTRATION(OperationSequencerTest);

void OperationSequencerTest::can_get_sequencing_handle_for_id_without_existing_handle() {
    OperationSequencer sequencer;
    auto handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    CPPUNIT_ASSERT(handle.valid());
}

void OperationSequencerTest::cannot_get_sequencing_handle_for_id_with_existing_handle() {
    OperationSequencer sequencer;
    auto first_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    auto second_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    CPPUNIT_ASSERT(! second_handle.valid());
}

void OperationSequencerTest::can_get_sequencing_handle_for_different_ids() {
    OperationSequencer sequencer;
    auto first_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    auto second_handle = sequencer.try_acquire(DocumentId("id:foo:test::efgh"));
    CPPUNIT_ASSERT(first_handle.valid());
    CPPUNIT_ASSERT(second_handle.valid());
}

void OperationSequencerTest::releasing_handle_allows_for_getting_new_handles_for_id() {
    OperationSequencer sequencer;
    auto first_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    // Explicit release
    first_handle.release();
    {
        auto second_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
        CPPUNIT_ASSERT(second_handle.valid());
        // Implicit release by scope exit
    }
    auto third_handle = sequencer.try_acquire(DocumentId("id:foo:test::abcd"));
    CPPUNIT_ASSERT(third_handle.valid());
}

} // storage::distributor
