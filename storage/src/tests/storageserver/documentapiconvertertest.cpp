// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <cppunit/extensions/HelperMacros.h>
#include <vespa/config/subscription/configuri.h>
#include <vespa/document/base/testdocrepo.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/storage/common/bucket_resolver.h>
#include <vespa/storage/storageserver/documentapiconverter.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/removelocation.h>
#include <vespa/storageapi/message/stat.h>
#include <vespa/vespalib/testkit/test_kit.h>

using document::Bucket;
using document::BucketId;
using document::BucketSpace;
using document::DataType;
using document::DocIdString;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::readDocumenttypesConfig;
using document::test::makeDocumentBucket;

namespace storage {

const DocumentId defaultDocId("id:test:text/html::0");
const BucketSpace defaultBucketSpace(5);
const vespalib::string defaultSpaceName("myspace");
const Bucket defaultBucket(defaultBucketSpace, BucketId(0));

struct MockBucketResolver : public BucketResolver {
    virtual Bucket bucketFromId(const DocumentId &documentId) const override {
        if (documentId.getDocType() == "text/html") {
            return defaultBucket;
        }
        return Bucket(BucketSpace(0), BucketId(0));
    }
    virtual BucketSpace bucketSpaceFromName(const vespalib::string &bucketSpace) const override {
        if (bucketSpace == defaultSpaceName) {
            return defaultBucketSpace;
        }
        return BucketSpace(0);
    }
    virtual vespalib::string nameFromBucketSpace(const document::BucketSpace &bucketSpace) const override {
        if (bucketSpace == defaultBucketSpace) {
            return defaultSpaceName;
        }
        return "";
    }
};

struct DocumentApiConverterTest : public CppUnit::TestFixture
{
    std::shared_ptr<MockBucketResolver> _bucketResolver;
    std::unique_ptr<DocumentApiConverter> _converter;
    const DocumentTypeRepo::SP _repo;
    const DataType& _html_type;

    DocumentApiConverterTest()
        : _bucketResolver(std::make_shared<MockBucketResolver>()),
          _repo(std::make_shared<DocumentTypeRepo>(readDocumenttypesConfig(
                    TEST_PATH("config-doctypes.cfg")))),
          _html_type(*_repo->getDocumentType("text/html"))
    {
    }

    void setUp() override {
        _converter.reset(new DocumentApiConverter("raw:", _bucketResolver));
    };

