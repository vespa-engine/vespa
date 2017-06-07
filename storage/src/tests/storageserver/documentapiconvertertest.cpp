// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/base/testdocrepo.h>
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/storage/storageserver/documentapiconverter.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/storageapi/message/datagram.h>
#include <vespa/storageapi/message/multioperation.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/bucket/bucketidfactory.h>
#include <vespa/vespalib/testkit/test_kit.h>

using document::DataType;
using document::DocIdString;
using document::Document;
using document::DocumentId;
using document::DocumentTypeRepo;
using document::readDocumenttypesConfig;

namespace storage {

struct DocumentApiConverterTest : public CppUnit::TestFixture
{
    std::unique_ptr<DocumentApiConverter> _converter;
    const DocumentTypeRepo::SP _repo;
    const DataType& _html_type;

    DocumentApiConverterTest()
        : _repo(new DocumentTypeRepo(readDocumenttypesConfig(
                    TEST_PATH("config-doctypes.cfg")))),
          _html_type(*_repo->getDocumentType("text/html"))
    {
    }

    void setUp() override {
        _converter.reset(new DocumentApiConverter("raw:"));
    };

    void testPut();
    void testForwardedPut();
    void testRemove();
    void testGet();
    void testCreateVisitor();
    void testCreateVisitorHighTimeout();
    void testCreateVisitorReplyNotReady();
    void testCreateVisitorReplyLastBucket();
    void testDestroyVisitor();
    void testVisitorInfo();
    void testDocBlock();
    void testDocBlockWithKeepTimeStamps();
    void testMultiOperation();
    void testBatchDocumentUpdate();

