// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributor_stripe_test_util.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/storage/common/reindexing_constants.h>
#include <vespa/storage/distributor/top_level_distributor.h>
#include <vespa/storage/distributor/distributor_bucket_space.h>
#include <vespa/storage/distributor/distributor_stripe.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storage/distributor/operations/external/getoperation.h>
#include <vespa/storage/distributor/operations/external/read_for_write_visitor_operation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/visitor.h>
#include <vespa/vespalib/gtest/gtest.h>

using document::test::makeDocumentBucket;
using document::DocumentId;
using document::FieldUpdate;
using document::StringFieldValue;
using document::AssignValueUpdate;
using namespace ::testing;

namespace storage::distributor {

struct ExternalOperationHandlerTest : Test, DistributorStripeTestUtil {
    document::TestDocMan _testDocMan;

    document::BucketId findNonOwnedUserBucketInState(vespalib::stringref state);
    document::BucketId findOwned1stNotOwned2ndInStates(vespalib::stringref state1, vespalib::stringref state2);

    std::shared_ptr<api::GetCommand> makeGetCommandForUser(uint64_t id) const;
    std::shared_ptr<api::GetCommand> makeGetCommand(const vespalib::string& id) const;
    std::shared_ptr<api::UpdateCommand> makeUpdateCommand(const vespalib::string& doc_type, const vespalib::string& id) const;
    std::shared_ptr<api::UpdateCommand> makeUpdateCommand() const;
    std::shared_ptr<api::UpdateCommand> makeUpdateCommandForUser(uint64_t id) const;
    std::shared_ptr<api::PutCommand> makePutCommand(const vespalib::string& doc_type, const vespalib::string& id) const;
    std::shared_ptr<api::RemoveCommand> makeRemoveCommand(const vespalib::string& id) const;

    void verify_busy_bounced_due_to_no_active_state(std::shared_ptr<api::StorageCommand> cmd);

    void start_operation_verify_not_rejected(std::shared_ptr<api::StorageCommand> cmd, Operation::SP& out_generated);
    void start_operation_verify_rejected(std::shared_ptr<api::StorageCommand> cmd);

    int64_t safe_time_not_reached_metric_count(const PersistenceOperationMetricSet & metrics) const {
        return metrics.failures.safe_time_not_reached.getLongValue("count");
    }

    int64_t safe_time_not_reached_metric_count(const UpdateMetricSet & metrics) const {
        return metrics.failures.safe_time_not_reached.getLongValue("count");
    }

    int64_t concurrent_mutatations_metric_count(const PersistenceOperationMetricSet& metrics) const {
        return metrics.failures.concurrent_mutations.getLongValue("count");
    }

    int64_t concurrent_mutatations_metric_count(const UpdateMetricSet & metrics) const {
        return metrics.failures.concurrent_mutations.getLongValue("count");
    }

    void set_up_distributor_for_sequencing_test();

    void set_up_distributor_with_feed_blocked_state();

    const vespalib::string _dummy_id{"id:foo:testdoctype1::bar"};

    // Returns an arbitrary bucket not owned in the pending state
    document::BucketId set_up_pending_cluster_state_transition(bool read_only_enabled);

    void assert_rejection_due_to_unsafe_time(
            std::shared_ptr<api::StorageCommand> cmd);

    void assert_second_command_rejected_due_to_concurrent_mutation(
            std::shared_ptr<api::StorageCommand> cmd1,
            std::shared_ptr<api::StorageCommand> cmd2,
            const vespalib::string& expected_id_in_message);
    void assert_second_command_not_rejected_due_to_concurrent_mutation(
            std::shared_ptr<api::StorageCommand> cmd1,
            std::shared_ptr<api::StorageCommand> cmd2);

    void TearDown() override {
        close();
    }