    template <typename DerivedT, typename BaseT>
    std::unique_ptr<DerivedT> dynamic_unique_ptr_cast(std::unique_ptr<BaseT> base) {
        auto derived = dynamic_cast<DerivedT*>(base.get());
        CPPUNIT_ASSERT(derived);
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

    void testPut();
    void testForwardedPut();
    void testUpdate();
    void testRemove();
    void testGet();
    void testCreateVisitor();
    void testCreateVisitorHighTimeout();
    void testCreateVisitorReplyNotReady();
    void testCreateVisitorReplyLastBucket();
    void testDestroyVisitor();
    void testVisitorInfo();
    void testBatchDocumentUpdate();
    void testStatBucket();
    void testGetBucketList();
    void testRemoveLocation();
    void can_replace_bucket_resolver_after_construction();

    CPPUNIT_TEST_SUITE(DocumentApiConverterTest);
    CPPUNIT_TEST(testPut);
    CPPUNIT_TEST(testForwardedPut);
    CPPUNIT_TEST(testUpdate);
    CPPUNIT_TEST(testRemove);
    CPPUNIT_TEST(testGet);
    CPPUNIT_TEST(testCreateVisitor);
    CPPUNIT_TEST(testCreateVisitorHighTimeout);
    CPPUNIT_TEST(testCreateVisitorReplyNotReady);
    CPPUNIT_TEST(testCreateVisitorReplyLastBucket);
    CPPUNIT_TEST(testDestroyVisitor);
    CPPUNIT_TEST(testVisitorInfo);
    CPPUNIT_TEST(testBatchDocumentUpdate);
    CPPUNIT_TEST(testStatBucket);
    CPPUNIT_TEST(testGetBucketList);
    CPPUNIT_TEST(testRemoveLocation);
    CPPUNIT_TEST(can_replace_bucket_resolver_after_construction);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DocumentApiConverterTest);

void DocumentApiConverterTest::testPut()
{
    auto doc = std::make_shared<Document>(_html_type, defaultDocId);

    documentapi::PutDocumentMessage putmsg(doc);
    putmsg.setTimestamp(1234);

    auto cmd = toStorageAPI<api::PutCommand>(putmsg);
    CPPUNIT_ASSERT_EQUAL(defaultBucket, cmd->getBucket());
    CPPUNIT_ASSERT(cmd->getDocument().get() == doc.get());

    std::unique_ptr<mbus::Reply> reply = putmsg.createReply();
    CPPUNIT_ASSERT(reply.get());

    toStorageAPI<api::PutReply>(*reply, *cmd);

    auto mbusPut = toDocumentAPI<documentapi::PutDocumentMessage>(*cmd);
    CPPUNIT_ASSERT(mbusPut->getDocumentSP().get() == doc.get());
    CPPUNIT_ASSERT(mbusPut->getTimestamp() == 1234);
}

void DocumentApiConverterTest::testForwardedPut()
{
    auto doc = std::make_shared<Document>(_html_type, DocumentId(DocIdString("test", "test")));

    documentapi::PutDocumentMessage* putmsg = new documentapi::PutDocumentMessage(doc);
    std::unique_ptr<mbus::Reply> reply(((documentapi::DocumentMessage*)putmsg)->createReply());
    reply->setMessage(std::unique_ptr<mbus::Message>(putmsg));

    auto cmd = toStorageAPI<api::PutCommand>(*putmsg);
    cmd->setTimestamp(1234);

    auto rep = dynamic_unique_ptr_cast<api::PutReply>(cmd->makeReply());
    _converter->transferReplyState(*rep, *reply);
}

void DocumentApiConverterTest::testUpdate()
{
    auto update = std::make_shared<document::DocumentUpdate>(_html_type, defaultDocId);
    documentapi::UpdateDocumentMessage updateMsg(update);
    updateMsg.setOldTimestamp(1234);
    updateMsg.setNewTimestamp(5678);

    auto updateCmd = toStorageAPI<api::UpdateCommand>(updateMsg);
    CPPUNIT_ASSERT_EQUAL(defaultBucket, updateCmd->getBucket());
    CPPUNIT_ASSERT_EQUAL(update.get(), updateCmd->getUpdate().get());
    CPPUNIT_ASSERT_EQUAL(api::Timestamp(1234), updateCmd->getOldTimestamp());
    CPPUNIT_ASSERT_EQUAL(api::Timestamp(5678), updateCmd->getTimestamp());

    auto mbusReply = updateMsg.createReply();
    CPPUNIT_ASSERT(mbusReply.get());
    toStorageAPI<api::UpdateReply>(*mbusReply, *updateCmd);

    auto mbusUpdate = toDocumentAPI<documentapi::UpdateDocumentMessage>(*updateCmd);
    CPPUNIT_ASSERT((&mbusUpdate->getDocumentUpdate()) == update.get());
    CPPUNIT_ASSERT_EQUAL(api::Timestamp(1234), mbusUpdate->getOldTimestamp());
    CPPUNIT_ASSERT_EQUAL(api::Timestamp(5678), mbusUpdate->getNewTimestamp());
}

void DocumentApiConverterTest::testRemove()
{
    documentapi::RemoveDocumentMessage removemsg(defaultDocId);
    auto cmd = toStorageAPI<api::RemoveCommand>(removemsg);
    CPPUNIT_ASSERT_EQUAL(defaultBucket, cmd->getBucket());
    CPPUNIT_ASSERT_EQUAL(defaultDocId, cmd->getDocumentId());

    std::unique_ptr<mbus::Reply> reply = removemsg.createReply();
    CPPUNIT_ASSERT(reply.get());

    toStorageAPI<api::RemoveReply>(*reply, *cmd);

    auto mbusRemove = toDocumentAPI<documentapi::RemoveDocumentMessage>(*cmd);
    CPPUNIT_ASSERT_EQUAL(defaultDocId, mbusRemove->getDocumentId());
}

void DocumentApiConverterTest::testGet()
{
    documentapi::GetDocumentMessage getmsg(defaultDocId, "foo bar");

    auto cmd = toStorageAPI<api::GetCommand>(getmsg);
    CPPUNIT_ASSERT_EQUAL(defaultBucket, cmd->getBucket());
    CPPUNIT_ASSERT_EQUAL(defaultDocId, cmd->getDocumentId());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo bar"), cmd->getFieldSet());
}

void DocumentApiConverterTest::testCreateVisitor()
{
    documentapi::CreateVisitorMessage cv("mylib", "myinstance", "control-dest", "data-dest");
    cv.setBucketSpace(defaultSpaceName);
    cv.setTimeRemaining(123456);

    auto cmd = toStorageAPI<api::CreateVisitorCommand>(cv);
    CPPUNIT_ASSERT_EQUAL(defaultBucketSpace, cmd->getBucket().getBucketSpace());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("mylib"), cmd->getLibraryName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("myinstance"), cmd->getInstanceId());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("control-dest"), cmd->getControlDestination());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("data-dest"), cmd->getDataDestination());
    CPPUNIT_ASSERT_EQUAL(123456u, cmd->getTimeout());

    auto msg = toDocumentAPI<documentapi::CreateVisitorMessage>(*cmd);
    CPPUNIT_ASSERT_EQUAL(defaultSpaceName, msg->getBucketSpace());
}

