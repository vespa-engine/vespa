// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/distributor/distributortestutil.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/test/make_document_bucket.h>

using document::test::makeDocumentBucket;

namespace storage::distributor {

class ExternalOperationHandlerTest : public CppUnit::TestFixture,
                                     public DistributorTestUtil
{
    document::TestDocMan _testDocMan;

    CPPUNIT_TEST_SUITE(ExternalOperationHandlerTest);
    CPPUNIT_TEST(testBucketSplitMask);
    CPPUNIT_TEST(testOperationRejectedOnWrongDistribution);
    CPPUNIT_TEST(testOperationRejectedOnPendingWrongDistribution);
    CPPUNIT_TEST(reject_put_if_not_past_safe_time_point);
    CPPUNIT_TEST(reject_remove_if_not_past_safe_time_point);
    CPPUNIT_TEST(reject_update_if_not_past_safe_time_point);
    CPPUNIT_TEST(get_not_rejected_by_unsafe_time_point);
    CPPUNIT_TEST(mutation_not_rejected_when_safe_point_reached);
    CPPUNIT_TEST(reject_put_with_concurrent_mutation_to_same_id);
    CPPUNIT_TEST(do_not_reject_put_operations_to_different_ids);
    CPPUNIT_TEST(reject_remove_with_concurrent_mutation_to_same_id);
    CPPUNIT_TEST(do_not_reject_remove_operations_to_different_ids);
    CPPUNIT_TEST(reject_update_with_concurrent_mutation_to_same_id);
    CPPUNIT_TEST(do_not_reject_update_operations_to_different_ids);
    CPPUNIT_TEST(operation_destruction_allows_new_mutations_for_id);
    CPPUNIT_TEST(concurrent_get_and_mutation_do_not_conflict);
    CPPUNIT_TEST(sequencing_works_across_mutation_types);
    CPPUNIT_TEST(sequencing_can_be_explicitly_config_disabled);
    CPPUNIT_TEST_SUITE_END();

    document::BucketId findNonOwnedUserBucketInState(vespalib::stringref state);
    document::BucketId findOwned1stNotOwned2ndInStates(
            vespalib::stringref state1,
            vespalib::stringref state2);

    std::shared_ptr<api::GetCommand> makeGetCommandForUser(uint64_t id) const;
    std::shared_ptr<api::GetCommand> makeGetCommand(const vespalib::string& id) const;
    std::shared_ptr<api::UpdateCommand> makeUpdateCommand(const vespalib::string& doc_type,
                                                          const vespalib::string& id) const;
    std::shared_ptr<api::UpdateCommand> makeUpdateCommand() const;
    std::shared_ptr<api::PutCommand> makePutCommand(const vespalib::string& doc_type,
                                                    const vespalib::string& id) const;
    std::shared_ptr<api::RemoveCommand> makeRemoveCommand(const vespalib::string& id) const;

    Operation::SP start_operation_verify_not_rejected(std::shared_ptr<api::StorageCommand> cmd);
    void start_operation_verify_rejected(std::shared_ptr<api::StorageCommand> cmd);

    int64_t safe_time_not_reached_metric_count(
            const metrics::LoadMetric<PersistenceOperationMetricSet>& metrics) const {
        return metrics[documentapi::LoadType::DEFAULT].failures
                .safe_time_not_reached.getLongValue("count");
    }

    int64_t concurrent_mutatations_metric_count(
            const metrics::LoadMetric<PersistenceOperationMetricSet>& metrics) const {
        return metrics[documentapi::LoadType::DEFAULT].failures
                .concurrent_mutations.getLongValue("count");
    }

    void set_up_distributor_for_sequencing_test();

    const vespalib::string _dummy_id{"id:foo:testdoctype1::bar"};

protected:
    void testBucketSplitMask();
    void testOperationRejectedOnWrongDistribution();
    void testOperationRejectedOnPendingWrongDistribution();
    void reject_put_if_not_past_safe_time_point();
    void reject_remove_if_not_past_safe_time_point();
    void reject_update_if_not_past_safe_time_point();
    void get_not_rejected_by_unsafe_time_point();
    void mutation_not_rejected_when_safe_point_reached();
    void reject_put_with_concurrent_mutation_to_same_id();
    void do_not_reject_put_operations_to_different_ids();
    void reject_remove_with_concurrent_mutation_to_same_id();
    void do_not_reject_remove_operations_to_different_ids();
    void reject_update_with_concurrent_mutation_to_same_id();
    void do_not_reject_update_operations_to_different_ids();
    void operation_destruction_allows_new_mutations_for_id();
    void concurrent_get_and_mutation_do_not_conflict();
    void sequencing_works_across_mutation_types();
    void sequencing_can_be_explicitly_config_disabled();

    void assert_rejection_due_to_unsafe_time(
            std::shared_ptr<api::StorageCommand> cmd);

    void assert_second_command_rejected_due_to_concurrent_mutation(
            std::shared_ptr<api::StorageCommand> cmd1,
            std::shared_ptr<api::StorageCommand> cmd2,
            const vespalib::string& expected_id_in_message);
    void assert_second_command_not_rejected_due_to_concurrent_mutation(
            std::shared_ptr<api::StorageCommand> cmd1,
            std::shared_ptr<api::StorageCommand> cmd2);

public:
    void tearDown() override {
        close();
    }

};

CPPUNIT_TEST_SUITE_REGISTRATION(ExternalOperationHandlerTest);

using document::DocumentId;

void
ExternalOperationHandlerTest::testBucketSplitMask()
{
    {
        createLinks();
        getDirConfig().getConfig("stor-distributormanager").set("minsplitcount", "16");

        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0xffff),
                getExternalOperationHandler().getBucketId(document::DocumentId(
                    vespalib::make_string("userdoc:ns:%d::", 0xffff))
                ).stripUnused());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0),
                getExternalOperationHandler().getBucketId(document::DocumentId(
                    vespalib::make_string("userdoc:ns:%d::", 0x10000))
                ).stripUnused());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0xffff),
                getExternalOperationHandler().getBucketId(document::DocumentId(
                    vespalib::make_string("userdoc:ns:%d::", 0xffff))
                ).stripUnused());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 0x100),
                getExternalOperationHandler().getBucketId(document::DocumentId(
                    vespalib::make_string("userdoc:ns:%d::", 0x100))
                ).stripUnused());
        close();
    }
    {
        getDirConfig().getConfig("stor-distributormanager").set("minsplitcount", "20");
        createLinks();
        CPPUNIT_ASSERT_EQUAL(document::BucketId(20, 0x11111),
                getExternalOperationHandler().getBucketId(document::DocumentId(
                    vespalib::make_string("userdoc:ns:%d::", 0x111111))
                ).stripUnused());
        CPPUNIT_ASSERT_EQUAL(document::BucketId(20, 0x22222),
                getExternalOperationHandler().getBucketId(document::DocumentId(
                    vespalib::make_string("userdoc:ns:%d::", 0x222222))
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
        if (!getExternalOperationHandler().ownsBucketInState(state, makeDocumentBucket(bucket))) {
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
        if (getExternalOperationHandler().ownsBucketInState(state1, makeDocumentBucket(bucket))
            && !getExternalOperationHandler().ownsBucketInState(state2, makeDocumentBucket(bucket)))
        {
            return bucket;
        }
    }
    throw std::runtime_error("no appropriate bucket found");
}

std::shared_ptr<api::GetCommand>
ExternalOperationHandlerTest::makeGetCommand(const vespalib::string& id) const {
    return std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId(0)), DocumentId(id), "[all]");
}