    void do_test_get_weak_consistency_is_propagated(bool use_weak);
};

TEST_F(ExternalOperationHandlerTest, bucket_split_mask) {
    {
        createLinks();
        getDirConfig().getConfig("stor-distributormanager").set("minsplitcount", "16");

        EXPECT_EQ(document::BucketId(16, 0xffff),
                operation_context().make_split_bit_constrained_bucket_id(document::DocumentId(
                    vespalib::make_string("id:ns:test:n=%d::", 0xffff))
                ).stripUnused());
        EXPECT_EQ(document::BucketId(16, 0),
                operation_context().make_split_bit_constrained_bucket_id(document::DocumentId(
                    vespalib::make_string("id:ns:test:n=%d::", 0x10000))
                ).stripUnused());
        EXPECT_EQ(document::BucketId(16, 0xffff),
                operation_context().make_split_bit_constrained_bucket_id(document::DocumentId(
                    vespalib::make_string("id:ns:test:n=%d::", 0xffff))
                ).stripUnused());
        EXPECT_EQ(document::BucketId(16, 0x100),
                operation_context().make_split_bit_constrained_bucket_id(document::DocumentId(
                    vespalib::make_string("id:ns:test:n=%d::", 0x100))
                ).stripUnused());
        close();
    }
    {
        getDirConfig().getConfig("stor-distributormanager").set("minsplitcount", "20");
        createLinks();
        EXPECT_EQ(document::BucketId(20, 0x11111),
                operation_context().make_split_bit_constrained_bucket_id(document::DocumentId(
                    vespalib::make_string("id:ns:test:n=%d::", 0x111111))
                ).stripUnused());
        EXPECT_EQ(document::BucketId(20, 0x22222),
                operation_context().make_split_bit_constrained_bucket_id(document::DocumentId(
                    vespalib::make_string("id:ns:test:n=%d::", 0x222222))
                ).stripUnused());
    }
}

document::BucketId
ExternalOperationHandlerTest::findNonOwnedUserBucketInState(
        vespalib::stringref statestr)
{
    lib::ClusterState state(statestr);
    for (uint64_t i = 1; i < 1000; ++i) {
        document::BucketId bucket(32, i);
        if (!getDistributorBucketSpace().owns_bucket_in_state(state, bucket)) {
            return bucket;
        }
    }
    throw std::runtime_error("no appropriate bucket found");
}

document::BucketId
ExternalOperationHandlerTest::findOwned1stNotOwned2ndInStates(
        vespalib::stringref statestr1,
        vespalib::stringref statestr2)
{
    lib::ClusterState state1(statestr1);
    lib::ClusterState state2(statestr2);
    for (uint64_t i = 1; i < 1000; ++i) {
        document::BucketId bucket(32, i);
        if (getDistributorBucketSpace().owns_bucket_in_state(state1, bucket)
            && !getDistributorBucketSpace().owns_bucket_in_state(state2, bucket))
        {
            return bucket;
        }
    }
    throw std::runtime_error("no appropriate bucket found");
}

std::shared_ptr<api::GetCommand>
ExternalOperationHandlerTest::makeGetCommand(const vespalib::string& id) const {
    return std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId(0)), DocumentId(id), document::AllFields::NAME);
}

std::shared_ptr<api::GetCommand>
ExternalOperationHandlerTest::makeGetCommandForUser(uint64_t id) const {
    DocumentId docId(vespalib::make_string("id:foo:test:n=%" PRIu64 ":bar", id));
    return std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId(0)), docId, document::AllFields::NAME);
}

std::shared_ptr<api::UpdateCommand> ExternalOperationHandlerTest::makeUpdateCommand(
        const vespalib::string& doc_type,
        const vespalib::string& id) const {
    auto update = std::make_shared<document::DocumentUpdate>(
            _testDocMan.getTypeRepo(),
            *_testDocMan.getTypeRepo().getDocumentType(doc_type),
            document::DocumentId(id));
    return std::make_shared<api::UpdateCommand>(
            makeDocumentBucket(document::BucketId(0)), std::move(update), api::Timestamp(0));
}

std::shared_ptr<api::UpdateCommand>
ExternalOperationHandlerTest::makeUpdateCommand() const {
    return makeUpdateCommand("testdoctype1", "id:foo:testdoctype1::baz");
}

std::shared_ptr<api::UpdateCommand>
ExternalOperationHandlerTest::makeUpdateCommandForUser(uint64_t id) const {
    return makeUpdateCommand("testdoctype1", vespalib::make_string("id::testdoctype1:n=%" PRIu64 ":bar", id));
}