void DocumentApiConverterTest::testCreateVisitorHighTimeout()
{
    documentapi::CreateVisitorMessage cv("mylib", "myinstance", "control-dest", "data-dest");
    cv.setTimeRemaining((uint64_t)std::numeric_limits<uint32_t>::max() + 1); // Will be INT_MAX

    auto cmd = toStorageAPI<api::CreateVisitorCommand>(cv);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("mylib"), cmd->getLibraryName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("myinstance"), cmd->getInstanceId());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("control-dest"), cmd->getControlDestination());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("data-dest"), cmd->getDataDestination());
    CPPUNIT_ASSERT_EQUAL((uint32_t) std::numeric_limits<int32_t>::max(), cmd->getTimeout());
}

void DocumentApiConverterTest::testCreateVisitorReplyNotReady()
{
    documentapi::CreateVisitorMessage cv("mylib", "myinstance", "control-dest", "data-dest");

    auto cmd = toStorageAPI<api::CreateVisitorCommand>(cv);
    api::CreateVisitorReply cvr(*cmd);
    cvr.setResult(api::ReturnCode(api::ReturnCode::NOT_READY, "not ready"));

    std::unique_ptr<documentapi::CreateVisitorReply> reply(
            dynamic_cast<documentapi::CreateVisitorReply*>(cv.createReply().release()));
    CPPUNIT_ASSERT(reply.get());
    _converter->transferReplyState(cvr, *reply);
    CPPUNIT_ASSERT_EQUAL((uint32_t)documentapi::DocumentProtocol::ERROR_NODE_NOT_READY, reply->getError(0).getCode());
    CPPUNIT_ASSERT_EQUAL(document::BucketId(std::numeric_limits<int>::max()), reply->getLastBucket());
}

void DocumentApiConverterTest::testCreateVisitorReplyLastBucket()
{
    documentapi::CreateVisitorMessage cv("mylib", "myinstance", "control-dest", "data-dest");

    auto cmd = toStorageAPI<api::CreateVisitorCommand>(cv);
    api::CreateVisitorReply cvr(*cmd);
    cvr.setLastBucket(document::BucketId(123));
    std::unique_ptr<documentapi::CreateVisitorReply> reply(
            dynamic_cast<documentapi::CreateVisitorReply*>(cv.createReply().release()));

    CPPUNIT_ASSERT(reply.get());
    _converter->transferReplyState(cvr, *reply);
    CPPUNIT_ASSERT_EQUAL(document::BucketId(123), reply->getLastBucket());
}

void DocumentApiConverterTest::testDestroyVisitor()
{
    documentapi::DestroyVisitorMessage cv("myinstance");

    auto cmd = toStorageAPI<api::DestroyVisitorCommand>(cv);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("myinstance"), cmd->getInstanceId());
}

void
DocumentApiConverterTest::testVisitorInfo()
{
    api::VisitorInfoCommand vicmd;
    std::vector<api::VisitorInfoCommand::BucketTimestampPair> bucketsCompleted;
    bucketsCompleted.push_back(api::VisitorInfoCommand::BucketTimestampPair(document::BucketId(16, 1), 0));
    bucketsCompleted.push_back(api::VisitorInfoCommand::BucketTimestampPair(document::BucketId(16, 2), 0));
    bucketsCompleted.push_back(api::VisitorInfoCommand::BucketTimestampPair(document::BucketId(16, 4), 0));

    vicmd.setBucketsCompleted(bucketsCompleted);

    auto mbusvi = toDocumentAPI<documentapi::VisitorInfoMessage>(vicmd);
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1), mbusvi->getFinishedBuckets()[0]);
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 2), mbusvi->getFinishedBuckets()[1]);
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 4), mbusvi->getFinishedBuckets()[2]);

    std::unique_ptr<mbus::Reply> reply = mbusvi->createReply();
    CPPUNIT_ASSERT(reply.get());

    toStorageAPI<api::VisitorInfoReply>(*reply, vicmd);
}

