// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/helper/configgetter.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/storage/distributor/operations/external/putoperation.h>
#include <vespa/storage/distributor/distributor.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <tests/distributor/distributortestutil.h>
#include <tests/common/dummystoragelink.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/vespalib/text/stringtokenizer.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/config/helper/configgetter.hpp>
#include <iomanip>

using std::shared_ptr;
using config::ConfigGetter;
using document::DocumenttypesConfig;
using config::FileSpec;
using vespalib::string;
using namespace document;
using namespace storage;
using namespace storage::api;
using namespace storage::lib;
using namespace std::literals::string_literals;
using document::test::makeDocumentBucket;

namespace storage {

namespace distributor {

class PutOperationTest : public CppUnit::TestFixture,
                         public DistributorTestUtil {
    CPPUNIT_TEST_SUITE(PutOperationTest);
    CPPUNIT_TEST(testSimple);
    CPPUNIT_TEST(testBucketDatabaseGetsSpecialEntryWhenCreateBucketSent);
    CPPUNIT_TEST(testSendInlineSplitBeforePutIfBucketTooLarge);
    CPPUNIT_TEST(testDoNotSendInlineSplitIfNotConfigured);
    CPPUNIT_TEST(testNodeRemovedOnReply);
    CPPUNIT_TEST(testDoNotSendCreateBucketIfAlreadyPending);
    CPPUNIT_TEST(testMultipleCopies);
    CPPUNIT_TEST(testMultipleCopiesEarlyReturnPrimaryNotRequired);
    CPPUNIT_TEST(testMultipleCopiesEarlyReturnPrimaryRequired);
    CPPUNIT_TEST(testMultipleCopiesEarlyReturnPrimaryRequiredNotDone);
    CPPUNIT_TEST_IGNORED(testDoNotRevertOnFailureAfterEarlyReturn);
    CPPUNIT_TEST(testStorageFailed);
    CPPUNIT_TEST(testRevertSuccessfulCopiesWhenOneFails);
    CPPUNIT_TEST(testNoRevertIfRevertDisabled);
    CPPUNIT_TEST(testNoStorageNodes);
    CPPUNIT_TEST(testUpdateCorrectBucketOnRemappedPut);
    CPPUNIT_TEST(testTargetNodes);
    CPPUNIT_TEST(testDoNotResurrectDownedNodesInBucketDB);
    CPPUNIT_TEST(sendToRetiredNodesIfNoUpNodesAvailable);
    CPPUNIT_TEST(replicaImplicitlyActivatedWhenActivationIsNotDisabled);
    CPPUNIT_TEST(replicaNotImplicitlyActivatedWhenActivationIsDisabled);
    CPPUNIT_TEST_SUITE_END();

    std::shared_ptr<const DocumentTypeRepo> _repo;
    const DocumentType* _html_type;
    std::unique_ptr<Operation> op;

protected:
    void testSimple();
    void testBucketDatabaseGetsSpecialEntryWhenCreateBucketSent();
    void testSendInlineSplitBeforePutIfBucketTooLarge();
    void testDoNotSendInlineSplitIfNotConfigured();
    void testNodeRemovedOnReply();
    void testDoNotSendCreateBucketIfAlreadyPending();
    void testStorageFailed();
    void testNoReply();
    void testMultipleCopies();
    void testRevertSuccessfulCopiesWhenOneFails();
    void testNoRevertIfRevertDisabled();
    void testInconsistentChecksum();
    void testNoStorageNodes();
    void testMultipleCopiesEarlyReturnPrimaryNotRequired();
    void testMultipleCopiesEarlyReturnPrimaryRequired();
    void testMultipleCopiesEarlyReturnPrimaryRequiredNotDone();
    void testDoNotRevertOnFailureAfterEarlyReturn();
    void testUpdateCorrectBucketOnRemappedPut();
    void testBucketNotFound();
    void testTargetNodes();
    void testDoNotResurrectDownedNodesInBucketDB();
    void sendToRetiredNodesIfNoUpNodesAvailable();
    void replicaImplicitlyActivatedWhenActivationIsNotDisabled();
    void replicaNotImplicitlyActivatedWhenActivationIsDisabled();

    void doTestCreationWithBucketActivationDisabled(bool disabled);

public:
    void setUp() override {
        _repo.reset(
                new DocumentTypeRepo(*ConfigGetter<DocumenttypesConfig>
                                     ::getConfig("config-doctypes",
                                                 FileSpec(TEST_PATH("config-doctypes.cfg")))));
        _html_type = _repo->getDocumentType("text/html");
        createLinks();
    };

    void tearDown() override {
        close();
    }

    document::BucketId createAndSendSampleDocument(uint32_t timeout);
    std::string getNodes(const std::string& infoString);

    void sendReply(int idx = -1,
                      api::ReturnCode::Result result
                      = api::ReturnCode::OK,
                      api::BucketInfo info = api::BucketInfo(1,2,3,4,5))
    {
        CPPUNIT_ASSERT(!_sender.commands.empty());
        if (idx == -1) {
            idx = _sender.commands.size() - 1;
        } else if (static_cast<size_t>(idx) >= _sender.commands.size()) {
            throw std::logic_error("Specified message index is greater "
                                   "than number of received messages");
        }

        std::shared_ptr<api::StorageCommand> msg =  _sender.commands[idx];
        api::StorageReply::SP reply(msg->makeReply().release());
        dynamic_cast<api::BucketInfoReply*>(reply.get())->setBucketInfo(info);
        reply->setResult(result);

        op->receive(_sender, reply);
    }

    void sendPut(std::shared_ptr<api::PutCommand> msg) {
        op.reset(new PutOperation(getExternalOperationHandler(),
                                  getDistributorBucketSpace(),
                                  msg,
                                  getDistributor().getMetrics().
                                  puts[msg->getLoadType()]));
        op->start(_sender, framework::MilliSecTime(0));
    }

    Document::SP createDummyDocument(const char* ns,
                                                    const char* id) const
    {
        return Document::SP(
                new Document(*_html_type,
                             DocumentId(DocIdString(ns, id))));

    }

    std::shared_ptr<api::PutCommand> createPut(
            const Document::SP doc) const
    {
        return std::shared_ptr<api::PutCommand>(
                new api::PutCommand(makeDocumentBucket(document::BucketId(0)), doc, 100));
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(PutOperationTest);

document::BucketId
PutOperationTest::createAndSendSampleDocument(uint32_t timeout) {
    Document::SP
        doc(new Document(*_html_type,
                         DocumentId(DocIdString("test", "test"))));

    document::BucketId id = getExternalOperationHandler().getBucketId(doc->getId());
    addIdealNodes(id);

    std::shared_ptr<api::PutCommand> msg(
            new api::PutCommand(makeDocumentBucket(document::BucketId(0)),
                                doc,
                                0));
    msg->setTimestamp(100);
    msg->setPriority(128);
    msg->setTimeout(timeout);
    sendPut(msg);
    return id;
}

namespace {

typedef int Redundancy;
typedef int NodeCount;
typedef uint32_t ReturnAfter;
typedef bool RequirePrimaryWritten;

}

void
PutOperationTest::testSimple()
{
    setupDistributor(1, 1, "storage:1 distributor:1");
    createAndSendSampleDocument(180);

    CPPUNIT_ASSERT_EQUAL(std::string("Put(BucketId(0x4000000000008b13), "
                                     "doc:test:test, timestamp 100, size 33) => 0"),
                         _sender.getCommands(true, true));

    sendReply();

    CPPUNIT_ASSERT_EQUAL(std::string("PutReply(doc:test:test, BucketId(0x0000000000000000), "
                                     "timestamp 100) ReturnCode(NONE)"),
                         _sender.getLastReply());
}

void
PutOperationTest::testBucketDatabaseGetsSpecialEntryWhenCreateBucketSent()
{
    setupDistributor(2, 1, "storage:1 distributor:1");

    Document::SP doc(createDummyDocument("test", "test"));
    document::BucketId bucketId(getExternalOperationHandler().getBucketId(doc->getId()));
    sendPut(createPut(doc));

    // Database updated before CreateBucket is sent
    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000008b13) : "
                        "node(idx=0,crc=0x1,docs=0/0,bytes=0/0,trusted=true,active=true,ready=false)"),
            dumpBucket(getExternalOperationHandler().getBucketId(doc->getId())));

    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 0,Put => 0"),
                         _sender.getCommands(true));
}