std::shared_ptr<api::PutCommand> ExternalOperationHandlerTest::makePutCommand(
        const vespalib::string& doc_type,
        const vespalib::string& id) const {
    auto doc = _testDocMan.createDocument(doc_type, id);
    return std::make_shared<api::PutCommand>(
            makeDocumentBucket(document::BucketId(0)), std::move(doc), api::Timestamp(0));
}

std::shared_ptr<api::RemoveCommand> ExternalOperationHandlerTest::makeRemoveCommand(const vespalib::string& id) const {
    return std::make_shared<api::RemoveCommand>(makeDocumentBucket(document::BucketId(0)), DocumentId(id), api::Timestamp(0));
}

TEST_F(ExternalOperationHandlerTest, mutating_operation_wdr_bounced_on_wrong_current_distribution) {
    createLinks();
    std::string state("version:1 distributor:2 storage:2");
    setup_stripe(1, 2, state);

    document::BucketId bucket(findNonOwnedUserBucketInState(state));
    auto cmd = makeUpdateCommandForUser(bucket.withoutCountBits());

    Operation::SP genOp;
    ASSERT_TRUE(getExternalOperationHandler().handleMessage(cmd, genOp));
    ASSERT_FALSE(genOp.get());
    ASSERT_EQ(1, _sender.replies().size());
    EXPECT_EQ("ReturnCode(WRONG_DISTRIBUTION, "
              "version:1 distributor:2 storage:2)",
              _sender.reply(0)->getResult().toString());
}

TEST_F(ExternalOperationHandlerTest, read_only_operation_wdr_bounced_on_wrong_current_distribution) {
    createLinks();
    std::string state("version:1 distributor:2 storage:2");
    setup_stripe(1, 2, state);

    document::BucketId bucket(findNonOwnedUserBucketInState(state));
    auto cmd = makeGetCommandForUser(bucket.withoutCountBits());

    Operation::SP genOp;
    ASSERT_TRUE(getExternalOperationHandler().handleMessage(cmd, genOp));
    ASSERT_FALSE(genOp.get());
    ASSERT_EQ(1, _sender.replies().size());
    EXPECT_EQ("ReturnCode(WRONG_DISTRIBUTION, "
              "version:1 distributor:2 storage:2)",
              _sender.reply(0)->getResult().toString());
}

TEST_F(ExternalOperationHandlerTest, mutating_operation_busy_bounced_on_wrong_pending_distribution) {
    createLinks();
    std::string current("version:10 distributor:2 storage:2");
    std::string pending("version:11 distributor:3 storage:3");
    setup_stripe(1, 3, current);

    document::BucketId b(findOwned1stNotOwned2ndInStates(current, pending));

    // Trigger pending cluster state
    simulate_set_pending_cluster_state(pending);

    auto cmd = makeUpdateCommandForUser(b.withoutCountBits());

    Operation::SP genOp;
    ASSERT_TRUE(getExternalOperationHandler().handleMessage(cmd, genOp));
    ASSERT_FALSE(genOp.get());
    ASSERT_EQ(1, _sender.replies().size());
    EXPECT_EQ("ReturnCode(BUSY, Currently pending cluster state transition from version 10 to 11)",
              _sender.reply(0)->getResult().toString());
}

void
ExternalOperationHandlerTest::verify_busy_bounced_due_to_no_active_state(std::shared_ptr<api::StorageCommand> cmd)
{
    createLinks();
    std::string state{}; // No version --> not yet received
    setup_stripe(1, 2, state);

    Operation::SP genOp;
    ASSERT_TRUE(getExternalOperationHandler().handleMessage(cmd, genOp));
    ASSERT_FALSE(genOp.get());
    ASSERT_EQ(1, _sender.replies().size());
    EXPECT_EQ("ReturnCode(BUSY, No cluster state activated yet)",
              _sender.reply(0)->getResult().toString());
}

// TODO NOT_READY is a more appropriate return code for this case, but must ensure it's
// handled gracefully and silently through the stack. BUSY is a safe bet until then.
TEST_F(ExternalOperationHandlerTest, mutating_operation_busy_bounced_if_no_cluster_state_received_yet) {
    verify_busy_bounced_due_to_no_active_state(makeUpdateCommandForUser(12345));
}