    CPPUNIT_TEST_SUITE(DocumentApiConverterTest);
    CPPUNIT_TEST(testPut);
    CPPUNIT_TEST(testForwardedPut);
    CPPUNIT_TEST(testRemove);
    CPPUNIT_TEST(testGet);
    CPPUNIT_TEST(testCreateVisitor);
    CPPUNIT_TEST(testCreateVisitorHighTimeout);
    CPPUNIT_TEST(testCreateVisitorReplyNotReady);
    CPPUNIT_TEST(testCreateVisitorReplyLastBucket);
    CPPUNIT_TEST(testDestroyVisitor);
    CPPUNIT_TEST(testVisitorInfo);
    CPPUNIT_TEST(testDocBlock);
    CPPUNIT_TEST(testDocBlockWithKeepTimeStamps);
    CPPUNIT_TEST(testMultiOperation);
    CPPUNIT_TEST(testBatchDocumentUpdate);
    CPPUNIT_TEST_SUITE_END();
};

CPPUNIT_TEST_SUITE_REGISTRATION(DocumentApiConverterTest);

void DocumentApiConverterTest::testPut()
{
    Document::SP
        doc(new Document(_html_type, DocumentId(DocIdString("test", "test"))));

    documentapi::PutDocumentMessage putmsg(doc);
    putmsg.setTimestamp(1234);

    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(putmsg, _repo);

    api::PutCommand* pc = dynamic_cast<api::PutCommand*>(cmd.get());

    CPPUNIT_ASSERT(pc);
    CPPUNIT_ASSERT(pc->getDocument().get() == doc.get());

    std::unique_ptr<mbus::Reply> reply = putmsg.createReply();
    CPPUNIT_ASSERT(reply.get());

    std::unique_ptr<storage::api::StorageReply> rep = _converter->toStorageAPI(
            static_cast<documentapi::DocumentReply&>(*reply), *cmd);
    api::PutReply* pr = dynamic_cast<api::PutReply*>(rep.get());
    CPPUNIT_ASSERT(pr);

    std::unique_ptr<mbus::Message> mbusmsg =
        _converter->toDocumentAPI(*pc, _repo);

    documentapi::PutDocumentMessage* mbusput = dynamic_cast<documentapi::PutDocumentMessage*>(mbusmsg.get());
    CPPUNIT_ASSERT(mbusput);
    CPPUNIT_ASSERT(mbusput->getDocument().get() == doc.get());
    CPPUNIT_ASSERT(mbusput->getTimestamp() == 1234);
};

void DocumentApiConverterTest::testForwardedPut()
{
    Document::SP
        doc(new Document(_html_type, DocumentId(DocIdString("test", "test"))));

    documentapi::PutDocumentMessage* putmsg = new documentapi::PutDocumentMessage(doc);
    std::unique_ptr<mbus::Reply> reply(((documentapi::DocumentMessage*)putmsg)->createReply());
    reply->setMessage(std::unique_ptr<mbus::Message>(putmsg));

    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(*putmsg, _repo);
    ((storage::api::PutCommand*)cmd.get())->setTimestamp(1234);

    std::unique_ptr<storage::api::StorageReply> rep = cmd->makeReply();
    api::PutReply* pr = dynamic_cast<api::PutReply*>(rep.get());
    CPPUNIT_ASSERT(pr);

    _converter->transferReplyState(*pr, *reply);
}

void DocumentApiConverterTest::testRemove()
{
    documentapi::RemoveDocumentMessage removemsg(document::DocumentId(document::DocIdString("test", "test")));
    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(removemsg, _repo);

    api::RemoveCommand* rc = dynamic_cast<api::RemoveCommand*>(cmd.get());

    CPPUNIT_ASSERT(rc);
    CPPUNIT_ASSERT_EQUAL(document::DocumentId(document::DocIdString("test", "test")), rc->getDocumentId());

    std::unique_ptr<mbus::Reply> reply = removemsg.createReply();
    CPPUNIT_ASSERT(reply.get());

    std::unique_ptr<storage::api::StorageReply> rep = _converter->toStorageAPI(
            static_cast<documentapi::DocumentReply&>(*reply), *cmd);
    api::RemoveReply* pr = dynamic_cast<api::RemoveReply*>(rep.get());
    CPPUNIT_ASSERT(pr);

    std::unique_ptr<mbus::Message> mbusmsg =
        _converter->toDocumentAPI(*rc, _repo);

    documentapi::RemoveDocumentMessage* mbusremove = dynamic_cast<documentapi::RemoveDocumentMessage*>(mbusmsg.get());
    CPPUNIT_ASSERT(mbusremove);
    CPPUNIT_ASSERT_EQUAL(document::DocumentId(document::DocIdString("test", "test")), mbusremove->getDocumentId());
};

void DocumentApiConverterTest::testGet()
{
    documentapi::GetDocumentMessage getmsg(
            document::DocumentId(document::DocIdString("test", "test")),
            "foo bar");

    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(getmsg, _repo);

    api::GetCommand* rc = dynamic_cast<api::GetCommand*>(cmd.get());

    CPPUNIT_ASSERT(rc);
    CPPUNIT_ASSERT_EQUAL(document::DocumentId(document::DocIdString("test", "test")), rc->getDocumentId());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("foo bar"), rc->getFieldSet());
};

void DocumentApiConverterTest::testCreateVisitor()
{
    documentapi::CreateVisitorMessage cv(
            "mylib",
            "myinstance",
            "control-dest",
            "data-dest");

    cv.setTimeRemaining(123456);

    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(cv, _repo);

    api::CreateVisitorCommand* pc = dynamic_cast<api::CreateVisitorCommand*>(cmd.get());

    CPPUNIT_ASSERT(pc);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("mylib"), pc->getLibraryName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("myinstance"), pc->getInstanceId());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("control-dest"), pc->getControlDestination());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("data-dest"), pc->getDataDestination());
    CPPUNIT_ASSERT_EQUAL(123456u, pc->getTimeout());
}

