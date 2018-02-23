// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/helper/configgetter.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/storage/distributor/externaloperationhandler.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storage/distributor/distributormetricsset.h>
#include <tests/distributor/distributortestutil.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/config/helper/configgetter.hpp>
#include <iomanip>
#include <vespa/storage/distributor/operations/external/getoperation.h>

using std::shared_ptr;
using config::ConfigGetter;
using document::DocumenttypesConfig;
using config::FileSpec;
using document::test::makeDocumentBucket;

namespace storage::distributor {

class GetOperationTest : public CppUnit::TestFixture, public DistributorTestUtil {
    CPPUNIT_TEST_SUITE(GetOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testNotFound);
    CPPUNIT_TEST(testResendOnStorageFailure);
    CPPUNIT_TEST(testResendOnStorageFailureAllFail);
    CPPUNIT_TEST(testSendToIdealCopyIfBucketInSync);
    CPPUNIT_TEST(testReturnNotFoundWhenBucketNotInDb);
    CPPUNIT_TEST(testAskAllNodesIfBucketIsInconsistent);
    CPPUNIT_TEST(testSendToAllInvalidNodesWhenInconsistent);
    CPPUNIT_TEST(testAskTrustedNodeIfBucketIsInconsistent);
    CPPUNIT_TEST(testInconsistentSplit); // Test that we ask all nodes if a bucket is inconsistent.
    CPPUNIT_TEST(testSendToAllInvalidCopies);
    CPPUNIT_TEST(testMultiInconsistentBucket);
    CPPUNIT_TEST(testMultiInconsistentBucketFail);
    CPPUNIT_TEST(testMultiInconsistentBucketNotFound);
    CPPUNIT_TEST(testMultiInconsistentBucketNotFoundDeleted);
    CPPUNIT_TEST(testMultipleCopiesWithFailureOnLocalNode);
    CPPUNIT_TEST(canGetDocumentsWhenAllReplicaNodesRetired);
    CPPUNIT_TEST_SUITE_END();

    document::DocumentTypeRepo::SP _repo;

public:
    document::DocumentId docId;
    document::BucketId bucketId;
    std::unique_ptr<Operation> op;

    void setUp() override {
        _repo.reset(
                new document::DocumentTypeRepo(*ConfigGetter<DocumenttypesConfig>::
                        getConfig("config-doctypes",
                                  FileSpec(TEST_PATH("config-doctypes.cfg")))));
        createLinks();

        docId = document::DocumentId(document::DocIdString("test", "uri"));
        bucketId = getExternalOperationHandler().getBucketId(docId);
    };

    void tearDown() override {
        close();
        op.reset();
    }

    void sendGet() {
        std::shared_ptr<api::GetCommand> msg(
                new api::GetCommand(makeDocumentBucket(document::BucketId(0)), docId, "[all]"));

        op.reset(new GetOperation(getExternalOperationHandler(),
                                  getDistributorBucketSpace(),
                                  msg,
                                  getDistributor().getMetrics().
                                  gets[msg->getLoadType()]));
        op->start(_sender, framework::MilliSecTime(0));
    }

    void sendReply(uint32_t idx,
               api::ReturnCode::Result result,
               std::string authorVal, uint32_t timestamp)
    {
        if (idx == (uint32_t)-1) {
            idx = _sender.commands.size() - 1;
        }

        std::shared_ptr<api::StorageCommand> msg2 = _sender.commands[idx];
        CPPUNIT_ASSERT_EQUAL(api::MessageType::GET, msg2->getType());

        api::GetCommand* tmp = static_cast<api::GetCommand*>(msg2.get());
        document::Document::SP doc;

        if (authorVal.length()) {
            const document::DocumentType* type(_repo->getDocumentType("text/html"));
            doc = document::Document::SP(
                    new document::Document(*type, docId));

            doc->setValue(doc->getField("author"),
                          document::StringFieldValue(authorVal));
        }

        api::GetReply* reply = new api::GetReply(*tmp, doc, timestamp);
        reply->setResult(result);

        op->receive(_sender, std::shared_ptr<api::StorageReply>(reply));
    }

    void replyWithFailure() {
        sendReply(-1, api::ReturnCode::IO_FAILURE, "", 0);
    }

    void replyWithNotFound() {
        sendReply(-1, api::ReturnCode::OK, "", 0);
    }

    void replyWithDocument() {
        sendReply(-1, api::ReturnCode::OK, "foo", 100);
    }

    std::string getLastReplyAuthor() {
        api::StorageMessage& msg = *_sender.replies[_sender.replies.size() - 1];

        if (msg.getType() == api::MessageType::GET_REPLY) {
            document::Document::SP doc(
                    dynamic_cast<api::GetReply&>(msg).getDocument());

            return doc->getValue(doc->getField("author"))->toString();
        } else {
            std::ostringstream ost;
            ost << "Last reply was not a GET reply, but " << msg;
            return ost.str();
        }
    }

