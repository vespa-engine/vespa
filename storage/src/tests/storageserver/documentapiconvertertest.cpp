// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/subscription/configuri.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/messagebus/error.h>
#include <vespa/storage/common/bucket_resolver.h>
#include <vespa/storage/storageserver/documentapiconverter.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/test/test_path.h>
#include <vespa/documentapi/messagebus/messages/testandsetcondition.h>

using document::Bucket;
using document::BucketId;
using document::BucketSpace;
using document::DataType;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::readDocumenttypesConfig;
using document::test::makeDocumentBucket;
using documentapi::TestAndSetCondition;
using namespace ::testing;
using namespace std::chrono_literals;

namespace storage {

const DocumentId defaultDocId("id:test:text/html::0");
const BucketSpace defaultBucketSpace(5);
const std::string defaultSpaceName("myspace");
const Bucket defaultBucket(defaultBucketSpace, BucketId(0));
const TestAndSetCondition my_condition("my condition");

struct MockBucketResolver : public BucketResolver {
    Bucket bucketFromId(const DocumentId &documentId) const override {
        if (documentId.getDocType() == "text/html") {
            return defaultBucket;
        }
        return Bucket(BucketSpace(0), BucketId(0));
    }
    BucketSpace bucketSpaceFromName(const std::string &bucketSpace) const override {
        if (bucketSpace == defaultSpaceName) {
            return defaultBucketSpace;
        }
        return BucketSpace(0);
    }
    std::string nameFromBucketSpace(const document::BucketSpace &bucketSpace) const override {
        if (bucketSpace == defaultBucketSpace) {
            return defaultSpaceName;
        }
        return "";
    }
};

struct DocumentApiConverterTest : Test {
    std::shared_ptr<MockBucketResolver> _bucketResolver;
    std::unique_ptr<DocumentApiConverter> _converter;
    const std::shared_ptr<const DocumentTypeRepo> _repo;
    const DataType& _html_type;

    DocumentApiConverterTest()
        : _bucketResolver(std::make_shared<MockBucketResolver>()),
          _repo(std::make_shared<DocumentTypeRepo>(readDocumenttypesConfig(TEST_PATH("../config-doctypes.cfg")))),
          _html_type(*_repo->getDocumentType("text/html"))
    {
    }

    void SetUp() override {
        _converter = std::make_unique<DocumentApiConverter>(_bucketResolver);
    };

    template <typename DerivedT, typename BaseT>
    std::unique_ptr<DerivedT> dynamic_unique_ptr_cast(std::unique_ptr<BaseT> base) {
        auto derived = dynamic_cast<DerivedT*>(base.get());
        assert(derived);
        base.release();
        return std::unique_ptr<DerivedT>(derived);
    }

    template <typename T>
    std::unique_ptr<T> toStorageAPI(documentapi::DocumentMessage &msg) {
        auto result = _converter->toStorageAPI(msg);
        return dynamic_unique_ptr_cast<T>(std::move(result));
    }

    template <typename T>
    std::unique_ptr<T> toStorageAPI(mbus::Reply &fromReply,
                                    api::StorageCommand &fromCommand) {
        auto result = _converter->toStorageAPI(static_cast<documentapi::DocumentReply&>(fromReply), fromCommand);
        return dynamic_unique_ptr_cast<T>(std::move(result));
    }