TEST_F(ExternalOperationHandlerTest, read_only_operation_busy_bounced_if_no_cluster_state_received_yet) {
    verify_busy_bounced_due_to_no_active_state(makeGetCommandForUser(12345));
}

using TimePoint = ExternalOperationHandler::TimePoint;
using namespace std::literals::chrono_literals;

void ExternalOperationHandlerTest::assert_rejection_due_to_unsafe_time(
        std::shared_ptr<api::StorageCommand> cmd)
{
    createLinks();
    setup_stripe(1, 2, "version:1 distributor:1 storage:1");
    getClock().setAbsoluteTimeInSeconds(9);
    getExternalOperationHandler().rejectFeedBeforeTimeReached(TimePoint(10s));

    Operation::SP generated;
    getExternalOperationHandler().handleMessage(cmd, generated);
    ASSERT_EQ(generated.get(), nullptr);
    ASSERT_EQ(1, _sender.replies().size());
    EXPECT_EQ("ReturnCode(STALE_TIMESTAMP, "
              "Operation received at time 9, which is before "
              "bucket ownership transfer safe time of 10)",
              _sender.reply(0)->getResult().toString());
}

TEST_F(ExternalOperationHandlerTest, reject_put_if_not_past_safe_time_point) {
    assert_rejection_due_to_unsafe_time(makePutCommand("foo", "id:foo:testdoctype1::bar"));
    EXPECT_EQ(1, safe_time_not_reached_metric_count(metrics().puts));
}

TEST_F(ExternalOperationHandlerTest, reject_remove_if_not_past_safe_time_point) {
    assert_rejection_due_to_unsafe_time(makeRemoveCommand("id:foo:testdoctype1::bar"));
    EXPECT_EQ(1, safe_time_not_reached_metric_count(metrics().removes));
}

TEST_F(ExternalOperationHandlerTest, reject_update_if_not_past_safe_time_point) {
    assert_rejection_due_to_unsafe_time(makeUpdateCommand());
    EXPECT_EQ(1, safe_time_not_reached_metric_count(metrics().updates));
}

TEST_F(ExternalOperationHandlerTest, get_not_rejected_by_unsafe_time_point) {
    createLinks();
    setup_stripe(1, 2, "version:1 distributor:1 storage:1");
    getClock().setAbsoluteTimeInSeconds(9);
    getExternalOperationHandler().rejectFeedBeforeTimeReached(TimePoint(10s));

    Operation::SP generated;
    getExternalOperationHandler().handleMessage(
            makeGetCommandForUser(0), generated);
    ASSERT_NE(generated.get(), nullptr);
    ASSERT_EQ(0, _sender.replies().size());
    EXPECT_EQ(0, safe_time_not_reached_metric_count(metrics().gets));
}

TEST_F(ExternalOperationHandlerTest, mutation_not_rejected_when_safe_point_reached) {
    createLinks();
    setup_stripe(1, 2, "version:1 distributor:1 storage:1");
    getClock().setAbsoluteTimeInSeconds(10);
    getExternalOperationHandler().rejectFeedBeforeTimeReached(TimePoint(10s));

    Operation::SP generated;
    DocumentId id("id:foo:testdoctype1::bar");
    getExternalOperationHandler().handleMessage(
            std::make_shared<api::RemoveCommand>(
                makeDocumentBucket(document::BucketId(0)), id, api::Timestamp(0)),
            generated);
    ASSERT_NE(generated.get(), nullptr);
    ASSERT_EQ(0, _sender.replies().size());
    EXPECT_EQ(0, safe_time_not_reached_metric_count(metrics().removes));
}

void ExternalOperationHandlerTest::set_up_distributor_for_sequencing_test() {
    createLinks();
    setup_stripe(1, 2, "version:1 distributor:1 storage:1");
}

void ExternalOperationHandlerTest::set_up_distributor_with_feed_blocked_state() {
    createLinks();
    setup_stripe(1, 2,
                 lib::ClusterStateBundle(lib::ClusterState("version:1 distributor:1 storage:1"),
                                         {}, {true, "full disk"}, false));
}

