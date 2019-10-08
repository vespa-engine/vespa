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
using namespace storage;
using namespace storage::distributor;
using namespace storage::api;
using namespace std;
using namespace storage::lib;
using namespace ::testing;
using config::ConfigGetter;
using config::FileSpec;
using vespalib::string;
using document::test::makeDocumentBucket;

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
    sendUpdate(const std::string& bucketState);

    document::BucketId _bId;
};

std::shared_ptr<UpdateOperation>
UpdateOperationTest::sendUpdate(const std::string& bucketState)
{
    auto update = std::make_shared<document::DocumentUpdate>(
            *_repo, *_html_type,
            document::DocumentId("id:ns:" + _html_type->getName() + "::1"));

    _bId = getExternalOperationHandler().getBucketId(update->getId());

    addNodesToBucketDB(_bId, bucketState);

    auto msg = std::make_shared<api::UpdateCommand>(makeDocumentBucket(document::BucketId(0)), update, 100);

    ExternalOperationHandler& handler = getExternalOperationHandler();
    return std::make_shared<UpdateOperation>(
            handler, getDistributorBucketSpace(), msg,
            getDistributor().getMetrics().updates[msg->getLoadType()]);
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

    auto& metrics = getDistributor().getMetrics().updates[documentapi::LoadType::DEFAULT];
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

    auto& metrics = getDistributor().getMetrics().updates[documentapi::LoadType::DEFAULT];
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

    auto& metrics = getDistributor().getMetrics().updates[documentapi::LoadType::DEFAULT];
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

    auto& metrics = getDistributor().getMetrics().updates[documentapi::LoadType::DEFAULT];
    EXPECT_EQ(1, metrics.failures.test_and_set_failed.getValue());
}