    void setClusterState(const std::string& clusterState) {
        enableDistributorClusterState(clusterState);
    }

    void testSimple();
    void testReturnNotFoundWhenBucketNotInDb();
    void testNotFound();
    void testResendOnStorageFailure();
    void testResendOnStorageFailureAllFail();
    void testSendToIdealCopyIfBucketInSync();
    void testAskAllNodesIfBucketIsInconsistent();
    void testSendToAllInvalidNodesWhenInconsistent();
    void testAskTrustedNodeIfBucketIsInconsistent();
    void testInconsistentSplit();
    void testMultiInconsistentBucket();
    void testMultiInconsistentBucketFail();
    void testMultiInconsistentBucketNotFound();
    void testMultiInconsistentBucketNotFoundDeleted();
    void testSendToAllInvalidCopies();
    void testMultipleCopiesWithFailureOnLocalNode();
    void canGetDocumentsWhenAllReplicaNodesRetired();
};

CPPUNIT_TEST_SUITE_REGISTRATION(GetOperationTest);

void
GetOperationTest::testSimple()
{
    setClusterState("distributor:1 storage:2");

    addNodesToBucketDB(bucketId, "0=4,1=4");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0"),
            _sender.getCommands(true));

    replyWithDocument();

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 100) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
GetOperationTest::testAskTrustedNodeIfBucketIsInconsistent()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100/3/10,1=200/4/12/t");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(std::string("Get => 1"),
                         _sender.getCommands(true));

    replyWithDocument();

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 100) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
GetOperationTest::testAskAllNodesIfBucketIsInconsistent()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100/3/10,1=200/4/12");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0,Get => 1"),
            _sender.getCommands(true));

    sendReply(0, api::ReturnCode::OK, "newauthor", 2);
    sendReply(1, api::ReturnCode::OK, "oldauthor", 1);

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 2) ReturnCode(NONE)"),
            _sender.getLastReply());

    CPPUNIT_ASSERT_EQUAL(std::string("newauthor"), getLastReplyAuthor());
}


void
GetOperationTest::testSendToAllInvalidCopies()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "2=0/0/1,3=0/0/1");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 2,Get => 3"),
            _sender.getCommands(true));

    sendReply(0, api::ReturnCode::OK, "newauthor", 2);
    sendReply(1, api::ReturnCode::OK, "oldauthor", 1);

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 2) ReturnCode(NONE)"),
            _sender.getLastReply());

    CPPUNIT_ASSERT_EQUAL(std::string("newauthor"), getLastReplyAuthor());
}

void
GetOperationTest::testSendToAllInvalidNodesWhenInconsistent()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,1=200,2=0/0/1,3=0/0/1");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 2,Get => 3,Get => 0,Get => 1"),
            _sender.getCommands(true));

    sendReply(0, api::ReturnCode::OK, "newauthor", 2);
    sendReply(1, api::ReturnCode::OK, "oldauthor", 1);
    sendReply(2, api::ReturnCode::OK, "oldauthor", 1);
    sendReply(3, api::ReturnCode::OK, "oldauthor", 1);

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 2) ReturnCode(NONE)"),
            _sender.getLastReply());

    CPPUNIT_ASSERT_EQUAL(std::string("newauthor"), getLastReplyAuthor());
}

void
GetOperationTest::testInconsistentSplit()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(document::BucketId(16, 0x2a52), "0=100");
    addNodesToBucketDB(document::BucketId(17, 0x2a52), "1=200");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0,Get => 1"),
            _sender.getCommands(true));

    sendReply(0, api::ReturnCode::OK, "newauthor", 2);
    sendReply(1, api::ReturnCode::OK, "oldauthor", 1);

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 2) ReturnCode(NONE)"),
            _sender.getLastReply());

    CPPUNIT_ASSERT_EQUAL(std::string("newauthor"), getLastReplyAuthor());
}


void
GetOperationTest::testMultiInconsistentBucketNotFound()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0,Get => 1"),
            _sender.getCommands(true));

    sendReply(0, api::ReturnCode::OK, "newauthor", 2);
    sendReply(1, api::ReturnCode::OK, "", 0);

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 2) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
GetOperationTest::testMultiInconsistentBucketNotFoundDeleted()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0,Get => 1"),
            _sender.getCommands(true));

    sendReply(0, api::ReturnCode::OK, "newauthor", 2);
    // This signifies that the latest change was that the document was deleted
    // at timestamp 3.
    sendReply(1, api::ReturnCode::OK, "", 3);

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 3) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
GetOperationTest::testMultiInconsistentBucket()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0,Get => 1"),
            _sender.getCommands(true));

    sendReply(0, api::ReturnCode::OK, "newauthor", 2);
    sendReply(1, api::ReturnCode::OK, "oldauthor", 1);

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 2) ReturnCode(NONE)"),
            _sender.getLastReply());

    CPPUNIT_ASSERT_EQUAL(std::string("newauthor"), getLastReplyAuthor());
}