void ExternalOperationHandlerTest::start_operation_verify_not_rejected(
        std::shared_ptr<api::StorageCommand> cmd,
        Operation::SP& out_generated)
{
    Operation::SP generated;
    _sender.replies().clear();
    getExternalOperationHandler().handleMessage(cmd, generated);
    ASSERT_NE(generated.get(), nullptr);
    ASSERT_EQ(0, _sender.replies().size());
    out_generated = std::move(generated);
}
void ExternalOperationHandlerTest::start_operation_verify_rejected(
        std::shared_ptr<api::StorageCommand> cmd) {
    Operation::SP generated;
    _sender.replies().clear();
    getExternalOperationHandler().handleMessage(cmd, generated);
    ASSERT_EQ(generated.get(), nullptr);
    ASSERT_EQ(1, _sender.replies().size());
}

void ExternalOperationHandlerTest::assert_second_command_rejected_due_to_concurrent_mutation(
        std::shared_ptr<api::StorageCommand> cmd1,
        std::shared_ptr<api::StorageCommand> cmd2,
        const vespalib::string& expected_id_in_message) {
    set_up_distributor_for_sequencing_test();

    // Must hold ref to started operation, or sequencing handle will be released.
    Operation::SP generated1;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(std::move(cmd1), generated1));
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(std::move(cmd2)));

    // TODO reconsider BUSY return code. Need something transient and non-noisy
    EXPECT_EQ(vespalib::make_string(
                    "ReturnCode(BUSY, A mutating operation for document "
                    "'%s' is already in progress)", expected_id_in_message.c_str()),
              _sender.reply(0)->getResult().toString());
}

void ExternalOperationHandlerTest::assert_second_command_not_rejected_due_to_concurrent_mutation(
        std::shared_ptr<api::StorageCommand> cmd1,
        std::shared_ptr<api::StorageCommand> cmd2) {
    set_up_distributor_for_sequencing_test();

    Operation::SP generated1;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(std::move(cmd1), generated1));
    Operation::SP generated2;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(std::move(cmd2), generated2));
}

TEST_F(ExternalOperationHandlerTest, reject_put_with_concurrent_mutation_to_same_id) {
    ASSERT_NO_FATAL_FAILURE(assert_second_command_rejected_due_to_concurrent_mutation(
            makePutCommand("testdoctype1", _dummy_id),
            makePutCommand("testdoctype1", _dummy_id), _dummy_id));
    EXPECT_EQ(1, concurrent_mutatations_metric_count(metrics().puts));
}

TEST_F(ExternalOperationHandlerTest, do_not_reject_put_operations_to_different_ids) {
    ASSERT_NO_FATAL_FAILURE(assert_second_command_not_rejected_due_to_concurrent_mutation(
            makePutCommand("testdoctype1", "id:foo:testdoctype1::baz"),
            makePutCommand("testdoctype1", "id:foo:testdoctype1::foo")));
    EXPECT_EQ(0, concurrent_mutatations_metric_count(metrics().puts));
}

TEST_F(ExternalOperationHandlerTest, reject_remove_with_concurrent_mutation_to_same_id) {
    ASSERT_NO_FATAL_FAILURE(assert_second_command_rejected_due_to_concurrent_mutation(
            makeRemoveCommand(_dummy_id), makeRemoveCommand(_dummy_id), _dummy_id));
    EXPECT_EQ(1, concurrent_mutatations_metric_count(metrics().removes));
}

TEST_F(ExternalOperationHandlerTest, do_not_reject_remove_operations_to_different_ids) {
    ASSERT_NO_FATAL_FAILURE(assert_second_command_not_rejected_due_to_concurrent_mutation(
            makeRemoveCommand("id:foo:testdoctype1::baz"),
            makeRemoveCommand("id:foo:testdoctype1::foo")));
    EXPECT_EQ(0, concurrent_mutatations_metric_count(metrics().removes));
}

TEST_F(ExternalOperationHandlerTest, reject_update_with_concurrent_mutation_to_same_id) {
    ASSERT_NO_FATAL_FAILURE(assert_second_command_rejected_due_to_concurrent_mutation(
            makeUpdateCommand("testdoctype1", _dummy_id),
            makeUpdateCommand("testdoctype1", _dummy_id), _dummy_id));
    EXPECT_EQ(1, concurrent_mutatations_metric_count(metrics().updates));
}

