// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/dummystoragelink.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/bucket.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/distributor/operations/external/updateoperation.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/config/helper/configgetter.hpp>
#include <vespa/vespalib/gtest/gtest.h>

using namespace document;
using namespace storage::api;
using namespace std;
using namespace storage::lib;
using namespace ::testing;
using config::ConfigGetter;
using config::FileSpec;
using vespalib::string;
using document::test::makeDocumentBucket;

namespace storage::distributor {

struct UpdateOperationTest : Test, DistributorTestUtil {
    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType* _html_type;

    void SetUp() override {
        _repo.reset(
                new DocumentTypeRepo(*ConfigGetter<DocumenttypesConfig>::
                                     getConfig("config-doctypes", FileSpec("../config-doctypes.cfg"))));
        _html_type = _repo->getDocumentType("text/html");
        createLinks();
    }

    void TearDown() override {
        close();
    }

    void replyToMessage(UpdateOperation& callback, DistributorMessageSenderStub& sender, uint32_t index,
                        uint64_t oldTimestamp, const api::BucketInfo& info = api::BucketInfo(2,4,6),
                        const api::ReturnCode& result = api::ReturnCode());

    std::shared_ptr<UpdateOperation>
    sendUpdate(const std::string& bucketState, bool create_if_missing = false);

    document::BucketId _bId;
};

std::shared_ptr<UpdateOperation>
UpdateOperationTest::sendUpdate(const std::string& bucketState, bool create_if_missing)
{
    auto update = std::make_shared<document::DocumentUpdate>(
            *_repo, *_html_type,
            document::DocumentId("id:ns:" + _html_type->getName() + "::1"));
    update->setCreateIfNonExistent(create_if_missing);

    _bId = distributor_component().getBucketId(update->getId());

    addNodesToBucketDB(_bId, bucketState);

    auto msg = std::make_shared<api::UpdateCommand>(makeDocumentBucket(document::BucketId(0)), update, 100);

    auto& comp = distributor_component();
    return std::make_shared<UpdateOperation>(
            comp, comp, getDistributorBucketSpace(), msg, std::vector<BucketDatabase::Entry>(),
            getDistributor().getMetrics().updates);
}

void
UpdateOperationTest::replyToMessage(UpdateOperation& callback, DistributorMessageSenderStub& sender, uint32_t index,
                                     uint64_t oldTimestamp, const api::BucketInfo& info, const api::ReturnCode& result)
{
    std::shared_ptr<api::StorageMessage> msg2  = sender.command(index);
    auto* updatec = dynamic_cast<UpdateCommand*>(msg2.get());
    std::unique_ptr<api::StorageReply> reply(updatec->makeReply());
    auto* updateR = static_cast<api::UpdateReply*>(reply.get());
    updateR->setOldTimestamp(oldTimestamp);
    updateR->setBucketInfo(info);
    updateR->setResult(result);

    callback.onReceive(sender, std::shared_ptr<StorageReply>(reply.release()));
}

TEST_F(UpdateOperationTest, simple) {
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 90);

    ASSERT_EQ("UpdateReply(id:ns:text/html::1, BucketId(0x0000000000000000), "
              "timestamp 100, timestamp of updated doc: 90) ReturnCode(NONE)",
              sender.getLastReply(true));

    auto& metrics = getDistributor().getMetrics().updates;
    EXPECT_EQ(0, metrics.diverging_timestamp_updates.getValue());
}

TEST_F(UpdateOperationTest, not_found) {
    setupDistributor(1, 1, "storage:1 distributor:1");

    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 0);

    EXPECT_EQ("UpdateReply(id:ns:text/html::1, BucketId(0x0000000000000000), "
              "timestamp 100, timestamp of updated doc: 0) ReturnCode(NONE)",
              sender.getLastReply(true));
}

TEST_F(UpdateOperationTest, multi_node) {
    setupDistributor(2, 2, "distributor:1 storage:2");
    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 120);
    replyToMessage(*cb, sender, 1, 120);

    ASSERT_EQ("UpdateReply(id:ns:text/html::1, BucketId(0x0000000000000000), "
              "timestamp 100, timestamp of updated doc: 120) ReturnCode(NONE)",
              sender.getLastReply(true));

    ASSERT_EQ(_bId.toString() + " : "
              "node(idx=1,crc=0x2,docs=4/4,bytes=6/6,trusted=true,active=false,ready=false), "
              "node(idx=0,crc=0x2,docs=4/4,bytes=6/6,trusted=true,active=false,ready=false)",
              dumpBucket(_bId));

    auto& metrics = getDistributor().getMetrics().updates;
    EXPECT_EQ(0, metrics.diverging_timestamp_updates.getValue());
}

