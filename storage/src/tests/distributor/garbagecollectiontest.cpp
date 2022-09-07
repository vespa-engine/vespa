// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "dummy_cluster_context.h"
#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/idealstatemanager.h>
#include <vespa/storage/distributor/idealstatemetricsset.h>
#include <vespa/storage/distributor/operations/idealstate/garbagecollectionoperation.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using document::BucketId;
using document::DocumentId;
using document::FixedBucketSpaces;
using namespace ::testing;

namespace storage::distributor {

struct GarbageCollectionOperationTest : Test, DistributorStripeTestUtil {
    BucketId            _bucket_id;
    OperationSequencer  _operation_sequencer;
    uint32_t            _gc_start_time_sec;
    spi::IdAndTimestamp _e1;
    spi::IdAndTimestamp _e2;
    spi::IdAndTimestamp _e3;
    spi::IdAndTimestamp _e4;
    spi::IdAndTimestamp _e5;

    GarbageCollectionOperationTest()
        : _bucket_id(16, 1),
          _operation_sequencer(),
          _gc_start_time_sec(34),
          _e1(DocumentId("id:foo:bar::doc-1"), spi::Timestamp(100)),
          _e2(DocumentId("id:foo:bar::doc-2"), spi::Timestamp(200)),
          _e3(DocumentId("id:foo:bar::doc-3"), spi::Timestamp(300)),
          _e4(DocumentId("id:foo:bar::doc-4"), spi::Timestamp(400)),
          _e5(DocumentId("id:foo:bar::doc-4"), spi::Timestamp(500)) // Same as e4 but with higher timestamp
    {}

    void SetUp() override {
        createLinks();
        enable_cluster_state("version:10 distributor:1 storage:2");
        addNodesToBucketDB(_bucket_id, "0=250/50/300,1=250/50/300");
        auto cfg = make_config();
        cfg->setGarbageCollection("music.date < 34", 3600s);
        configure_stripe(cfg);
        getClock().setAbsoluteTimeInSeconds(_gc_start_time_sec);
        _sender.set_operation_sequencer(_operation_sequencer);
    };

    void TearDown() override {
        close();
    }

    void enable_two_phase_gc() {
        NodeSupportedFeatures with_two_phase;
        with_two_phase.two_phase_remove_location = true;
        set_node_supported_features(0, with_two_phase);
        set_node_supported_features(1, with_two_phase);

        config_enable_two_phase_gc(true);
    }

    void config_enable_two_phase_gc(bool enabled) {
        auto config = make_config();
        config->set_enable_two_phase_garbage_collection(enabled);
        configure_stripe(std::move(config));
    }

    std::shared_ptr<GarbageCollectionOperation> create_op() {
        auto op = std::make_shared<GarbageCollectionOperation>(
                dummy_cluster_context, BucketAndNodes(makeDocumentBucket(_bucket_id),
                                                            toVector<uint16_t>(0, 1)));
        op->setIdealStateManager(&getIdealStateManager());
        return op;
    }

    static std::shared_ptr<api::RemoveLocationCommand> as_remove_location_command(const std::shared_ptr<api::StorageCommand>& cmd) {
        auto msg = std::dynamic_pointer_cast<api::RemoveLocationCommand>(cmd);
        assert(msg);
        return msg;
    }

    static std::shared_ptr<api::RemoveLocationReply> make_remove_location_reply(api::StorageCommand& msg) {
        auto reply = std::shared_ptr<api::StorageReply>(msg.makeReply());
        assert(reply->getType() == api::MessageType::REMOVELOCATION_REPLY);
        return std::dynamic_pointer_cast<api::RemoveLocationReply>(reply);
    }

    // FIXME fragile to assume that send order == node index, but that's the way it currently works
    void reply_to_nth_request(GarbageCollectionOperation& op, size_t n,
                              uint32_t bucket_info_checksum, uint32_t n_docs_removed) {
        auto msg = _sender.command(n);
        assert(msg->getType() == api::MessageType::REMOVELOCATION);
        std::shared_ptr<api::StorageReply> reply(msg->makeReply());
        auto& gc_reply = dynamic_cast<api::RemoveLocationReply&>(*reply);
        gc_reply.set_documents_removed(n_docs_removed);
        gc_reply.setBucketInfo(api::BucketInfo(bucket_info_checksum, 90, 500));

        op.receive(_sender, reply);
    }

    void assert_bucket_last_gc_timestamp_is(uint32_t gc_time) {
        BucketDatabase::Entry entry = getBucket(_bucket_id);
        ASSERT_TRUE(entry.valid());
        EXPECT_EQ(entry->getLastGarbageCollectionTime(), gc_time);
    }