TEST_F(ExternalOperationHandlerTest, do_not_reject_update_operations_to_different_ids) {
    ASSERT_NO_FATAL_FAILURE(assert_second_command_not_rejected_due_to_concurrent_mutation(
            makeUpdateCommand("testdoctype1", "id:foo:testdoctype1::baz"),
            makeUpdateCommand("testdoctype1", "id:foo:testdoctype1::foo")));
    EXPECT_EQ(0, concurrent_mutatations_metric_count(metrics().updates));
}

TEST_F(ExternalOperationHandlerTest, operation_destruction_allows_new_mutations_for_id) {
    set_up_distributor_for_sequencing_test();

    Operation::SP generated;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id), generated));

    generated.reset(); // Implicitly release sequencing handle

    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id), generated));
}

TEST_F(ExternalOperationHandlerTest, concurrent_get_and_mutation_do_not_conflict) {
    set_up_distributor_for_sequencing_test();

    Operation::SP generated1;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id), generated1));

    Operation::SP generated2;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(makeGetCommand(_dummy_id), generated2));
}

TEST_F(ExternalOperationHandlerTest, sequencing_works_across_mutation_types) {
    set_up_distributor_for_sequencing_test();

    Operation::SP generated;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(makePutCommand("testdoctype1", _dummy_id), generated));
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(makeRemoveCommand(_dummy_id)));
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(makeUpdateCommand("testdoctype1", _dummy_id)));
}

TEST_F(ExternalOperationHandlerTest, sequencing_can_be_explicitly_config_disabled) {
    set_up_distributor_for_sequencing_test();

    // Should be able to modify config after links have been created, i.e. this is a live config.
    auto cfg = make_config();
    cfg->setSequenceMutatingOperations(false);
    configure_stripe(cfg);

    Operation::SP generated1;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id), generated1));
    // Sequencing is disabled, so concurrent op is not rejected.
    Operation::SP generated2;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id), generated2));
}

TEST_F(ExternalOperationHandlerTest, gets_are_started_with_mutable_db_outside_transition_period) {
    createLinks();
    std::string current = "version:1 distributor:1 storage:3";
    setup_stripe(1, 3, current);
    auto cfg = make_config();
    cfg->setAllowStaleReadsDuringClusterStateTransitions(true);
    configure_stripe(cfg);

    document::BucketId b(16, 1234); // Only 1 distributor (us), so doesn't matter

    Operation::SP op;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(
            makeGetCommandForUser(b.withoutCountBits()), op));
    auto& get_op = dynamic_cast<GetOperation&>(*op);
    const auto* expected_space = &getBucketSpaceRepo().get(document::FixedBucketSpaces::default_space());
    EXPECT_EQ(expected_space, &get_op.bucketSpace());
}

document::BucketId ExternalOperationHandlerTest::set_up_pending_cluster_state_transition(bool read_only_enabled) {
    createLinks();
    std::string current = "version:123 distributor:2 storage:2";
    std::string pending = "version:321 distributor:3 storage:3";
    setup_stripe(1, 3, current);
    getBucketDBUpdater().set_stale_reads_enabled(read_only_enabled);
    auto cfg = make_config();
    cfg->setAllowStaleReadsDuringClusterStateTransitions(read_only_enabled);
    configure_stripe(cfg);

    // Trigger pending cluster state
    simulate_set_pending_cluster_state(pending);
    return findOwned1stNotOwned2ndInStates(current, pending);
}

TEST_F(ExternalOperationHandlerTest, gets_are_started_with_read_only_db_during_transition_period) {
    auto non_owned_bucket = set_up_pending_cluster_state_transition(true);

    Operation::SP op;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(
            makeGetCommandForUser(non_owned_bucket.withoutCountBits()), op));
    auto& get_op = dynamic_cast<GetOperation&>(*op);
    const auto* expected_space = &getReadOnlyBucketSpaceRepo().get(document::FixedBucketSpaces::default_space());
    EXPECT_EQ(expected_space, &get_op.bucketSpace());
}