std::shared_ptr<api::GetCommand>
ExternalOperationHandlerTest::makeGetCommandForUser(uint64_t id) const {
    DocumentId docId(document::UserDocIdString(vespalib::make_string("userdoc:foo:%lu:bar", id)));
    return std::make_shared<api::GetCommand>(makeDocumentBucket(document::BucketId(0)), docId, "[all]");
}

std::shared_ptr<api::UpdateCommand> ExternalOperationHandlerTest::makeUpdateCommand(
        const vespalib::string& doc_type,
        const vespalib::string& id) const {
    auto update = std::make_shared<document::DocumentUpdate>(
            *_testDocMan.getTypeRepo().getDocumentType(doc_type),
            document::DocumentId(id));
    return std::make_shared<api::UpdateCommand>(
            makeDocumentBucket(document::BucketId(0)), std::move(update), api::Timestamp(0));
}

std::shared_ptr<api::UpdateCommand>
ExternalOperationHandlerTest::makeUpdateCommand() const {
    return makeUpdateCommand("testdoctype1", "id:foo:testdoctype1::baz");
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

void
ExternalOperationHandlerTest::testOperationRejectedOnWrongDistribution()
{
    createLinks();
    std::string state("distributor:2 storage:2");
    setupDistributor(1, 2, state);

    document::BucketId bucket(findNonOwnedUserBucketInState(state));
    auto cmd = makeGetCommandForUser(bucket.withoutCountBits());

    Operation::SP genOp;
    CPPUNIT_ASSERT(getExternalOperationHandler().handleMessage(cmd, genOp));
    CPPUNIT_ASSERT(!genOp.get());
    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.replies.size());
    CPPUNIT_ASSERT_EQUAL(
            std::string("ReturnCode(WRONG_DISTRIBUTION, "
                        "distributor:2 storage:2)"),
            _sender.replies[0]->getResult().toString());
}