    template <typename T>
    std::unique_ptr<T> toDocumentAPI(api::StorageCommand &cmd) {
        auto result = _converter->toDocumentAPI(cmd);
        return dynamic_unique_ptr_cast<T>(std::move(result));
    }
};

TEST_F(DocumentApiConverterTest, put) {
    auto doc = std::make_shared<Document>(*_repo, _html_type, defaultDocId);

    documentapi::PutDocumentMessage putmsg(doc);
    putmsg.setTimestamp(1234);
    putmsg.setCondition(my_condition);
    putmsg.setApproxSize(13371337);

    auto cmd = toStorageAPI<api::PutCommand>(putmsg);
    EXPECT_EQ(defaultBucket, cmd->getBucket());
    ASSERT_EQ(cmd->getDocument().get(), doc.get());
    EXPECT_EQ(cmd->getCondition(), my_condition);
    EXPECT_FALSE(cmd->get_create_if_non_existent());
    EXPECT_EQ(cmd->getApproxByteSize(), 13371337);

    std::unique_ptr<mbus::Reply> reply = putmsg.createReply();
    ASSERT_TRUE(reply.get());

    toStorageAPI<api::PutReply>(*reply, *cmd);

    auto mbusPut = toDocumentAPI<documentapi::PutDocumentMessage>(*cmd);
    ASSERT_EQ(mbusPut->getDocumentSP().get(), doc.get());
    EXPECT_EQ(mbusPut->getTimestamp(), 1234);
    EXPECT_EQ(mbusPut->getCondition(), my_condition);
    EXPECT_FALSE(mbusPut->get_create_if_non_existent());
    EXPECT_EQ(mbusPut->getApproxSize(), 13371337);
}

TEST_F(DocumentApiConverterTest, put_with_create) {
    documentapi::PutDocumentMessage putmsg(std::make_shared<Document>(*_repo, _html_type, defaultDocId));
    putmsg.setCondition(my_condition);
    putmsg.set_create_if_non_existent(true);
    auto cmd = toStorageAPI<api::PutCommand>(putmsg);
    EXPECT_TRUE(cmd->get_create_if_non_existent());
    auto mbusPut = toDocumentAPI<documentapi::PutDocumentMessage>(*cmd);
    EXPECT_TRUE(mbusPut->get_create_if_non_existent());
}

TEST_F(DocumentApiConverterTest, forwarded_put) {
    auto doc = std::make_shared<Document>(*_repo, _html_type, DocumentId("id:ns:" + _html_type.getName() + "::test"));

    auto putmsg = std::make_unique<documentapi::PutDocumentMessage>(doc);
    auto* putmsg_raw = putmsg.get();
    std::unique_ptr<mbus::Reply> reply(putmsg->createReply());
    reply->setMessage(std::unique_ptr<mbus::Message>(putmsg.release()));

    auto cmd = toStorageAPI<api::PutCommand>(*putmsg_raw);
    cmd->setTimestamp(1234);

    auto rep = dynamic_unique_ptr_cast<api::PutReply>(cmd->makeReply());
    _converter->transferReplyState(*rep, *reply);
}

TEST_F(DocumentApiConverterTest, update) {
    auto do_test_update = [&](bool create_if_missing) {
        auto update = std::make_shared<document::DocumentUpdate>(*_repo, _html_type, defaultDocId);
        update->setCreateIfNonExistent(create_if_missing);
        documentapi::UpdateDocumentMessage updateMsg(update);
        updateMsg.setOldTimestamp(1234);
        updateMsg.setNewTimestamp(5678);
        updateMsg.setCondition(my_condition);
        updateMsg.setApproxSize(13371337);
        EXPECT_FALSE(updateMsg.has_cached_create_if_missing());
        EXPECT_EQ(updateMsg.create_if_missing(), create_if_missing);

        auto updateCmd = toStorageAPI<api::UpdateCommand>(updateMsg);
        EXPECT_EQ(defaultBucket, updateCmd->getBucket());
        ASSERT_EQ(update.get(), updateCmd->getUpdate().get());
        EXPECT_EQ(api::Timestamp(1234), updateCmd->getOldTimestamp());
        EXPECT_EQ(api::Timestamp(5678), updateCmd->getTimestamp());
        EXPECT_EQ(my_condition, updateCmd->getCondition());
        EXPECT_FALSE(updateCmd->has_cached_create_if_missing());
        EXPECT_EQ(updateCmd->create_if_missing(), create_if_missing);
        EXPECT_EQ(updateCmd->getApproxByteSize(), 13371337);

        auto mbusReply = updateMsg.createReply();
        ASSERT_TRUE(mbusReply.get());
        toStorageAPI<api::UpdateReply>(*mbusReply, *updateCmd);

        auto mbusUpdate = toDocumentAPI<documentapi::UpdateDocumentMessage>(*updateCmd);
        ASSERT_EQ((&mbusUpdate->getDocumentUpdate()), update.get());
        EXPECT_EQ(api::Timestamp(1234), mbusUpdate->getOldTimestamp());
        EXPECT_EQ(api::Timestamp(5678), mbusUpdate->getNewTimestamp());
        EXPECT_EQ(my_condition, mbusUpdate->getCondition());
        EXPECT_EQ(mbusUpdate->create_if_missing(), create_if_missing);
        EXPECT_EQ(mbusUpdate->getApproxSize(), 13371337);

        // Cached value of create_if_missing should override underlying update's value
        updateCmd->set_cached_create_if_missing(!create_if_missing);
        EXPECT_TRUE(updateCmd->has_cached_create_if_missing());
        EXPECT_EQ(updateCmd->create_if_missing(), !create_if_missing);
        mbusUpdate = toDocumentAPI<documentapi::UpdateDocumentMessage>(*updateCmd);
        EXPECT_TRUE(mbusUpdate->has_cached_create_if_missing());
        EXPECT_EQ(mbusUpdate->create_if_missing(), !create_if_missing);
    };
    do_test_update(false);
    do_test_update(true);
}

TEST_F(DocumentApiConverterTest, remove) {
    documentapi::RemoveDocumentMessage removemsg(defaultDocId);
    removemsg.setCondition(my_condition);
    auto cmd = toStorageAPI<api::RemoveCommand>(removemsg);
    EXPECT_EQ(defaultBucket, cmd->getBucket());
    EXPECT_EQ(defaultDocId, cmd->getDocumentId());
    EXPECT_EQ(my_condition, cmd->getCondition());

    std::unique_ptr<mbus::Reply> reply = removemsg.createReply();
    ASSERT_TRUE(reply.get());

    toStorageAPI<api::RemoveReply>(*reply, *cmd);

    auto mbusRemove = toDocumentAPI<documentapi::RemoveDocumentMessage>(*cmd);
    EXPECT_EQ(defaultDocId, mbusRemove->getDocumentId());
    EXPECT_EQ(my_condition, mbusRemove->getCondition());
}

TEST_F(DocumentApiConverterTest, get) {
    documentapi::GetDocumentMessage getmsg(defaultDocId, "foo bar");

    auto cmd = toStorageAPI<api::GetCommand>(getmsg);
    EXPECT_EQ(defaultBucket, cmd->getBucket());
    EXPECT_EQ(defaultDocId, cmd->getDocumentId());
    EXPECT_EQ("foo bar", cmd->getFieldSet());
    EXPECT_EQ(false, cmd->has_debug_replica_node_id());
}

TEST_F(DocumentApiConverterTest, get_from_specific_replica) {
    documentapi::GetDocumentMessage getmsg(defaultDocId, "foo bar");
    getmsg.set_debug_replica_node_id(2);

    auto cmd = toStorageAPI<api::GetCommand>(getmsg);
    EXPECT_EQ(true, cmd->has_debug_replica_node_id());
    EXPECT_EQ(2, cmd->debug_replica_node_id().value());
}

TEST_F(DocumentApiConverterTest, create_visitor) {
    documentapi::CreateVisitorMessage cv("mylib", "myinstance", "control-dest", "data-dest");
    cv.setBucketSpace(defaultSpaceName);
    cv.setTimeRemaining(123456ms);

    auto cmd = toStorageAPI<api::CreateVisitorCommand>(cv);
    EXPECT_EQ(defaultBucketSpace, cmd->getBucket().getBucketSpace());
    EXPECT_EQ("mylib", cmd->getLibraryName());
    EXPECT_EQ("myinstance", cmd->getInstanceId());
    EXPECT_EQ("control-dest", cmd->getControlDestination());
    EXPECT_EQ("data-dest", cmd->getDataDestination());
    EXPECT_EQ(123456ms, cmd->getTimeout());

    auto msg = toDocumentAPI<documentapi::CreateVisitorMessage>(*cmd);
    EXPECT_EQ(defaultSpaceName, msg->getBucketSpace());
}

TEST_F(DocumentApiConverterTest, create_visitor_high_timeout) {
    documentapi::CreateVisitorMessage cv("mylib", "myinstance", "control-dest", "data-dest");
    cv.setTimeRemaining(std::chrono::milliseconds(1l << 32)); // Will be larger than INT_MAX

    auto cmd = toStorageAPI<api::CreateVisitorCommand>(cv);
    EXPECT_EQ("mylib", cmd->getLibraryName());
    EXPECT_EQ("myinstance", cmd->getInstanceId());
    EXPECT_EQ("control-dest", cmd->getControlDestination());
    EXPECT_EQ("data-dest", cmd->getDataDestination());
    EXPECT_EQ(std::numeric_limits<int32_t>::max(), vespalib::count_ms(cmd->getTimeout()));
}

TEST_F(DocumentApiConverterTest, create_visitor_reply_not_ready) {
    documentapi::CreateVisitorMessage cv("mylib", "myinstance", "control-dest", "data-dest");

    auto cmd = toStorageAPI<api::CreateVisitorCommand>(cv);
    api::CreateVisitorReply cvr(*cmd);
    cvr.setResult(api::ReturnCode(api::ReturnCode::NOT_READY, "not ready"));

    std::unique_ptr<documentapi::CreateVisitorReply> reply(
            dynamic_cast<documentapi::CreateVisitorReply*>(cv.createReply().release()));
    ASSERT_TRUE(reply.get());
    _converter->transferReplyState(cvr, *reply);
    EXPECT_EQ(documentapi::DocumentProtocol::ERROR_NODE_NOT_READY, reply->getError(0).getCode());
    EXPECT_EQ(document::BucketId(std::numeric_limits<int>::max()), reply->getLastBucket());
}

TEST_F(DocumentApiConverterTest, create_visitor_reply_last_bucket) {
    documentapi::CreateVisitorMessage cv("mylib", "myinstance", "control-dest", "data-dest");

    auto cmd = toStorageAPI<api::CreateVisitorCommand>(cv);
    api::CreateVisitorReply cvr(*cmd);
    cvr.setLastBucket(document::BucketId(123));
    std::unique_ptr<documentapi::CreateVisitorReply> reply(
            dynamic_cast<documentapi::CreateVisitorReply*>(cv.createReply().release()));

    ASSERT_TRUE(reply.get());
    _converter->transferReplyState(cvr, *reply);
    EXPECT_EQ(document::BucketId(123), reply->getLastBucket());
}

TEST_F(DocumentApiConverterTest, destroy_visitor) {
    documentapi::DestroyVisitorMessage cv("myinstance");

    auto cmd = toStorageAPI<api::DestroyVisitorCommand>(cv);
    EXPECT_EQ("myinstance", cmd->getInstanceId());
}

TEST_F(DocumentApiConverterTest, visitor_info) {
    api::VisitorInfoCommand vicmd;
    std::vector<api::VisitorInfoCommand::BucketTimestampPair> bucketsCompleted;
    bucketsCompleted.emplace_back(document::BucketId(16, 1), 0);
    bucketsCompleted.emplace_back(document::BucketId(16, 2), 0);
    bucketsCompleted.emplace_back(document::BucketId(16, 4), 0);

    vicmd.setBucketsCompleted(bucketsCompleted);

    auto mbusvi = toDocumentAPI<documentapi::VisitorInfoMessage>(vicmd);
    EXPECT_EQ(document::BucketId(16, 1), mbusvi->getFinishedBuckets()[0]);
    EXPECT_EQ(document::BucketId(16, 2), mbusvi->getFinishedBuckets()[1]);
    EXPECT_EQ(document::BucketId(16, 4), mbusvi->getFinishedBuckets()[2]);

    std::unique_ptr<mbus::Reply> reply = mbusvi->createReply();
    ASSERT_TRUE(reply.get());

    toStorageAPI<api::VisitorInfoReply>(*reply, vicmd);
}

TEST_F(DocumentApiConverterTest, stat_bucket) {
    documentapi::StatBucketMessage msg(BucketId(123), "");
    msg.setBucketSpace(defaultSpaceName);

    auto cmd = toStorageAPI<api::StatBucketCommand>(msg);
    EXPECT_EQ(Bucket(defaultBucketSpace, BucketId(123)), cmd->getBucket());

    auto mbusMsg = toDocumentAPI<documentapi::StatBucketMessage>(*cmd);
    EXPECT_EQ(BucketId(123), mbusMsg->getBucketId());
    EXPECT_EQ(defaultSpaceName, mbusMsg->getBucketSpace());
}

TEST_F(DocumentApiConverterTest, get_bucket_list) {
    documentapi::GetBucketListMessage msg(BucketId(123));
    msg.setBucketSpace(defaultSpaceName);

    auto cmd = toStorageAPI<api::GetBucketListCommand>(msg);
    EXPECT_EQ(Bucket(defaultBucketSpace, BucketId(123)), cmd->getBucket());
}

TEST_F(DocumentApiConverterTest, remove_location) {
    document::BucketIdFactory factory;
    document::select::Parser parser(*_repo, factory);
    documentapi::RemoveLocationMessage msg(factory, parser, "id.group == \"mygroup\"");
    msg.setBucketSpace(defaultSpaceName);

    auto cmd = toStorageAPI<api::RemoveLocationCommand>(msg);
    EXPECT_EQ(defaultBucket, cmd->getBucket());
}

namespace {

struct ReplacementMockBucketResolver : public MockBucketResolver {
    Bucket bucketFromId(const DocumentId& id) const override {
        if (id.getDocType() == "testdoctype1") {
            return defaultBucket;
        }
        return Bucket(BucketSpace(0), BucketId(0));
    }
};

}

TEST_F(DocumentApiConverterTest, can_replace_bucket_resolver_after_construction) {
    documentapi::GetDocumentMessage get_msg(DocumentId("id::testdoctype1::baz"), "foo bar");
    auto cmd = toStorageAPI<api::GetCommand>(get_msg);

    EXPECT_EQ(BucketSpace(0), cmd->getBucket().getBucketSpace());

    _converter->setBucketResolver(std::make_shared<ReplacementMockBucketResolver>());

    cmd = toStorageAPI<api::GetCommand>(get_msg);
    EXPECT_EQ(defaultBucketSpace, cmd->getBucket().getBucketSpace());
}

}