TEST_F(UpdateOperationTest, multi_node_inconsistent_timestamp) {
    setupDistributor(2, 2, "distributor:1 storage:2");
    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    replyToMessage(*cb, sender, 0, 119);
    replyToMessage(*cb, sender, 1, 120);

    ASSERT_EQ("UpdateReply(id:ns:text/html::1, BucketId(0x0000000000000000), "
              "timestamp 100, timestamp of updated doc: 120 Was inconsistent "
              "(best node 1)) ReturnCode(NONE)",
              sender.getLastReply(true));

    auto& metrics = getDistributor().getMetrics().updates;
    EXPECT_EQ(1, metrics.diverging_timestamp_updates.getValue());
}

TEST_F(UpdateOperationTest, test_and_set_failures_increment_tas_metric) {
    setupDistributor(2, 2, "distributor:1 storage:1");
    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3"));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));
    ASSERT_EQ("Update => 0", sender.getCommands(true));
    api::ReturnCode result(api::ReturnCode::TEST_AND_SET_CONDITION_FAILED, "bork bork");
    replyToMessage(*cb, sender, 0, 1234, api::BucketInfo(), result);

    ASSERT_EQ("UpdateReply(id:ns:text/html::1, BucketId(0x0000000000000000), "
              "timestamp 100, timestamp of updated doc: 0) "
              "ReturnCode(TEST_AND_SET_CONDITION_FAILED, bork bork)",
              sender.getLastReply(true));

    auto& metrics = getDistributor().getMetrics().updates;
    EXPECT_EQ(1, metrics.failures.test_and_set_failed.getValue());
}

// Create-if-missing updates have a rather finicky behavior in the backend, wherein they'll
// set the timestamp of the previous document to that of the _new_ document timestamp if
// the update ended up creating a document from scratch. This particular behavior confuses
// the "after the fact" timestamp consistency checks, since it will seem like the document
// that was created from scratch is a better candidate to force convergence towards rather
// than the ones that actually updated an existing document.
// We therefore detect this case specially and treat the received timestamps as if the
// document updated had a timestamp of zero.
// An alternative approach to this is to change the backend behavior by sending timestamps
// of zero in this case, but this would cause complications during rolling upgrades that would
// need explicit workaround logic anyway.
TEST_F(UpdateOperationTest, create_if_missing_update_sentinel_timestamp_is_treated_as_zero_timestamp) {
    setupDistributor(2, 2, "distributor:1 storage:2");
    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3", true));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1", sender.getCommands(true));

    // For these tests, it's deterministic that the newly assigned timestamp
    // is 100. Reply that we updated this timestamp on all nodes, implying
    // that the document was auto-created.
    replyToMessage(*cb, sender, 0, 100);
    replyToMessage(*cb, sender, 1, 100);

    ASSERT_EQ("UpdateReply(id:ns:text/html::1, BucketId(0x0000000000000000), "
              "timestamp 100, timestamp of updated doc: 0) ReturnCode(NONE)",
              sender.getLastReply(true));

    auto& metrics = getDistributor().getMetrics().updates;
    EXPECT_EQ(0, metrics.diverging_timestamp_updates.getValue());
}

TEST_F(UpdateOperationTest, inconsistent_create_if_missing_updates_picks_largest_non_auto_created_replica) {
    setupDistributor(2, 3, "distributor:1 storage:3");
    std::shared_ptr<UpdateOperation> cb(sendUpdate("0=1/2/3,1=1/2/3,2=1/2/3", true));
    DistributorMessageSenderStub sender;
    cb->start(sender, framework::MilliSecTime(0));

    ASSERT_EQ("Update => 0,Update => 1,Update => 2", sender.getCommands(true));
    replyToMessage(*cb, sender, 0, 100); // Newly created
    replyToMessage(*cb, sender, 2, 80); // Too old and dusty; should not be picked.
    replyToMessage(*cb, sender, 1, 90); // Should be picked

    ASSERT_EQ("UpdateReply(id:ns:text/html::1, BucketId(0x0000000000000000), "
              "timestamp 100, timestamp of updated doc: 90 Was inconsistent "
              "(best node 1)) ReturnCode(NONE)",
              sender.getLastReply(true));

    auto newest = cb->getNewestTimestampLocation();
    EXPECT_NE(newest.first, BucketId());
    EXPECT_EQ(newest.second, 1);

    auto& metrics = getDistributor().getMetrics().updates;
    // Implementation detail: since we get diverging results from nodes 2 and 1, these are
    // counted as separate diverging updates.
    EXPECT_EQ(2, metrics.diverging_timestamp_updates.getValue());
}

}