void
ExternalOperationHandlerTest::testOperationRejectedOnPendingWrongDistribution()
{
    createLinks();
    std::string current("distributor:2 storage:2");
    std::string pending("distributor:3 storage:3");
    setupDistributor(1, 3, current);

    document::BucketId b(findOwned1stNotOwned2ndInStates(current, pending));

    // Trigger pending cluster state
    auto stateCmd = std::make_shared<api::SetSystemStateCommand>(
            lib::ClusterState(pending));
    getBucketDBUpdater().onSetSystemState(stateCmd);

    auto cmd = makeGetCommandForUser(b.withoutCountBits());

    Operation::SP genOp;
    CPPUNIT_ASSERT(getExternalOperationHandler().handleMessage(cmd, genOp));
    CPPUNIT_ASSERT(!genOp.get());
    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.replies.size());
    // Fail back with _pending_ cluster state so client can start trying
    // correct distributor immediately. If that distributor has not yet
    // completed processing its pending cluster state, it'll return the
    // old (current) cluster state, causing the client to bounce between
    // the two until the pending states have been resolved. This is pretty
    // much inevitable with the current design.
    CPPUNIT_ASSERT_EQUAL(
            std::string("ReturnCode(WRONG_DISTRIBUTION, "
                        "distributor:3 storage:3)"),
            _sender.replies[0]->getResult().toString());
}

using TimePoint = ExternalOperationHandler::TimePoint;
using namespace std::literals::chrono_literals;

void ExternalOperationHandlerTest::assert_rejection_due_to_unsafe_time(
        std::shared_ptr<api::StorageCommand> cmd)
{
    createLinks();
    setupDistributor(1, 2, "distributor:1 storage:1");
    getClock().setAbsoluteTimeInSeconds(9);
    getExternalOperationHandler().rejectFeedBeforeTimeReached(TimePoint(10s));

    Operation::SP generated;
    getExternalOperationHandler().handleMessage(cmd, generated);
    CPPUNIT_ASSERT(generated.get() == nullptr);
    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.replies.size());
    CPPUNIT_ASSERT_EQUAL(
            std::string("ReturnCode(STALE_TIMESTAMP, "
                        "Operation received at time 9, which is before "
                        "bucket ownership transfer safe time of 10)"),
            _sender.replies[0]->getResult().toString());
}

void ExternalOperationHandlerTest::reject_put_if_not_past_safe_time_point() {
    assert_rejection_due_to_unsafe_time(makePutCommand("foo", "id:foo:testdoctype1::bar"));
    CPPUNIT_ASSERT_EQUAL(int64_t(1), safe_time_not_reached_metric_count(
            getDistributor().getMetrics().puts));
}

void ExternalOperationHandlerTest::reject_remove_if_not_past_safe_time_point() {
    assert_rejection_due_to_unsafe_time(makeRemoveCommand("id:foo:testdoctype1::bar"));
    CPPUNIT_ASSERT_EQUAL(int64_t(1), safe_time_not_reached_metric_count(
            getDistributor().getMetrics().removes));
}

void ExternalOperationHandlerTest::reject_update_if_not_past_safe_time_point() {
    assert_rejection_due_to_unsafe_time(makeUpdateCommand());
    CPPUNIT_ASSERT_EQUAL(int64_t(1), safe_time_not_reached_metric_count(
            getDistributor().getMetrics().updates));
}