void
DocumentApiConverterTest::testBatchDocumentUpdate()
{
    std::vector<document::DocumentUpdate::SP > updates;

    {
        document::DocumentId docId(document::UserDocIdString("userdoc:test:1234:test1"));
        auto update = std::make_shared<document::DocumentUpdate>(_html_type, docId);
        updates.push_back(update);
    }

    {
        document::DocumentId docId(document::UserDocIdString("userdoc:test:1234:test2"));
        auto update = std::make_shared<document::DocumentUpdate>(_html_type, docId);
        updates.push_back(update);
    }

    {
        document::DocumentId docId(document::UserDocIdString("userdoc:test:1234:test3"));
        auto update = std::make_shared<document::DocumentUpdate>(_html_type, docId);
        updates.push_back(update);
    }

    auto msg = std::make_shared<documentapi::BatchDocumentUpdateMessage>(1234);
    for (std::size_t i = 0; i < updates.size(); ++i) {
        msg->addUpdate(updates[i]);
    }

    auto batchCmd = toStorageAPI<api::BatchDocumentUpdateCommand>(*msg);
    CPPUNIT_ASSERT_EQUAL(updates.size(), batchCmd->getUpdates().size());
    for (std::size_t i = 0; i < updates.size(); ++i) {
        CPPUNIT_ASSERT_EQUAL(*updates[i], *batchCmd->getUpdates()[i]);
    }

    api::BatchDocumentUpdateReply batchReply(*batchCmd);
    batchReply.getDocumentsNotFound().resize(3);
    batchReply.getDocumentsNotFound()[0] = true;
    batchReply.getDocumentsNotFound()[2] = true;

    std::unique_ptr<mbus::Reply> mbusReply = msg->createReply();
    documentapi::BatchDocumentUpdateReply* mbusBatchReply(
            dynamic_cast<documentapi::BatchDocumentUpdateReply*>(mbusReply.get()));
    CPPUNIT_ASSERT(mbusBatchReply != 0);

    _converter->transferReplyState(batchReply, *mbusReply);

    CPPUNIT_ASSERT_EQUAL(std::size_t(3), mbusBatchReply->getDocumentsNotFound().size());
    CPPUNIT_ASSERT(mbusBatchReply->getDocumentsNotFound()[0] == true);
    CPPUNIT_ASSERT(mbusBatchReply->getDocumentsNotFound()[1] == false);
    CPPUNIT_ASSERT(mbusBatchReply->getDocumentsNotFound()[2] == true);
}

void
DocumentApiConverterTest::testStatBucket()
{
    documentapi::StatBucketMessage msg(BucketId(123), "");
    msg.setBucketSpace(defaultSpaceName);

    auto cmd = toStorageAPI<api::StatBucketCommand>(msg);
    CPPUNIT_ASSERT_EQUAL(Bucket(defaultBucketSpace, BucketId(123)), cmd->getBucket());

    auto mbusMsg = toDocumentAPI<documentapi::StatBucketMessage>(*cmd);
    CPPUNIT_ASSERT_EQUAL(BucketId(123), mbusMsg->getBucketId());
    CPPUNIT_ASSERT_EQUAL(defaultSpaceName, mbusMsg->getBucketSpace());
}

void
DocumentApiConverterTest::testGetBucketList()
{
    documentapi::GetBucketListMessage msg(BucketId(123));
    msg.setBucketSpace(defaultSpaceName);

    auto cmd = toStorageAPI<api::GetBucketListCommand>(msg);
    CPPUNIT_ASSERT_EQUAL(Bucket(defaultBucketSpace, BucketId(123)), cmd->getBucket());
}

void
DocumentApiConverterTest::testRemoveLocation()
{
    document::BucketIdFactory factory;
    document::select::Parser parser(*_repo, factory);
    documentapi::RemoveLocationMessage msg(factory, parser, "id.group == \"mygroup\"");
    msg.setBucketSpace(defaultSpaceName);

    auto cmd = toStorageAPI<api::RemoveLocationCommand>(msg);
    CPPUNIT_ASSERT_EQUAL(defaultBucket, cmd->getBucket());
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

void DocumentApiConverterTest::can_replace_bucket_resolver_after_construction() {
    documentapi::GetDocumentMessage get_msg(DocumentId("id::testdoctype1::baz"), "foo bar");
    auto cmd = toStorageAPI<api::GetCommand>(get_msg);

    CPPUNIT_ASSERT_EQUAL(BucketSpace(0), cmd->getBucket().getBucketSpace());

    _converter->setBucketResolver(std::make_shared<ReplacementMockBucketResolver>());

    cmd = toStorageAPI<api::GetCommand>(get_msg);
    CPPUNIT_ASSERT_EQUAL(defaultBucketSpace, cmd->getBucket().getBucketSpace());
}

}