void DocumentApiConverterTest::testCreateVisitorHighTimeout()
{
    documentapi::CreateVisitorMessage cv(
            "mylib",
            "myinstance",
            "control-dest",
            "data-dest");

    cv.setTimeRemaining((uint64_t)std::numeric_limits<uint32_t>::max() + 1); // Will be INT_MAX

    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(cv, _repo);

    api::CreateVisitorCommand* pc = dynamic_cast<api::CreateVisitorCommand*>(cmd.get());

    CPPUNIT_ASSERT(pc);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("mylib"), pc->getLibraryName());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("myinstance"), pc->getInstanceId());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("control-dest"), pc->getControlDestination());
    CPPUNIT_ASSERT_EQUAL(vespalib::string("data-dest"), pc->getDataDestination());
    CPPUNIT_ASSERT_EQUAL((uint32_t) std::numeric_limits<int32_t>::max(),
                         pc->getTimeout());
}

void DocumentApiConverterTest::testCreateVisitorReplyNotReady()
{
    documentapi::CreateVisitorMessage cv(
            "mylib",
            "myinstance",
            "control-dest",
            "data-dest");

    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(cv, _repo);
    CPPUNIT_ASSERT(cmd.get());
    api::CreateVisitorCommand& cvc = dynamic_cast<api::CreateVisitorCommand&>(*cmd);

    api::CreateVisitorReply cvr(cvc);
    cvr.setResult(api::ReturnCode(api::ReturnCode::NOT_READY, "not ready"));

    std::unique_ptr<documentapi::CreateVisitorReply> reply(
            dynamic_cast<documentapi::CreateVisitorReply*>(
                    cv.createReply().release()));
    CPPUNIT_ASSERT(reply.get());

    _converter->transferReplyState(cvr, *reply);

    CPPUNIT_ASSERT_EQUAL((uint32_t)documentapi::DocumentProtocol::ERROR_NODE_NOT_READY, reply->getError(0).getCode());

    CPPUNIT_ASSERT_EQUAL(document::BucketId(INT_MAX), reply->getLastBucket());
}


void DocumentApiConverterTest::testCreateVisitorReplyLastBucket()
{
    documentapi::CreateVisitorMessage cv(
            "mylib",
            "myinstance",
            "control-dest",
            "data-dest");

    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(cv, _repo);
    CPPUNIT_ASSERT(cmd.get());
    api::CreateVisitorCommand& cvc = dynamic_cast<api::CreateVisitorCommand&>(*cmd);


    api::CreateVisitorReply cvr(cvc);
    cvr.setLastBucket(document::BucketId(123));


    std::unique_ptr<documentapi::CreateVisitorReply> reply(
            dynamic_cast<documentapi::CreateVisitorReply*>(
                    cv.createReply().release()));

    CPPUNIT_ASSERT(reply.get());

    _converter->transferReplyState(cvr, *reply);

    CPPUNIT_ASSERT_EQUAL(document::BucketId(123), reply->getLastBucket());
}


void DocumentApiConverterTest::testDestroyVisitor()
{
    documentapi::DestroyVisitorMessage cv("myinstance");

    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(cv, _repo);

    api::DestroyVisitorCommand* pc = dynamic_cast<api::DestroyVisitorCommand*>(cmd.get());

    CPPUNIT_ASSERT(pc);
    CPPUNIT_ASSERT_EQUAL(vespalib::string("myinstance"), pc->getInstanceId());
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

    std::unique_ptr<mbus::Message> mbusmsg =
        _converter->toDocumentAPI(vicmd, _repo);

    documentapi::VisitorInfoMessage* mbusvi = dynamic_cast<documentapi::VisitorInfoMessage*>(mbusmsg.get());
    CPPUNIT_ASSERT(mbusvi);
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 1), mbusvi->getFinishedBuckets()[0]);
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 2), mbusvi->getFinishedBuckets()[1]);
    CPPUNIT_ASSERT_EQUAL(document::BucketId(16, 4), mbusvi->getFinishedBuckets()[2]);

    std::unique_ptr<mbus::Reply> reply = mbusvi->createReply();
    CPPUNIT_ASSERT(reply.get());

    std::unique_ptr<storage::api::StorageReply> rep = _converter->toStorageAPI(
            static_cast<documentapi::DocumentReply&>(*reply), vicmd);
    api::VisitorInfoReply* pr = dynamic_cast<api::VisitorInfoReply*>(rep.get());
    CPPUNIT_ASSERT(pr);
}