void
PutOperationTest::testSendInlineSplitBeforePutIfBucketTooLarge()
{
    setupDistributor(1, 1, "storage:1 distributor:1");
    getConfig().setSplitCount(1024);
    getConfig().setSplitSize(1000000);

    addNodesToBucketDB(document::BucketId(0x4000000000002a52), "0=10000/10000/10000/t");

    sendPut(createPut(createDummyDocument("test", "uri")));

    CPPUNIT_ASSERT_EQUAL(
            std::string("SplitBucketCommand(BucketId(0x4000000000002a52)Max doc count: "
                        "1024, Max total doc size: 1000000) Reasons to start: "
                        "[Splitting bucket because its maximum size (10000 b, 10000 docs, 10000 meta, 10000 b total) is "
                        "higher than the configured limit of (1000000, 1024)] => 0,"
                        "Put(BucketId(0x4000000000002a52), doc:test:uri, timestamp 100, "
                        "size 32) => 0"),
            _sender.getCommands(true, true));
}

void
PutOperationTest::testDoNotSendInlineSplitIfNotConfigured()
{
    setupDistributor(1, 1, "storage:1 distributor:1");
    getConfig().setSplitCount(1024);
    getConfig().setDoInlineSplit(false);

    addNodesToBucketDB(document::BucketId(0x4000000000002a52), "0=10000/10000/10000/t");

    sendPut(createPut(createDummyDocument("test", "uri")));

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                        "Put(BucketId(0x4000000000002a52), doc:test:uri, timestamp 100, "
                        "size 32) => 0"),
            _sender.getCommands(true, true));
}