    void assert_bucket_db_contains(std::vector<api::BucketInfo> info, uint32_t last_gc_time) {
        BucketDatabase::Entry entry = getBucket(_bucket_id);
        ASSERT_TRUE(entry.valid());
        ASSERT_EQ(entry->getNodeCount(), info.size());
        EXPECT_EQ(entry->getLastGarbageCollectionTime(), last_gc_time);
        for (size_t i = 0; i < info.size(); ++i) {
            EXPECT_EQ(info[i], entry->getNode(i)->getBucketInfo())
                    << "Mismatching info for node " << i << ": " << info[i] << " vs "
                    << entry->getNode(i)->getBucketInfo();
        }
    }

    uint32_t gc_removed_documents_metric() {
        auto metric_base = getIdealStateManager().getMetrics().operations[IdealStateOperation::GARBAGE_COLLECTION];
        auto gc_metrics = std::dynamic_pointer_cast<GcMetricSet>(metric_base);
        assert(gc_metrics);
        return gc_metrics->documents_removed.getValue();
    }

    void assert_gc_op_completed_ok_without_second_phase(GarbageCollectionOperation& op) {
        ASSERT_EQ(0u, _sender.commands().size());
        EXPECT_TRUE(op.is_done());
        EXPECT_TRUE(op.ok()); // It's not a failure to have nothing to do
        // GC timestamp must be updated so we can move on to another bucket.
        EXPECT_NO_FATAL_FAILURE(assert_bucket_last_gc_timestamp_is(_gc_start_time_sec));
        EXPECT_EQ(0u, gc_removed_documents_metric()); // Nothing removed
    }
};

TEST_F(GarbageCollectionOperationTest, simple_legacy) {
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    EXPECT_FALSE(op->is_two_phase());

    ASSERT_EQ(2, _sender.commands().size());
    EXPECT_EQ(0u, gc_removed_documents_metric());

    for (uint32_t i = 0; i < 2; ++i) {
        std::shared_ptr<api::StorageCommand> msg = _sender.command(i);
        ASSERT_EQ(msg->getType(), api::MessageType::REMOVELOCATION);
        auto& tmp = dynamic_cast<api::RemoveLocationCommand&>(*msg);
        EXPECT_EQ("music.date < 34", tmp.getDocumentSelection());
        reply_to_nth_request(*op, i, 777 + i, 50);
    }
    ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(777, 90, 500), api::BucketInfo(778, 90, 500)}, 34));
    EXPECT_EQ(50u, gc_removed_documents_metric());
}

TEST_F(GarbageCollectionOperationTest, replica_bucket_info_not_added_to_db_until_all_replies_received) {
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    ASSERT_EQ(2, _sender.commands().size());
    EXPECT_EQ(0u, gc_removed_documents_metric());

    // Respond to 1st request. Should _not_ cause bucket info to be merged into the database yet
    reply_to_nth_request(*op, 0, 1234, 70);
    ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(250, 50, 300), api::BucketInfo(250, 50, 300)}, 0));

    // Respond to 2nd request. This _should_ cause bucket info to be merged into the database.
    reply_to_nth_request(*op, 1, 4567, 60);
    ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(1234, 90, 500), api::BucketInfo(4567, 90, 500)}, 34));

    EXPECT_EQ(70u, gc_removed_documents_metric()); // Use max of received metrics
}

TEST_F(GarbageCollectionOperationTest, gc_bucket_info_does_not_overwrite_later_sequenced_bucket_info_writes) {
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    ASSERT_EQ(2, _sender.commands().size());

    reply_to_nth_request(*op, 0, 1234, 0);
    // Change to replica on node 0 happens after GC op, but before GC info is merged into the DB. Must not be lost.
    insertBucketInfo(op->getBucketId(), 0, 7777, 100, 2000);
    reply_to_nth_request(*op, 1, 4567, 0);
    // Bucket info for node 0 is that of the later sequenced operation, _not_ from the earlier GC op.
    ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(7777, 100, 2000), api::BucketInfo(4567, 90, 500)}, 34));
}

TEST_F(GarbageCollectionOperationTest, two_phase_gc_requires_config_enabling_and_explicit_node_support) {
    NodeSupportedFeatures with_two_phase;
    with_two_phase.two_phase_remove_location = true;
    set_node_supported_features(1, with_two_phase);

    config_enable_two_phase_gc(true);

    // Config enabled, but only 1 node says it supports two-phase RemoveLocation
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    EXPECT_FALSE(op->is_two_phase());

    // Node 0 suddenly upgraded...!
    set_node_supported_features(0, with_two_phase);
    op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    EXPECT_TRUE(op->is_two_phase());

    // But doesn't matter if two-phase GC is config-disabled
    config_enable_two_phase_gc(false);

    op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    EXPECT_FALSE(op->is_two_phase());
}