void
DocumentApiConverterTest::testDocBlock()
{
    Document::SP
        doc(new Document(_html_type, DocumentId(DocIdString("test", "test"))));

    char buffer[10000];
    vdslib::WritableDocumentList docBlock(_repo, buffer, sizeof(buffer));
    docBlock.addPut(*doc, 100);

    document::BucketIdFactory fac;
    document::BucketId bucketId = fac.getBucketId(doc->getId());
    bucketId.setUsedBits(32);

    api::DocBlockCommand dbcmd(bucketId, docBlock, std::shared_ptr<void>());

    dbcmd.setTimeout(123456);

    std::unique_ptr<mbus::Message> mbusmsg =
        _converter->toDocumentAPI(dbcmd, _repo);

    documentapi::MultiOperationMessage* mbusdb = dynamic_cast<documentapi::MultiOperationMessage*>(mbusmsg.get());
    CPPUNIT_ASSERT(mbusdb);

    CPPUNIT_ASSERT_EQUAL((uint64_t)123456, mbusdb->getTimeRemaining());

    const vdslib::DocumentList& list = mbusdb->getOperations();
    CPPUNIT_ASSERT_EQUAL((uint32_t)1, list.size());
    CPPUNIT_ASSERT_EQUAL(*doc, *dynamic_cast<document::Document*>(list.begin()->getDocument().get()));

    std::unique_ptr<mbus::Reply> reply = mbusdb->createReply();
    CPPUNIT_ASSERT(reply.get());

    std::unique_ptr<storage::api::StorageReply> rep =
        _converter->toStorageAPI(static_cast<documentapi::DocumentReply&>(*reply), dbcmd);
    api::DocBlockReply* pr = dynamic_cast<api::DocBlockReply*>(rep.get());
    CPPUNIT_ASSERT(pr);
}


void
DocumentApiConverterTest::testDocBlockWithKeepTimeStamps()
{
    char buffer[10000];
    vdslib::WritableDocumentList docBlock(_repo, buffer, sizeof(buffer));
    api::DocBlockCommand dbcmd(document::BucketId(0), docBlock, std::shared_ptr<void>());

    {
	CPPUNIT_ASSERT_EQUAL(dbcmd.keepTimeStamps(), false);
	
	std::unique_ptr<mbus::Message> mbusmsg =
            _converter->toDocumentAPI(dbcmd, _repo);
	
	documentapi::MultiOperationMessage* mbusdb = dynamic_cast<documentapi::MultiOperationMessage*>(mbusmsg.get());
	CPPUNIT_ASSERT(mbusdb);
	
	CPPUNIT_ASSERT_EQUAL(mbusdb->keepTimeStamps(), false);
    }

    {
	dbcmd.keepTimeStamps(true);
	CPPUNIT_ASSERT_EQUAL(dbcmd.keepTimeStamps(), true);
	
	std::unique_ptr<mbus::Message> mbusmsg =
            _converter->toDocumentAPI(dbcmd, _repo);
	
	documentapi::MultiOperationMessage* mbusdb = dynamic_cast<documentapi::MultiOperationMessage*>(mbusmsg.get());
	CPPUNIT_ASSERT(mbusdb);
	
	CPPUNIT_ASSERT_EQUAL(mbusdb->keepTimeStamps(), true);
    }

}