void
PutOperationTest::testNodeRemovedOnReply()
{
    setupDistributor(2, 2, "storage:2 distributor:1");
    createAndSendSampleDocument(180);

    CPPUNIT_ASSERT_EQUAL(
            std::string("Put(BucketId(0x4000000000008b13), "
                        "doc:test:test, timestamp 100, size 33) => 1,"
                        "Put(BucketId(0x4000000000008b13), "
                        "doc:test:test, timestamp 100, size 33) => 0"),
            _sender.getCommands(true, true));

    getExternalOperationHandler().removeNodeFromDB(makeDocumentBucket(document::BucketId(16, 0x8b13)), 0);

    sendReply(0);
    sendReply(1);

    CPPUNIT_ASSERT_EQUAL(std::string(
                                 "PutReply(doc:test:test, BucketId(0x0000000000000000), "
                                 "timestamp 100) ReturnCode(BUCKET_DELETED, "
                                 "Bucket(BucketSpace(0x0000000000000001), BucketId(0x4000000000008b13)) was deleted from nodes [0] "
                                 "after message was sent but before it was done. "
                                 "Sent to [1,0])"),
                         _sender.getLastReply());
}

void
PutOperationTest::testStorageFailed()
{
    setupDistributor(2, 1, "storage:1 distributor:1");

    createAndSendSampleDocument(180);

    sendReply(-1, api::ReturnCode::INTERNAL_FAILURE);

    CPPUNIT_ASSERT_EQUAL(std::string("PutReply(doc:test:test, BucketId(0x0000000000000000), "
                                     "timestamp 100) ReturnCode(INTERNAL_FAILURE)"),
                         _sender.getLastReply(true));
}

void
PutOperationTest::testMultipleCopies()
{
    setupDistributor(3, 4, "storage:4 distributor:1");

    Document::SP doc(createDummyDocument("test", "test"));
    sendPut(createPut(doc));

    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 3,Create bucket => 1,"
                                     "Create bucket => 0,Put => 3,Put => 1,Put => 0"),
                         _sender.getCommands(true));

    for (uint32_t i = 0;  i < 6; i++) {
        sendReply(i);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("PutReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 100) ReturnCode(NONE)"),
            _sender.getLastReply(true));

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000008b13) : "
                        "node(idx=3,crc=0x1,docs=2/4,bytes=3/5,trusted=true,active=false,ready=false), "
                        "node(idx=1,crc=0x1,docs=2/4,bytes=3/5,trusted=true,active=false,ready=false), "
                        "node(idx=0,crc=0x1,docs=2/4,bytes=3/5,trusted=true,active=false,ready=false)"),
            dumpBucket(getExternalOperationHandler().getBucketId(doc->getId())));
}


void
PutOperationTest::testMultipleCopiesEarlyReturnPrimaryRequired()
{
    setupDistributor(3, 4, "storage:4 distributor:1", 2, true);

    sendPut(createPut(createDummyDocument("test", "test")));

    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 3,Create bucket => 1,"
                                     "Create bucket => 0,Put => 3,Put => 1,Put => 0"),
                         _sender.getCommands(true));

    // Reply to 2 CreateBucket, including primary
    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(i);
    }
    // Reply to 2 puts, including primary
    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(3 + i);
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "PutReply(doc:test:test, BucketId(0x0000000000000000), "
                    "timestamp 100) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