void ExternalOperationHandlerTest::get_not_rejected_by_unsafe_time_point() {
    createLinks();
    setupDistributor(1, 2, "distributor:1 storage:1");
    getClock().setAbsoluteTimeInSeconds(9);
    getExternalOperationHandler().rejectFeedBeforeTimeReached(TimePoint(10s));

    Operation::SP generated;
    getExternalOperationHandler().handleMessage(
            makeGetCommandForUser(0), generated);
    CPPUNIT_ASSERT(generated.get() != nullptr);
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.replies.size());
    CPPUNIT_ASSERT_EQUAL(int64_t(0), safe_time_not_reached_metric_count(
            getDistributor().getMetrics().gets));
}

void ExternalOperationHandlerTest::mutation_not_rejected_when_safe_point_reached() {
    createLinks();
    setupDistributor(1, 2, "distributor:1 storage:1");
    getClock().setAbsoluteTimeInSeconds(10);
    getExternalOperationHandler().rejectFeedBeforeTimeReached(TimePoint(10s));

    Operation::SP generated;
    DocumentId id("id:foo:testdoctype1::bar");
    getExternalOperationHandler().handleMessage(
            std::make_shared<api::RemoveCommand>(
                makeDocumentBucket(document::BucketId(0)), id, api::Timestamp(0)),
            generated);
    CPPUNIT_ASSERT(generated.get() != nullptr);
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.replies.size());
    CPPUNIT_ASSERT_EQUAL(int64_t(0), safe_time_not_reached_metric_count(
            getDistributor().getMetrics().removes));
}

void ExternalOperationHandlerTest::set_up_distributor_for_sequencing_test() {
    createLinks();
    setupDistributor(1, 2, "distributor:1 storage:1");
}

Operation::SP ExternalOperationHandlerTest::start_operation_verify_not_rejected(
        std::shared_ptr<api::StorageCommand> cmd) {
    Operation::SP generated;
    _sender.replies.clear();
    getExternalOperationHandler().handleMessage(cmd, generated);
    CPPUNIT_ASSERT(generated.get() != nullptr);
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.replies.size());
    return generated;
}
void ExternalOperationHandlerTest::start_operation_verify_rejected(
        std::shared_ptr<api::StorageCommand> cmd) {
    Operation::SP generated;
    _sender.replies.clear();
    getExternalOperationHandler().handleMessage(cmd, generated);
    CPPUNIT_ASSERT(generated.get() == nullptr);
    CPPUNIT_ASSERT_EQUAL(size_t(1), _sender.replies.size());
}

void ExternalOperationHandlerTest::assert_second_command_rejected_due_to_concurrent_mutation(
        std::shared_ptr<api::StorageCommand> cmd1,
        std::shared_ptr<api::StorageCommand> cmd2,
        const vespalib::string& expected_id_in_message) {
    set_up_distributor_for_sequencing_test();

    // Must hold ref to started operation, or sequencing handle will be released.
    Operation::SP generated1 = start_operation_verify_not_rejected(std::move(cmd1));
    start_operation_verify_rejected(std::move(cmd2));

    // TODO reconsider BUSY return code. Need something transient and non-noisy
    CPPUNIT_ASSERT_EQUAL(
            std::string(vespalib::make_string(
                    "ReturnCode(BUSY, A mutating operation for document "
                    "'%s' is already in progress)", expected_id_in_message.c_str())),
            _sender.replies[0]->getResult().toString());
}

void ExternalOperationHandlerTest::assert_second_command_not_rejected_due_to_concurrent_mutation(
        std::shared_ptr<api::StorageCommand> cmd1,
        std::shared_ptr<api::StorageCommand> cmd2) {
    set_up_distributor_for_sequencing_test();

    Operation::SP generated1 = start_operation_verify_not_rejected(std::move(cmd1));
    start_operation_verify_not_rejected(std::move(cmd2));
}

void ExternalOperationHandlerTest::reject_put_with_concurrent_mutation_to_same_id() {
    assert_second_command_rejected_due_to_concurrent_mutation(
            makePutCommand("testdoctype1", _dummy_id),
            makePutCommand("testdoctype1", _dummy_id), _dummy_id);
    CPPUNIT_ASSERT_EQUAL(int64_t(1), concurrent_mutatations_metric_count(getDistributor().getMetrics().puts));
}