TEST_F(ExternalOperationHandlerTest, gets_are_busy_bounced_during_transition_period_if_stale_reads_disabled) {
    auto non_owned_bucket = set_up_pending_cluster_state_transition(false);

    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(
            makeGetCommandForUser(non_owned_bucket.withoutCountBits())));
    EXPECT_EQ("ReturnCode(BUSY, Currently pending cluster state transition from version 123 to 321)",
              _sender.reply(0)->getResult().toString());
}

void ExternalOperationHandlerTest::do_test_get_weak_consistency_is_propagated(bool use_weak) {
    createLinks();
    setup_stripe(1, 2, "version:1 distributor:1 storage:1");
    // Explicitly only touch config in the case weak consistency is enabled to ensure the
    // default is strong.
    if (use_weak) {
        getExternalOperationHandler().set_use_weak_internal_read_consistency_for_gets(true);
    }
    document::BucketId b(16, 1234);
    Operation::SP op;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(
            makeGetCommandForUser(b.withoutCountBits()), op));
    auto& get_op = dynamic_cast<GetOperation&>(*op);
    EXPECT_EQ(get_op.desired_read_consistency(),
              (use_weak ? api::InternalReadConsistency::Weak
                        : api::InternalReadConsistency::Strong));
}

TEST_F(ExternalOperationHandlerTest, gets_are_sent_with_strong_consistency_by_default) {
    do_test_get_weak_consistency_is_propagated(false);
}

TEST_F(ExternalOperationHandlerTest, gets_are_sent_with_weak_consistency_if_config_enabled) {
    do_test_get_weak_consistency_is_propagated(true);
}

TEST_F(ExternalOperationHandlerTest, puts_are_rejected_if_feed_is_blocked) {
    set_up_distributor_with_feed_blocked_state();

    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(
            makePutCommand("testdoctype1", "id:foo:testdoctype1::foo")));
    EXPECT_EQ("ReturnCode(NO_SPACE, External feed is blocked due to resource exhaustion: full disk)",
              _sender.reply(0)->getResult().toString());
}

TEST_F(ExternalOperationHandlerTest, non_trivial_updates_are_rejected_if_feed_is_blocked) {
    set_up_distributor_with_feed_blocked_state();

    auto cmd = makeUpdateCommand("testdoctype1", "id:foo:testdoctype1::foo");
    const auto* doc_type = _testDocMan.getTypeRepo().getDocumentType("testdoctype1");
    cmd->getUpdate()->addUpdate(FieldUpdate(doc_type->getField("title")).addUpdate(std::make_unique<AssignValueUpdate>(StringFieldValue::make("new value"))));

    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(std::move(cmd)));
    EXPECT_EQ("ReturnCode(NO_SPACE, External feed is blocked due to resource exhaustion: full disk)",
              _sender.reply(0)->getResult().toString());
}

TEST_F(ExternalOperationHandlerTest, trivial_updates_are_not_rejected_if_feed_is_blocked) {
    set_up_distributor_with_feed_blocked_state();

    Operation::SP generated;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(
            makeUpdateCommand("testdoctype1", "id:foo:testdoctype1::foo"), generated));
}


struct OperationHandlerSequencingTest : ExternalOperationHandlerTest {
    void SetUp() override {
        set_up_distributor_for_sequencing_test();
    }

    static documentapi::TestAndSetCondition bucket_lock_bypass_tas_condition(const vespalib::string& token) {
        return documentapi::TestAndSetCondition(
                vespalib::make_string("%s=%s", reindexing_bucket_lock_bypass_prefix(), token.c_str()));
    }
};

TEST_F(OperationHandlerSequencingTest, put_not_allowed_through_locked_bucket_if_special_tas_token_not_present) {
    auto put = makePutCommand("testdoctype1", "id:foo:testdoctype1:n=1:bar");
    auto bucket = makeDocumentBucket(document::BucketId(16, 1));
    auto bucket_handle = getExternalOperationHandler().operation_sequencer().try_acquire(bucket, "foo");
    ASSERT_TRUE(bucket_handle.valid());
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(put));
}