void
GetOperationTest::testMultiInconsistentBucketFail()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "0=100,2=100,1=200,3=200");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0,Get => 1"),
            _sender.getCommands(true));

    sendReply(0, api::ReturnCode::OK, "newauthor", 1);
    sendReply(1, api::ReturnCode::DISK_FAILURE, "", 0);

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000002a52), doc:test:uri) => 3"),
            _sender.getLastCommand());

    replyWithDocument();

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 100) ReturnCode(NONE)"),
            _sender.getLastReply());
}


void
GetOperationTest::testReturnNotFoundWhenBucketNotInDb()
{
    setClusterState("distributor:1 storage:1");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 0) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
GetOperationTest::testNotFound()
{
    setClusterState("distributor:1 storage:1");

    addNodesToBucketDB(bucketId, "0=100");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000002a52), doc:test:uri) => 0"),
            _sender.getLastCommand());

    replyWithNotFound();

   CPPUNIT_ASSERT_EQUAL(
           std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                       "timestamp 0) ReturnCode(NONE)"),
           _sender.getLastReply());

   CPPUNIT_ASSERT_EQUAL(1, (int)(getDistributor().
                                 getMetrics().gets[documentapi::LoadType::DEFAULT].
                                 failures.notfound.getValue()));
}

void
GetOperationTest::testResendOnStorageFailure()
{
    setClusterState("distributor:1 storage:3");

    // Add two nodes that are not trusted. GET should retry each one of them
    // if one fails.
    addNodesToBucketDB(bucketId, "1=100,2=100");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000002a52), doc:test:uri) => 1"),
            _sender.getLastCommand());

    replyWithFailure();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000002a52), doc:test:uri) => 2"),
            _sender.getLastCommand());

    replyWithDocument();

   CPPUNIT_ASSERT_EQUAL(
           std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                       "timestamp 100) ReturnCode(NONE)"),
           _sender.getLastReply());
}

void
GetOperationTest::testResendOnStorageFailureAllFail()
{
    setClusterState("distributor:1 storage:3");

    // Add two nodes that are not trusted. GET should retry each one of them
    // if one fails.
    addNodesToBucketDB(bucketId, "1=100,2=100");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000002a52), doc:test:uri) => 1"),
            _sender.getLastCommand());

    replyWithFailure();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000002a52), doc:test:uri) => 2"),
            _sender.getLastCommand());

    replyWithFailure();

   CPPUNIT_ASSERT_EQUAL(
           std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                       "timestamp 0) ReturnCode(IO_FAILURE)"),
           _sender.getLastReply());
}

void
GetOperationTest::testSendToIdealCopyIfBucketInSync()
{
    setClusterState("distributor:1 storage:4");

    addNodesToBucketDB(bucketId, "1=100,2=100,3=100");

    sendGet();

    // Should always send to node 1 (follow bucket db order)
    CPPUNIT_ASSERT_EQUAL(
            std::string("Get(BucketId(0x4000000000002a52), doc:test:uri) => 1"),
            _sender.getLastCommand());

    replyWithDocument();

   CPPUNIT_ASSERT_EQUAL(
           std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                       "timestamp 100) ReturnCode(NONE)"),
           _sender.getLastReply());
}

void
GetOperationTest::testMultipleCopiesWithFailureOnLocalNode()
{
    setClusterState("distributor:1 storage:4");

    // Node 0 is local copy to distributor 0 and will be preferred when
    // sending initially.
    addNodesToBucketDB(document::BucketId(16, 0x2a52), "2=100,0=100");

    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0"),
            _sender.getCommands(true));

    // Fail local node; no reply must be sent yet since we've got more nodes
    // to try.
    sendReply(0, api::ReturnCode::TIMEOUT, "", 0);

    // Retry with remaining copy on node 2.
    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0,Get => 2"),
            _sender.getCommands(true));

    sendReply(1, api::ReturnCode::OK, "newestauthor", 3);

    CPPUNIT_ASSERT_EQUAL(
            std::string("GetReply(BucketId(0x0000000000000000), doc:test:uri, "
                        "timestamp 3) ReturnCode(NONE)"),
            _sender.getLastReply());

    CPPUNIT_ASSERT_EQUAL(std::string("newestauthor"), getLastReplyAuthor());
}

void
GetOperationTest::canGetDocumentsWhenAllReplicaNodesRetired()
{
    setClusterState("distributor:1 storage:2 .0.s:r .1.s:r");
    addNodesToBucketDB(bucketId, "0=4,1=4");
    sendGet();

    CPPUNIT_ASSERT_EQUAL(
            std::string("Get => 0"),
            _sender.getCommands(true));
}

}