void ExternalOperationHandlerTest::do_not_reject_put_operations_to_different_ids() {
    assert_second_command_not_rejected_due_to_concurrent_mutation(
            makePutCommand("testdoctype1", "id:foo:testdoctype1::baz"),
            makePutCommand("testdoctype1", "id:foo:testdoctype1::foo"));
    CPPUNIT_ASSERT_EQUAL(int64_t(0), concurrent_mutatations_metric_count(getDistributor().getMetrics().puts));
}

void ExternalOperationHandlerTest::reject_remove_with_concurrent_mutation_to_same_id() {
    assert_second_command_rejected_due_to_concurrent_mutation(
            makeRemoveCommand(_dummy_id), makeRemoveCommand(_dummy_id), _dummy_id);
    CPPUNIT_ASSERT_EQUAL(int64_t(1), concurrent_mutatations_metric_count(getDistributor().getMetrics().removes));
}

void ExternalOperationHandlerTest::do_not_reject_remove_operations_to_different_ids() {
    assert_second_command_not_rejected_due_to_concurrent_mutation(
            makeRemoveCommand("id:foo:testdoctype1::baz"),
            makeRemoveCommand("id:foo:testdoctype1::foo"));
    CPPUNIT_ASSERT_EQUAL(int64_t(0), concurrent_mutatations_metric_count(getDistributor().getMetrics().removes));
}

void ExternalOperationHandlerTest::reject_update_with_concurrent_mutation_to_same_id() {
    assert_second_command_rejected_due_to_concurrent_mutation(
            makeUpdateCommand("testdoctype1", _dummy_id),
            makeUpdateCommand("testdoctype1", _dummy_id), _dummy_id);
    CPPUNIT_ASSERT_EQUAL(int64_t(1), concurrent_mutatations_metric_count(getDistributor().getMetrics().updates));
}

void ExternalOperationHandlerTest::do_not_reject_update_operations_to_different_ids() {
    assert_second_command_not_rejected_due_to_concurrent_mutation(
            makeUpdateCommand("testdoctype1", "id:foo:testdoctype1::baz"),
            makeUpdateCommand("testdoctype1", "id:foo:testdoctype1::foo"));
    CPPUNIT_ASSERT_EQUAL(int64_t(0), concurrent_mutatations_metric_count(getDistributor().getMetrics().updates));
}

void ExternalOperationHandlerTest::operation_destruction_allows_new_mutations_for_id() {
    set_up_distributor_for_sequencing_test();

    Operation::SP generated = start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id));

    generated.reset(); // Implicitly release sequencing handle

    start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id));
}

void ExternalOperationHandlerTest::concurrent_get_and_mutation_do_not_conflict() {
    set_up_distributor_for_sequencing_test();

    Operation::SP generated1 = start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id));

    start_operation_verify_not_rejected(makeGetCommand(_dummy_id));
}

void ExternalOperationHandlerTest::sequencing_works_across_mutation_types() {
    set_up_distributor_for_sequencing_test();

    Operation::SP generated = start_operation_verify_not_rejected(makePutCommand("testdoctype1", _dummy_id));
    start_operation_verify_rejected(makeRemoveCommand(_dummy_id));
    start_operation_verify_rejected(makeUpdateCommand("testdoctype1", _dummy_id));
}

void ExternalOperationHandlerTest::sequencing_can_be_explicitly_config_disabled() {
    set_up_distributor_for_sequencing_test();

    // Should be able to modify config after links have been created, i.e. this is a live config.
    getConfig().setSequenceMutatingOperations(false);

    Operation::SP generated = start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id));
    // Sequencing is disabled, so concurrent op is not rejected.
    start_operation_verify_not_rejected(makeRemoveCommand(_dummy_id));
}

// TODO support sequencing of RemoveLocation? It's a mutating operation, but supporting it with
// the current approach is not trivial. A RemoveLocation operation covers the _entire_ bucket
// sub tree under a given location, while the sequencer works on individual GIDs. Mapping the
// former to the latter is not trivial unless we introduce higher level "location" mutation
// pseudo-locks in the sequencer. I.e. if we get a RemoveLocation with id.user==123456, this
// prevents any handles from being acquired to any GID under location BucketId(32, 123456).

}
