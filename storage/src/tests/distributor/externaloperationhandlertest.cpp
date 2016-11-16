// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>

#include <tests/distributor/distributortestutil.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/document/base/testdocman.h>

namespace storage {
namespace distributor {

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
    CPPUNIT_TEST_SUITE_END();

    document::BucketId findNonOwnedUserBucketInState(vespalib::stringref state);
    document::BucketId findOwned1stNotOwned2ndInStates(
            vespalib::stringref state1,
            vespalib::stringref state2);

    std::shared_ptr<api::GetCommand> makeGetCommandForUser(uint64_t id);
    std::shared_ptr<api::UpdateCommand> makeUpdateCommand();

    int64_t safe_time_not_reached_metric_count(
            const metrics::LoadMetric<PersistenceOperationMetricSet>& metrics) const {
        return metrics[documentapi::LoadType::DEFAULT].failures
                .safe_time_not_reached.getLongValue("count");
    }
protected:
    void testBucketSplitMask();
    void testOperationRejectedOnWrongDistribution();
    void testOperationRejectedOnPendingWrongDistribution();
    void reject_put_if_not_past_safe_time_point();
    void reject_remove_if_not_past_safe_time_point();
    void reject_update_if_not_past_safe_time_point();
    void get_not_rejected_by_unsafe_time_point();
    void mutation_not_rejected_when_safe_point_reached();

    void assert_rejection_due_to_unsafe_time(
            std::shared_ptr<api::StorageCommand> cmd);

public:
    void tearDown() override {
        close();
    }

};

CPPUNIT_TEST_SUITE_REGISTRATION(ExternalOperationHandlerTest);

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
        if (!getExternalOperationHandler().ownsBucketInState(state, bucket)) {
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
        if (getExternalOperationHandler().ownsBucketInState(state1, bucket)
            && !getExternalOperationHandler().ownsBucketInState(state2, bucket))
        {
            return bucket;
        }
    }
    throw std::runtime_error("no appropriate bucket found");
}

std::shared_ptr<api::GetCommand>
ExternalOperationHandlerTest::makeGetCommandForUser(uint64_t id)
{
    document::DocumentId docId(document::UserDocIdString("userdoc:foo:" + vespalib::make_string("%lu", id) + ":bar"));
    return std::make_shared<api::GetCommand>(
            document::BucketId(0), docId, "[all]");
}

std::shared_ptr<api::UpdateCommand>
ExternalOperationHandlerTest::makeUpdateCommand()
{
    auto update = std::make_shared<document::DocumentUpdate>(
            *_testDocMan.getTypeRepo().getDocumentType("testdoctype1"),
            document::DocumentId("id:foo:testdoctype1::baz"));
    return std::make_shared<api::UpdateCommand>(
            document::BucketId(0), std::move(update), api::Timestamp(0));
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
    auto doc = _testDocMan.createDocument("foo", "id:foo:testdoctype1::bar");
    auto cmd = std::make_shared<api::PutCommand>(
            document::BucketId(0), std::move(doc), api::Timestamp(0));
    assert_rejection_due_to_unsafe_time(cmd);
    CPPUNIT_ASSERT_EQUAL(int64_t(1), safe_time_not_reached_metric_count(
            getDistributor().getMetrics().puts));
}

void ExternalOperationHandlerTest::reject_remove_if_not_past_safe_time_point() {
    document::DocumentId id("id:foo:testdoctype1::bar");
    assert_rejection_due_to_unsafe_time(std::make_shared<api::RemoveCommand>(
            document::BucketId(0), id, api::Timestamp(0)));
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
    document::DocumentId id("id:foo:testdoctype1::bar");
    getExternalOperationHandler().handleMessage(
            std::make_shared<api::RemoveCommand>(
                document::BucketId(0), id, api::Timestamp(0)),
            generated);
    CPPUNIT_ASSERT(generated.get() != nullptr);
    CPPUNIT_ASSERT_EQUAL(size_t(0), _sender.replies.size());
    CPPUNIT_ASSERT_EQUAL(int64_t(0), safe_time_not_reached_metric_count(
            getDistributor().getMetrics().removes));
}

} // distributor
} // storage
