// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#include <vespa/fastos/fastos.h>

#include <tests/distributor/distributortestutil.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/vdstestlib/cppunit/macros.h>

namespace storage {
namespace distributor {

class ExternalOperationHandlerTest : public CppUnit::TestFixture,
                                     public DistributorTestUtil
{
    CPPUNIT_TEST_SUITE(ExternalOperationHandlerTest);
    CPPUNIT_TEST(testBucketSplitMask);
    CPPUNIT_TEST(testOperationRejectedOnWrongDistribution);
    CPPUNIT_TEST(testOperationRejectedOnPendingWrongDistribution);
    CPPUNIT_TEST_SUITE_END();

    document::BucketId findNonOwnedUserBucketInState(vespalib::stringref state);
    document::BucketId findOwned1stNotOwned2ndInStates(
            vespalib::stringref state1,
            vespalib::stringref state2);

    std::shared_ptr<api::StorageMessage> makeGetCommandForUser(uint64_t id);

protected:
    void testBucketSplitMask();
    void testOperationRejectedOnWrongDistribution();
    void testOperationRejectedOnPendingWrongDistribution();

public:
    void tearDown() {
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

std::shared_ptr<api::StorageMessage>
ExternalOperationHandlerTest::makeGetCommandForUser(uint64_t id)
{
    document::DocumentId docId(document::UserDocIdString("userdoc:foo:" + vespalib::make_string("%lu", id) + ":bar"));
    std::shared_ptr<api::StorageMessage> cmd(
            new api::GetCommand(document::BucketId(0), docId, "[all]"));
    return cmd;
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
} // distributor
} // storage