TEST_F(OperationHandlerSequencingTest, put_allowed_through_locked_bucket_if_special_tas_token_present) {
    set_up_distributor_for_sequencing_test();

    auto put = makePutCommand("testdoctype1", "id:foo:testdoctype1:n=1:bar");
    put->setCondition(bucket_lock_bypass_tas_condition("foo"));

    auto bucket = makeDocumentBucket(document::BucketId(16, 1));
    auto bucket_handle = getExternalOperationHandler().operation_sequencer().try_acquire(bucket, "foo");
    ASSERT_TRUE(bucket_handle.valid());

    Operation::SP op;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(put, op));
}

TEST_F(OperationHandlerSequencingTest, put_not_allowed_through_locked_bucket_if_tas_token_mismatches_current_lock_tkoen) {
    auto put = makePutCommand("testdoctype1", "id:foo:testdoctype1:n=1:bar");
    put->setCondition(bucket_lock_bypass_tas_condition("bar"));
    auto bucket = makeDocumentBucket(document::BucketId(16, 1));
    auto bucket_handle = getExternalOperationHandler().operation_sequencer().try_acquire(bucket, "foo");
    ASSERT_TRUE(bucket_handle.valid());
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(put));
}

TEST_F(OperationHandlerSequencingTest, put_with_bucket_lock_tas_token_is_rejected_if_no_bucket_lock_present) {
    auto put = makePutCommand("testdoctype1", "id:foo:testdoctype1:n=1:bar");
    put->setCondition(bucket_lock_bypass_tas_condition("foo"));
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(put));
    EXPECT_EQ("ReturnCode(TEST_AND_SET_CONDITION_FAILED, Operation expects a read-for-write bucket "
              "lock to be present, but none currently exists)",
              _sender.reply(0)->getResult().toString());
}

// This test is a variation of the above, but whereas it tests the case where _no_ lock is
// present, this tests the case where a lock is present but it's not a bucket-level lock.
TEST_F(OperationHandlerSequencingTest, put_with_bucket_lock_tas_token_is_rejected_if_document_lock_present) {
    auto put = makePutCommand("testdoctype1", _dummy_id);
    put->setCondition(bucket_lock_bypass_tas_condition("foo"));
    Operation::SP op;
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_not_rejected(makeUpdateCommand("testdoctype1", _dummy_id), op));
    ASSERT_NO_FATAL_FAILURE(start_operation_verify_rejected(std::move(put)));
    EXPECT_EQ("ReturnCode(TEST_AND_SET_CONDITION_FAILED, Operation expects a read-for-write bucket "
              "lock to be present, but none currently exists)",
              _sender.reply(0)->getResult().toString());
}

TEST_F(OperationHandlerSequencingTest, reindexing_visitor_creates_read_for_write_operation) {
    auto cmd = std::make_shared<api::CreateVisitorCommand>(
            document::FixedBucketSpaces::default_space(), "reindexingvisitor", "foo", "");
    Operation::SP op;
    getExternalOperationHandler().handleMessage(cmd, op);
    ASSERT_TRUE(op.get() != nullptr);
    ASSERT_TRUE(dynamic_cast<ReadForWriteVisitorOperationStarter*>(op.get()) != nullptr);
}

TEST_F(OperationHandlerSequencingTest, reindexing_visitor_library_check_is_case_insensitive) {
    auto cmd = std::make_shared<api::CreateVisitorCommand>(
            document::FixedBucketSpaces::default_space(), "ReIndexingVisitor", "foo", "");
    Operation::SP op;
    getExternalOperationHandler().handleMessage(cmd, op);
    ASSERT_TRUE(op.get() != nullptr);
    ASSERT_TRUE(dynamic_cast<ReadForWriteVisitorOperationStarter*>(op.get()) != nullptr);
}

// TODO support sequencing of RemoveLocation? It's a mutating operation, but supporting it with
// the current approach is not trivial. A RemoveLocation operation covers the _entire_ bucket
// sub tree under a given location, while the sequencer works on individual GIDs. Mapping the
// former to the latter is not trivial unless we introduce higher level "location" mutation
// pseudo-locks in the sequencer. I.e. if we get a RemoveLocation with id.user==123456, this
// prevents any handles from being acquired to any GID under location BucketId(32, 123456).

}