PutOperationTest::testMultipleCopiesEarlyReturnPrimaryNotRequired()
{
    setupDistributor(3, 4, "storage:4 distributor:1", 2, false);

    sendPut(createPut(createDummyDocument("test", "test")));

    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 3,Create bucket => 1,"
                                     "Create bucket => 0,Put => 3,Put => 1,Put => 0"),
                         _sender.getCommands(true));

    // Reply only to 2 nodes (but not the primary)
    for (uint32_t i = 1;  i < 3; i++) {
        sendReply(i); // CreateBucket
    }
    for (uint32_t i = 1;  i < 3; i++) {
        sendReply(3 + i); // Put
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string("PutReply(doc:test:test, BucketId(0x0000000000000000), "
                        "timestamp 100) ReturnCode(NONE)"),
            _sender.getLastReply());
}

void
PutOperationTest::testMultipleCopiesEarlyReturnPrimaryRequiredNotDone()
{
    setupDistributor(3, 4, "storage:4 distributor:1", 2, true);

    sendPut(createPut(createDummyDocument("test", "test")));

    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 3,Create bucket => 1,"
                                     "Create bucket => 0,Put => 3,Put => 1,Put => 0"),
                         _sender.getCommands(true));

    // Reply only to 2 nodes (but not the primary)
    sendReply(1);
    sendReply(2);
    sendReply(4);
    sendReply(5);

    CPPUNIT_ASSERT_EQUAL(0, (int)_sender.replies.size());
}

void
PutOperationTest::testDoNotRevertOnFailureAfterEarlyReturn()
{
    setupDistributor(Redundancy(3),NodeCount(4), "storage:4 distributor:1",
                     ReturnAfter(2), RequirePrimaryWritten(false));

    sendPut(createPut(createDummyDocument("test", "test")));

    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 3,Create bucket => 1,"
                                     "Create bucket => 0,Put => 3,Put => 1,Put => 0"),
                         _sender.getCommands(true));

    for (uint32_t i = 0;  i < 3; i++) {
        sendReply(i); // CreateBucket
    }
    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(3 + i); // Put
    }

    CPPUNIT_ASSERT_EQUAL(
            std::string(
                    "PutReply(doc:test:test, BucketId(0x0000000000000000), "
                    "timestamp 100) ReturnCode(NONE)"),
            _sender.getLastReply());

    sendReply(5, api::ReturnCode::INTERNAL_FAILURE);
    // Should not be any revert commands sent
    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 3,Create bucket => 1,"
                                     "Create bucket => 0,Put => 3,Put => 1,Put => 0"),
                         _sender.getCommands(true));
}

void
PutOperationTest::testRevertSuccessfulCopiesWhenOneFails()
{
    setupDistributor(3, 4, "storage:4 distributor:1");

    createAndSendSampleDocument(180);

    CPPUNIT_ASSERT_EQUAL(std::string("Put => 3,Put => 1,Put => 0"),
                         _sender.getCommands(true));

    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(i);
    }

    sendReply(2, api::ReturnCode::INTERNAL_FAILURE);

    CPPUNIT_ASSERT_EQUAL(std::string("PutReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), timestamp 100) "
                                     "ReturnCode(INTERNAL_FAILURE)"),
                         _sender.getLastReply(true));

    CPPUNIT_ASSERT_EQUAL(std::string("Revert => 3,Revert => 1"),
                         _sender.getCommands(true, false, 3));
}

void
PutOperationTest::testNoRevertIfRevertDisabled()
{
    close();
    getDirConfig().getConfig("stor-distributormanager")
                  .set("enable_revert", "false");
    setUp();
    setupDistributor(3, 4, "storage:4 distributor:1");

    createAndSendSampleDocument(180);

    CPPUNIT_ASSERT_EQUAL(std::string("Put => 3,Put => 1,Put => 0"),
                         _sender.getCommands(true));

    for (uint32_t i = 0;  i < 2; i++) {
        sendReply(i);
    }

    sendReply(2, api::ReturnCode::INTERNAL_FAILURE);

    CPPUNIT_ASSERT_EQUAL(std::string("PutReply(doc:test:test, "
                                     "BucketId(0x0000000000000000), timestamp 100) "
                                     "ReturnCode(INTERNAL_FAILURE)"),
                         _sender.getLastReply(true));

    CPPUNIT_ASSERT_EQUAL(std::string(""),
                         _sender.getCommands(true, false, 3));
}