TEST_F(GarbageCollectionOperationTest, first_phase_sends_enumerate_only_remove_locations_with_provided_gc_pri) {
    enable_two_phase_gc();
    auto op = create_op();
    op->setPriority(getConfig().getMaintenancePriorities().garbageCollection);
    op->start(_sender, framework::MilliSecTime(0));
    ASSERT_EQ(2, _sender.commands().size());

    for (int i : {0, 1}) {
        auto cmd = as_remove_location_command(_sender.command(i));
        EXPECT_TRUE(cmd->only_enumerate_docs());
        EXPECT_EQ(cmd->getPriority(), getConfig().getMaintenancePriorities().garbageCollection);
    }
}

TEST_F(GarbageCollectionOperationTest, second_phase_sends_highest_timestamped_union_of_returned_entries_with_feed_pri) {
    enable_two_phase_gc();
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    ASSERT_EQ(2, _sender.commands().size());

    auto r1 = make_remove_location_reply(*_sender.command(0));
    r1->set_selection_matches({_e1, _e2, _e3, _e5});
    auto r2 = make_remove_location_reply(*_sender.command(1));
    r2->set_selection_matches({_e2, _e3, _e4});

    _sender.commands().clear();
    op->receive(_sender, r1);
    ASSERT_EQ(0u, _sender.commands().size()); // No phase 2 yet, must get reply from all nodes
    op->receive(_sender, r2);
    ASSERT_EQ(2u, _sender.commands().size()); // Phase 2 sent

    // e5 is same doc as e4, but at a higher timestamp; only e5 entry should be included.
    std::vector<spi::IdAndTimestamp> expected({_e1, _e2, _e3, _e5});
    for (int i : {0, 1}) {
        auto cmd = as_remove_location_command(_sender.command(i));
        EXPECT_FALSE(cmd->only_enumerate_docs());
        EXPECT_EQ(cmd->explicit_remove_set(), expected);
        EXPECT_EQ(cmd->getPriority(), getConfig().default_external_feed_priority());
    }
}

TEST_F(GarbageCollectionOperationTest, no_second_phase_if_first_phase_has_no_results) {
    enable_two_phase_gc();
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    ASSERT_EQ(2, _sender.commands().size());

    auto r1 = make_remove_location_reply(*_sender.command(0));
    auto r2 = make_remove_location_reply(*_sender.command(1));
    _sender.commands().clear();
    // Empty result sets in both replies
    op->receive(_sender, r1);
    op->receive(_sender, r2);

    EXPECT_NO_FATAL_FAILURE(assert_gc_op_completed_ok_without_second_phase(*op));
}

TEST_F(GarbageCollectionOperationTest, db_metrics_and_timestamp_are_updated_on_second_phase_completion) {
    enable_two_phase_gc();
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    ASSERT_EQ(2, _sender.commands().size());

    auto r1 = make_remove_location_reply(*_sender.command(0));
    r1->set_selection_matches({_e1, _e2, _e3});
    auto r2 = make_remove_location_reply(*_sender.command(1));
    r2->set_selection_matches({_e2, _e3, _e4});

    _sender.commands().clear();
    op->receive(_sender, r1);
    op->receive(_sender, r2);
    ASSERT_EQ(2u, _sender.commands().size()); // Phase 2 sent

    r1 = make_remove_location_reply(*_sender.command(0));
    r1->set_documents_removed(3);
    r1->setBucketInfo(api::BucketInfo(0x1234, 90, 500));

    r2 = make_remove_location_reply(*_sender.command(1));
    r2->set_documents_removed(3);
    r2->setBucketInfo(api::BucketInfo(0x4567, 90, 500));

    op->receive(_sender, r1);
    op->receive(_sender, r2);

    EXPECT_TRUE(op->ok());
    EXPECT_TRUE(op->is_done());
    EXPECT_EQ(3u, gc_removed_documents_metric());
    ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(0x1234, 90, 500),
                                                       api::BucketInfo(0x4567, 90, 500)},
                                                      _gc_start_time_sec));
}

struct GarbageCollectionOperationPhase1FailureTest : GarbageCollectionOperationTest {
    std::shared_ptr<GarbageCollectionOperation> _op;
    std::shared_ptr<api::RemoveLocationReply>   _r1;
    std::shared_ptr<api::RemoveLocationReply>   _r2;