void
DocumentApiConverterTest::testMultiOperation()
{
    //create a document
    Document::SP
        doc(new Document(_html_type, DocumentId(DocIdString("test", "test"))));

    document::BucketIdFactory fac;
    document::BucketId bucketId = fac.getBucketId(doc->getId());
    bucketId.setUsedBits(32);

    {
        documentapi::MultiOperationMessage momsg(_repo, bucketId, 10000);

        vdslib::WritableDocumentList operations(_repo, &(momsg.getBuffer()[0]),
                                                momsg.getBuffer().size());
        operations.addPut(*doc, 100);

        momsg.setOperations(operations);

        CPPUNIT_ASSERT(momsg.getBuffer().size() > 0);

        // Convert it to Storage API
        std::unique_ptr<api::StorageCommand> stcmd =
            _converter->toStorageAPI(momsg, _repo);

        api::MultiOperationCommand* mocmd = dynamic_cast<api::MultiOperationCommand*>(stcmd.get());
        CPPUNIT_ASSERT(mocmd);
        CPPUNIT_ASSERT(mocmd->getBuffer().size() > 0);

        // Get operations from Storage API message and check document
        const vdslib::DocumentList& list = mocmd->getOperations();
        CPPUNIT_ASSERT_EQUAL((uint32_t)1, list.size());
        CPPUNIT_ASSERT_EQUAL(*doc, *dynamic_cast<document::Document*>(list.begin()->getDocument().get()));

        // Create Storage API Reply
        std::unique_ptr<api::MultiOperationReply> moreply = std::unique_ptr<api::MultiOperationReply>(new api::MultiOperationReply(*mocmd));
        CPPUNIT_ASSERT(moreply.get());

        // convert storage api reply to mbus reply.....
        // ...
    }

    {
        api::MultiOperationCommand mocmd(_repo, bucketId, 10000, false);
        mocmd.getOperations().addPut(*doc, 100);

        // Convert it to documentapi
        std::unique_ptr<mbus::Message> mbmsg =
            _converter->toDocumentAPI(mocmd, _repo);
        documentapi::MultiOperationMessage* momsg = dynamic_cast<documentapi::MultiOperationMessage*>(mbmsg.get());
        CPPUNIT_ASSERT(momsg);

        // Get operations from Document API msg and check document
        const vdslib::DocumentList& list = momsg->getOperations();
        CPPUNIT_ASSERT_EQUAL((uint32_t)1, list.size());
        CPPUNIT_ASSERT_EQUAL(*doc, *dynamic_cast<document::Document*>(list.begin()->getDocument().get()));

        // Create Document API reply
        mbus::Reply::UP moreply = momsg->createReply();
        CPPUNIT_ASSERT(moreply.get());

        //Convert DocumentAPI reply to storageapi reply
        std::unique_ptr<api::StorageReply> streply =
            _converter->toStorageAPI(static_cast<documentapi::DocumentReply&>(*moreply), mocmd);
        api::MultiOperationReply* mostreply = dynamic_cast<api::MultiOperationReply*>(streply.get());
        CPPUNIT_ASSERT(mostreply);

    }
}

void
DocumentApiConverterTest::testBatchDocumentUpdate()
{
    std::vector<document::DocumentUpdate::SP > updates;

    {
        document::DocumentId docId(document::UserDocIdString("userdoc:test:1234:test1"));
        document::DocumentUpdate::SP update(
                new document::DocumentUpdate(_html_type, docId));
        updates.push_back(update);
    }

    {
        document::DocumentId docId(document::UserDocIdString("userdoc:test:1234:test2"));
        document::DocumentUpdate::SP update(
                new document::DocumentUpdate(_html_type, docId));
        updates.push_back(update);
    }

    {
        document::DocumentId docId(document::UserDocIdString("userdoc:test:1234:test3"));
        document::DocumentUpdate::SP update(
                new document::DocumentUpdate(_html_type, docId));
        updates.push_back(update);
    }

    std::shared_ptr<documentapi::BatchDocumentUpdateMessage> msg(
            new documentapi::BatchDocumentUpdateMessage(1234));
    for (std::size_t i = 0; i < updates.size(); ++i) {
        msg->addUpdate(updates[i]);
    }

    std::unique_ptr<storage::api::StorageCommand> cmd =
        _converter->toStorageAPI(*msg, _repo);
    api::BatchDocumentUpdateCommand* batchCmd = dynamic_cast<api::BatchDocumentUpdateCommand*>(cmd.get());
    CPPUNIT_ASSERT(batchCmd);
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

}