void
PutOperationTest::testDoNotSendCreateBucketIfAlreadyPending()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    Document::SP doc(createDummyDocument("test", "uri"));
    sendPut(createPut(doc));

    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 1,Create bucket => 0,"
                                     "Put => 1,Put => 0"),
                         _sender.getCommands(true));

    // Manually shove sent messages into pending message tracker, since
    // this isn't done automatically.
    for (size_t i = 0; i < _sender.commands.size(); ++i) {
        getExternalOperationHandler().getDistributor().getPendingMessageTracker()
            .insert(_sender.commands[i]);
    }

    sendPut(createPut(doc));

    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 1,Create bucket => 0,"
                                     "Put => 1,Put => 0,"
                                     "Put => 1,Put => 0"),
                         _sender.getCommands(true));
}

void
PutOperationTest::testNoStorageNodes()
{
    setupDistributor(2, 1, "storage:0 distributor:1");
    createAndSendSampleDocument(180);
    CPPUNIT_ASSERT_EQUAL(std::string("PutReply(doc:test:test, BucketId(0x0000000000000000), "
                                     "timestamp 100) ReturnCode(NOT_CONNECTED, "
                                     "Can't store document: No storage nodes available)"),
                         _sender.getLastReply(true));
}

void
PutOperationTest::testUpdateCorrectBucketOnRemappedPut()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    Document::SP doc(new Document(*_html_type, DocumentId(
                            UserDocIdString("userdoc:test:13:uri"))));

    addNodesToBucketDB(document::BucketId(16,13), "0=0,1=0");

    sendPut(createPut(doc));

    CPPUNIT_ASSERT_EQUAL(std::string("Put => 0,Put => 1"),
                         _sender.getCommands(true));

    {
        std::shared_ptr<api::StorageCommand> msg2  = _sender.commands[0];
        std::shared_ptr<api::StorageReply> reply(msg2->makeReply().release());
        PutReply* sreply = (PutReply*)reply.get();
        sreply->remapBucketId(document::BucketId(17, 13));
        sreply->setBucketInfo(api::BucketInfo(1,2,3,4,5));
        op->receive(_sender, reply);
    }

    sendReply(1);

    CPPUNIT_ASSERT_EQUAL(std::string("PutReply(userdoc:test:13:uri, "
                                     "BucketId(0x0000000000000000), "
                                     "timestamp 100) ReturnCode(NONE)"),
                         _sender.getLastReply());

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x440000000000000d) : "
                        "node(idx=0,crc=0x1,docs=2/4,bytes=3/5,trusted=true,active=false,ready=false)"),
            dumpBucket(document::BucketId(17, 13)));
}

BucketInfo
parseBucketInfoString(const std::string& nodeList) {
    vespalib::StringTokenizer tokenizer(nodeList, ",");

    BucketInfo entry;
    for (uint32_t i = 0; i < tokenizer.size(); i++) {
        vespalib::StringTokenizer tokenizer2(tokenizer[i], "-");
        int node = atoi(tokenizer2[0].c_str());
        int size = atoi(tokenizer2[1].c_str());
        bool trusted = (tokenizer2[2] == "true");

        entry.addNode(BucketCopy(0,
                                 node,
                                 api::BucketInfo(size, size * 1000, size * 2000))
                      .setTrusted(trusted),
                      toVector<uint16_t>(0));
    }

    return entry;
}

std::string
PutOperationTest::getNodes(const std::string& infoString) {
    Document::SP doc(createDummyDocument("test", "uri"));
    document::BucketId bid(getExternalOperationHandler().getBucketId(doc->getId()));

    BucketInfo entry = parseBucketInfoString(infoString);

    std::ostringstream ost;

    std::vector<uint16_t> targetNodes;
    std::vector<uint16_t> createNodes;
    PutOperation::getTargetNodes(getExternalOperationHandler().getIdealNodes(makeDocumentBucket(bid)),
                                 targetNodes, createNodes, entry, 2);

    ost << "target( ";
    for (uint32_t i = 0; i < targetNodes.size(); i++) {
        ost << targetNodes[i] << " ";
    }
    ost << ") create( ";
    for (uint32_t i = 0; i < createNodes.size(); i++) {
        ost << createNodes[i] << " ";
    }
    ost << ")";

    return ost.str();
}