    void SetUp() override {
        GarbageCollectionOperationTest::SetUp();

        enable_two_phase_gc();
        _op = create_op();
        _op->start(_sender, framework::MilliSecTime(0));
        ASSERT_EQ(2, _sender.commands().size());

        _r1 = make_remove_location_reply(*_sender.command(0));
        _r1->set_selection_matches({_e1});
        _r2 = make_remove_location_reply(*_sender.command(1));
        _r2->set_selection_matches({_e1});
    }

    void receive_phase1_replies() {
        _sender.commands().clear();
        _op->receive(_sender, _r1);
        _op->receive(_sender, _r2);
    }

    void receive_phase1_replies_and_assert_no_phase_2_started() {
        receive_phase1_replies();
        ASSERT_EQ(0u, _sender.commands().size());
        EXPECT_TRUE(_op->is_done());
        EXPECT_FALSE(_op->ok());
        // GC not completed, so timestamp/bucket DB are _not_ updated
        ASSERT_NO_FATAL_FAILURE(assert_bucket_db_contains({api::BucketInfo(250, 50, 300), // test init values
                                                           api::BucketInfo(250, 50, 300)},
                                                          0/*GC start timestamp*/));
        EXPECT_EQ(0u, gc_removed_documents_metric()); // Nothing removed
    }
};

TEST_F(GarbageCollectionOperationPhase1FailureTest, no_second_phase_if_failure_during_first_phase) {
    _r2->setResult(api::ReturnCode(api::ReturnCode::TIMEOUT, "oh no"));
    receive_phase1_replies_and_assert_no_phase_2_started();
}

TEST_F(GarbageCollectionOperationPhase1FailureTest, no_second_phase_if_cluster_state_version_changed_between_phases) {
    enable_cluster_state("version:11 distributor:1 storage:2"); // version 10 -> 11
    receive_phase1_replies_and_assert_no_phase_2_started();
}

TEST_F(GarbageCollectionOperationPhase1FailureTest, no_second_phase_if_pending_cluster_state_between_phases) {
    simulate_set_pending_cluster_state("version:11 distributor:1 storage:2"); // Pending; not enabled yet
    receive_phase1_replies_and_assert_no_phase_2_started();
}

TEST_F(GarbageCollectionOperationPhase1FailureTest, no_second_phase_if_bucket_inconsistently_split_between_phases) {
    // Add a logical child of _bucket_id to the bucket tree. This implies an inconsistent split, as we never
    // want to have a tree with buckets in inner node positions, only in leaves.
    addNodesToBucketDB(BucketId(17, 1), "0=250/50/300,1=250/50/300");
    receive_phase1_replies_and_assert_no_phase_2_started();
}

TEST_F(GarbageCollectionOperationTest, document_level_write_locks_are_checked_and_held_if_acquired) {
    enable_two_phase_gc();
    auto op = create_op();
    op->start(_sender, framework::MilliSecTime(0));
    ASSERT_EQ(2, _sender.commands().size());

    auto r1 = make_remove_location_reply(*_sender.command(0));
    r1->set_selection_matches({_e1, _e2, _e3});
    auto r2 = make_remove_location_reply(*_sender.command(1));
    r2->set_selection_matches({_e1, _e2, _e3});

    // Grab a lock on e2 to simulate a concurrent write to the document.
    auto e2_lock = _operation_sequencer.try_acquire(FixedBucketSpaces::default_space(), _e2.id);
    ASSERT_TRUE(e2_lock.valid());

    _sender.commands().clear();
    op->receive(_sender, r1);
    op->receive(_sender, r2);
    ASSERT_EQ(2, _sender.commands().size());

    // Locks on e1 and e3 are held while GC removes are sent
    auto e1_lock = _operation_sequencer.try_acquire(FixedBucketSpaces::default_space(), _e1.id);
    EXPECT_FALSE(e1_lock.valid());
    auto e3_lock = _operation_sequencer.try_acquire(FixedBucketSpaces::default_space(), _e3.id);
    EXPECT_FALSE(e3_lock.valid());

    std::vector<spi::IdAndTimestamp> expected({_e1, _e3}); // e2 not included in remove set
    for (int i : {0, 1}) {
        auto cmd = as_remove_location_command(_sender.command(i));
        EXPECT_EQ(cmd->explicit_remove_set(), expected);
    }

    // Locks are implicitly released when the underlying operation is destroyed
    op.reset();
    e1_lock = _operation_sequencer.try_acquire(FixedBucketSpaces::default_space(), _e1.id);
    EXPECT_TRUE(e1_lock.valid());
    e3_lock = _operation_sequencer.try_acquire(FixedBucketSpaces::default_space(), _e3.id);
    EXPECT_TRUE(e3_lock.valid());
}

} // storage::distributor