void
PutOperationTest::testTargetNodes()
{
    setupDistributor(2, 6, "storage:6 distributor:1");

    // Ideal state of bucket is 1,3.
    CPPUNIT_ASSERT_EQUAL(std::string("target( 1 3 ) create( 1 3 )"), getNodes(""));
    CPPUNIT_ASSERT_EQUAL(std::string("target( 1 3 ) create( 3 )"),   getNodes("1-1-true"));
    CPPUNIT_ASSERT_EQUAL(std::string("target( 1 3 ) create( 3 )"),   getNodes("1-1-false"));
    CPPUNIT_ASSERT_EQUAL(std::string("target( 3 4 5 ) create( )"),   getNodes("3-1-true,4-1-true,5-1-true"));
    CPPUNIT_ASSERT_EQUAL(std::string("target( 3 4 ) create( )"),     getNodes("3-2-true,4-2-true,5-1-false"));
    CPPUNIT_ASSERT_EQUAL(std::string("target( 1 3 4 ) create( )"),   getNodes("3-2-true,4-2-true,1-1-false"));
    CPPUNIT_ASSERT_EQUAL(std::string("target( 4 5 ) create( )"),     getNodes("4-2-false,5-1-false"));
    CPPUNIT_ASSERT_EQUAL(std::string("target( 1 4 ) create( 1 )"),   getNodes("4-1-true"));
}

void
PutOperationTest::testDoNotResurrectDownedNodesInBucketDB()
{
    setupDistributor(2, 2, "storage:2 distributor:1");

    Document::SP doc(createDummyDocument("test", "uri"));
    document::BucketId bId = getExternalOperationHandler().getBucketId(doc->getId());

    addNodesToBucketDB(bId, "0=1/2/3/t,1=1/2/3/t");

    sendPut(createPut(doc));

    CPPUNIT_ASSERT_EQUAL(std::string("Put => 1,Put => 0"),
                         _sender.getCommands(true));

    enableDistributorClusterState("distributor:1 storage:2 .1.s:d");
    addNodesToBucketDB(bId, "0=1/2/3/t"); // This will actually remove node #1.

    sendReply(0, api::ReturnCode::OK, api::BucketInfo(9,9,9));
    sendReply(1, api::ReturnCode::OK, api::BucketInfo(5,6,7));

    CPPUNIT_ASSERT_EQUAL(
            std::string("BucketId(0x4000000000002a52) : "
                        "node(idx=0,crc=0x5,docs=6/6,bytes=7/7,trusted=true,active=false,ready=false)"),
            dumpBucket(getExternalOperationHandler().getBucketId(doc->getId())));
}

void
PutOperationTest::sendToRetiredNodesIfNoUpNodesAvailable()
{
    setupDistributor(Redundancy(2), NodeCount(2),
                     "distributor:1 storage:2 .0.s:r .1.s:r");
    Document::SP doc(createDummyDocument("test", "uri"));
    document::BucketId bucket(
            getExternalOperationHandler().getBucketId(doc->getId()));
    addNodesToBucketDB(bucket, "0=1/2/3/t,1=1/2/3/t");

    sendPut(createPut(doc));

    CPPUNIT_ASSERT_EQUAL("Put => 0,Put => 1"s,
                         _sender.getCommands(true));
}

void
PutOperationTest::doTestCreationWithBucketActivationDisabled(bool disabled)
{
    setupDistributor(Redundancy(2), NodeCount(2), "distributor:1 storage:1");
    disableBucketActivationInConfig(disabled);

    Document::SP doc(createDummyDocument("test", "uri"));
    sendPut(createPut(doc));

    CPPUNIT_ASSERT_EQUAL(std::string("Create bucket => 0,Put => 0"),
                         _sender.getCommands(true));
    auto cmd = _sender.commands[0];
    auto createCmd = std::dynamic_pointer_cast<api::CreateBucketCommand>(cmd);
    CPPUNIT_ASSERT(createCmd.get() != nullptr);
    // There's only 1 content node, so if activation were not disabled, it
    // should always be activated.
    CPPUNIT_ASSERT_EQUAL(!disabled, createCmd->getActive());
}

void
PutOperationTest::replicaImplicitlyActivatedWhenActivationIsNotDisabled()
{
    doTestCreationWithBucketActivationDisabled(false);
}

void
PutOperationTest::replicaNotImplicitlyActivatedWhenActivationIsDisabled()
{
    doTestCreationWithBucketActivationDisabled(true);
}

}

}
