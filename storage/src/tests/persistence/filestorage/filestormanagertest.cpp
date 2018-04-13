// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/testhelper.h>
#include <tests/common/storagelinktest.h>
#include <tests/common/teststorageapp.h>
#include <tests/persistence/filestorage/forwardingmessagesender.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/storage/storageserver/statemanager.h>
#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/persistence/persistencethread.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/persistence/filestorage/modifiedbucketchecker.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/select/parser.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/storageapi/message/batch.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/fastos/file.h>

#include <vespa/log/log.h>
LOG_SETUP(".filestormanagertest");

using std::unique_ptr;
using document::Document;
using namespace storage::api;
using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;

#define ASSERT_SINGLE_REPLY(replytype, reply, link, time) \
reply = 0; \
try{ \
    link.waitForMessages(1, time); \
    CPPUNIT_ASSERT_EQUAL((size_t)1, link.getNumReplies()); \
    reply = dynamic_cast<replytype*>(link.getReply(0).get()); \
    if (reply == 0) { \
        CPPUNIT_FAIL("Got reply of unexpected type: " \
                     + link.getReply(0)->getType().toString()); \
    } \
} catch (vespalib::Exception& e) { \
    reply = 0; \
    CPPUNIT_FAIL("Failed to find single reply in time"); \
}

namespace storage {

namespace {

spi::LoadType defaultLoadType(0, "default");

struct TestFileStorComponents;

}

struct FileStorManagerTest : public CppUnit::TestFixture {
    enum {LONG_WAITTIME=60};
    unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<vdstestlib::DirConfig> config;
    std::unique_ptr<vdstestlib::DirConfig> config2;
    std::unique_ptr<vdstestlib::DirConfig> smallConfig;
    const uint32_t _waitTime;
    const document::DocumentType* _testdoctype1;

    FileStorManagerTest() : _node(), _waitTime(LONG_WAITTIME) {}

    void setUp() override;
    void tearDown() override;

    void testPut();
    void testHeaderOnlyPut();
    void testFlush();
    void testRemapSplit();
    void testHandlerPriority();
    void testHandlerMulti();
    void testHandlerTimeout();
    void testHandlerPause();
    void testHandlerPausedMultiThread();
    void testPriority();
    void testSplit1();
    void testSplitSingleGroup();
    void testSplitEmptyTargetWithRemappedOps();
    void testNotifyOnSplitSourceOwnershipChanged();
    void testJoin();
    void testVisiting();
    void testRemoveLocation();
    void testDeleteBucket();
    void testDeleteBucketRejectOutdatedBucketInfo();
    void testDeleteBucketWithInvalidBucketInfo();
    void testNoTimestamps();
    void testEqualTimestamps();
    void testGetIter();
    void testSetBucketActiveState();
    void testNotifyOwnerDistributorOnOutdatedSetBucketState();
    void testGetBucketDiffImplicitCreateBucket();
    void testMergeBucketImplicitCreateBucket();
    void testNewlyCreatedBucketIsReady();
    void testCreateBucketSetsActiveFlagInDatabaseAndReply();
    void testStateChange();
    void testRepairNotifiesDistributorOnChange();
    void testDiskMove();
    void put_command_size_is_added_to_metric();
    void update_command_size_is_added_to_metric();
    void remove_command_size_is_added_to_metric();
    void get_command_size_is_added_to_metric();

    CPPUNIT_TEST_SUITE(FileStorManagerTest);
    CPPUNIT_TEST(testPut);
    CPPUNIT_TEST(testHeaderOnlyPut);
    CPPUNIT_TEST(testFlush);
    CPPUNIT_TEST(testRemapSplit);
    CPPUNIT_TEST(testHandlerPriority);
    CPPUNIT_TEST(testHandlerMulti);
    CPPUNIT_TEST(testHandlerTimeout);
    CPPUNIT_TEST(testHandlerPause);
    CPPUNIT_TEST(testHandlerPausedMultiThread);
    CPPUNIT_TEST(testPriority);
    CPPUNIT_TEST(testSplit1);
    CPPUNIT_TEST(testSplitSingleGroup);
    CPPUNIT_TEST(testSplitEmptyTargetWithRemappedOps);
    CPPUNIT_TEST(testNotifyOnSplitSourceOwnershipChanged);
    CPPUNIT_TEST(testJoin);
    CPPUNIT_TEST(testVisiting);
    CPPUNIT_TEST(testRemoveLocation);
    CPPUNIT_TEST(testDeleteBucket);
    CPPUNIT_TEST(testDeleteBucketRejectOutdatedBucketInfo);
    CPPUNIT_TEST(testDeleteBucketWithInvalidBucketInfo);
    CPPUNIT_TEST(testNoTimestamps);
    CPPUNIT_TEST(testEqualTimestamps);
    CPPUNIT_TEST(testGetIter);
    CPPUNIT_TEST(testSetBucketActiveState);
    CPPUNIT_TEST(testNotifyOwnerDistributorOnOutdatedSetBucketState);
    CPPUNIT_TEST(testGetBucketDiffImplicitCreateBucket);
    CPPUNIT_TEST(testMergeBucketImplicitCreateBucket);
    CPPUNIT_TEST(testNewlyCreatedBucketIsReady);
    CPPUNIT_TEST(testCreateBucketSetsActiveFlagInDatabaseAndReply);
    CPPUNIT_TEST(testStateChange);
    CPPUNIT_TEST(testRepairNotifiesDistributorOnChange);
    CPPUNIT_TEST(testDiskMove);
    CPPUNIT_TEST(put_command_size_is_added_to_metric);
    CPPUNIT_TEST(update_command_size_is_added_to_metric);
    CPPUNIT_TEST(remove_command_size_is_added_to_metric);
    CPPUNIT_TEST(get_command_size_is_added_to_metric);
    CPPUNIT_TEST_SUITE_END();

    void createBucket(document::BucketId bid, uint16_t disk)
    {
        spi::Context context(defaultLoadType, spi::Priority(0), spi::Trace::TraceLevel(0));
        _node->getPersistenceProvider().createBucket(makeSpiBucket(bid, spi::PartitionId(disk)), context);

        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(bid, "foo", StorBucketDatabase::CREATE_IF_NONEXISTING));
        entry->disk = disk;
        entry->info = api::BucketInfo(0, 0, 0, 0, 0, true, false);
        entry.write();
    }

    document::Document::UP createDocument(const std::string& content, const std::string& id)
    {
        return _node->getTestDocMan().createDocument(content, id);
    }

    bool ownsBucket(uint16_t distributorIndex,
                    const document::BucketId& bucket) const
    {
        auto clusterStateBundle = _node->getStateUpdater().getClusterStateBundle();
        const auto &clusterState = *clusterStateBundle->getBaselineClusterState();
        uint16_t distributor(
                _node->getDistribution()->getIdealDistributorNode(
                        clusterState, bucket));
        return distributor == distributorIndex;
    }
    
    document::BucketId getFirstBucketNotOwnedByDistributor(uint16_t distributor) {
        for (int i = 0; i < 1000; ++i) {
            if (!ownsBucket(distributor, document::BucketId(16, i))) {
                return document::BucketId(16, i);
            }
        }
        return document::BucketId(0);
    }

    spi::dummy::DummyPersistence& getDummyPersistence() {
        return static_cast<spi::dummy::DummyPersistence&>
            (_node->getPersistenceProvider());
    }

    void setClusterState(const std::string& state) {
        _node->getStateUpdater().setClusterState(
                lib::ClusterState::CSP(
                        new lib::ClusterState(state)));
    }

    void setupDisks(uint32_t diskCount) {
        std::string rootOfRoot = "filestormanagertest";
        config.reset(new vdstestlib::DirConfig(getStandardConfig(true, rootOfRoot)));

        config2.reset(new vdstestlib::DirConfig(*config));
        config2->getConfig("stor-server").set("root_folder", rootOfRoot + "-vdsroot.2");
        config2->getConfig("stor-devices").set("root_folder", rootOfRoot + "-vdsroot.2");
        config2->getConfig("stor-server").set("node_index", "1");

        smallConfig.reset(new vdstestlib::DirConfig(*config));
        vdstestlib::DirConfig::Config& c(
                smallConfig->getConfig("stor-filestor", true));
        c.set("initial_index_read", "128");
        c.set("use_direct_io", "false");
        c.set("maximum_gap_to_read_through", "64");

        assert(system(vespalib::make_string("rm -rf %s", getRootFolder(*config).c_str()).c_str()) == 0);
        assert(system(vespalib::make_string("rm -rf %s", getRootFolder(*config2).c_str()).c_str()) == 0);
        assert(system(vespalib::make_string("mkdir -p %s/disks/d0", getRootFolder(*config).c_str()).c_str()) == 0);
        assert(system(vespalib::make_string("mkdir -p %s/disks/d0", getRootFolder(*config2).c_str()).c_str()) == 0);
        try {
            _node.reset(new TestServiceLayerApp(DiskCount(diskCount), NodeIndex(0),
                                                config->getConfigId()));
            _node->setupDummyPersistence();
        } catch (config::InvalidConfigException& e) {
            fprintf(stderr, "%s\n", e.what());
        }
        _testdoctype1 = _node->getTypeRepo()->getDocumentType("testdoctype1");
    }

    void putDoc(DummyStorageLink& top,
                FileStorHandler& filestorHandler,
                const document::BucketId& bucket,
                uint32_t docNum);

    template <typename Metric>
    void assert_request_size_set(TestFileStorComponents& c,
                                 std::shared_ptr<api::StorageMessage> cmd,
                                 const Metric& metric);

    auto& thread_metrics_of(FileStorManager& manager) {
        return manager._metrics->disks[0]->threads[0];
    }
};

CPPUNIT_TEST_SUITE_REGISTRATION(FileStorManagerTest);

std::string findFile(const std::string& path, const std::string& file) {
    FastOS_DirectoryScan dirScan(path.c_str());
    while (dirScan.ReadNext()) {
        if (dirScan.GetName()[0] == '.') {
            // Ignore current and parent dir.. Ignores hidden files too, but
            // that doesn't matter as we're not trying to find them.
            continue;
        }
        std::string filename(dirScan.GetName());
        if (dirScan.IsDirectory()) {
            std::string result = findFile(path + "/" + filename, file);
            if (result != "") {
                return result;
            }
        }
        if (filename == file) {
            return path + "/" + filename;
        }
    }
    return "";
}

bool fileExistsWithin(const std::string& path, const std::string& file) {
    return !(findFile(path, file) == "");
}

std::unique_ptr<DiskThread> createThread(vdstestlib::DirConfig& config,
                                       TestServiceLayerApp& node,
                                       spi::PersistenceProvider& provider,
                                       FileStorHandler& filestorHandler,
                                       FileStorThreadMetrics& metrics,
                                       uint16_t deviceIndex)
{
    (void) config;
    std::unique_ptr<DiskThread> disk;
    disk.reset(new PersistenceThread(
            node.getComponentRegister(), config.getConfigId(), provider,
            filestorHandler, metrics,
            deviceIndex));
    return disk;
}

namespace {

struct TestFileStorComponents
{
private:
    TestName _testName;
public:
    DummyStorageLink top;
    FileStorManager* manager;

    TestFileStorComponents(FileStorManagerTest& test, const char* testName)
        : _testName(testName),
          manager(new FileStorManager(test.config->getConfigId(),
                                      test._node->getPartitions(),
                                      test._node->getPersistenceProvider(),
                                      test._node->getComponentRegister()))
    {
        top.push_back(unique_ptr<StorageLink>(manager));
        top.open();
    }
};

}

void
FileStorManagerTest::setUp()
{
    setupDisks(1);
}

void
FileStorManagerTest::tearDown()
{
    _node.reset(0);
}

void
FileStorManagerTest::testHeaderOnlyPut()
{
    TestName testName("testHeaderOnlyPut");
    // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager =
            new FileStorManager(config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);
    // Creating a document to test with
    Document::SP doc(createDocument(
                "some content", "userdoc:crawler:4000:foo").release());

    document::BucketId bid(16, 4000);

    createBucket(bid, 0);

    // Putting it
    {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 105));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        CPPUNIT_ASSERT_EQUAL(1, (int)reply->getBucketInfo().getDocumentCount());
    }
    doc->setValue(doc->getField("headerval"), document::IntFieldValue(42));
    // Putting it again, this time with header only
    {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 124));
        cmd->setUpdateTimestamp(105);
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode::OK, reply->getResult().getResult());
    }
    // Getting it
    {
        std::shared_ptr<api::GetCommand> cmd(new api::GetCommand(
                                                       makeDocumentBucket(bid), doc->getId(), "[all]"));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::GetReply> reply2(
                std::dynamic_pointer_cast<api::GetReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply2.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply2->getResult());
        CPPUNIT_ASSERT_EQUAL(doc->getId().toString(),
                             reply2->getDocumentId().toString());
            // Ensure partial update was done, but other things are equal
        document::FieldValue::UP value(
                reply2->getDocument()->getValue(doc->getField("headerval")));
        CPPUNIT_ASSERT(value.get());
        CPPUNIT_ASSERT_EQUAL(42, dynamic_cast<document::IntFieldValue&>(
                                    *value).getAsInt());
        reply2->getDocument()->remove("headerval");
        doc->remove("headerval");
        CPPUNIT_ASSERT_EQUAL(*doc, *reply2->getDocument());
    }
}

void
FileStorManagerTest::testPut()
{
    TestName testName("testPut");
    // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager =
            new FileStorManager(config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);
    // Creating a document to test with
    Document::SP doc(createDocument(
                "some content", "userdoc:crawler:4000:foo").release());

    document::BucketId bid(16, 4000);

    createBucket(bid, 0);

    // Putting it
    {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 105));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        CPPUNIT_ASSERT_EQUAL(1, (int)reply->getBucketInfo().getDocumentCount());
    }
}

void
FileStorManagerTest::testDiskMove()
{
    setupDisks(2);

    // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager =
            new FileStorManager(config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);
    // Creating a document to test with
    Document::SP doc(createDocument(
                "some content", "userdoc:crawler:4000:foo").release());

    document::BucketId bid(16, 4000);

    createBucket(bid, 0);

    // Putting it
    {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 105));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        CPPUNIT_ASSERT_EQUAL(1, (int)reply->getBucketInfo().getDocumentCount());
    }

    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(bid, "foo"));

        CPPUNIT_ASSERT_EQUAL(0, (int)entry->disk);
        CPPUNIT_ASSERT_EQUAL(
                vespalib::string(
                        "BucketInfo(crc 0x28cc441f, docCount 1, totDocSize 114, "
                        "ready true, active false)"),
                entry->getBucketInfo().toString());
    }

    {
        std::shared_ptr<BucketDiskMoveCommand> cmd(
                new BucketDiskMoveCommand(makeDocumentBucket(bid), 0, 1));

        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<BucketDiskMoveReply> reply(
                std::dynamic_pointer_cast<BucketDiskMoveReply>(top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        CPPUNIT_ASSERT_EQUAL(1, (int)reply->getBucketInfo().getDocumentCount());
    }

    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(bid, "foo"));

        CPPUNIT_ASSERT_EQUAL(1, (int)entry->disk);
        CPPUNIT_ASSERT_EQUAL(
                vespalib::string(
                        "BucketInfo(crc 0x28cc441f, docCount 1, totDocSize 114, "
                        "ready true, active false)"),
                entry->getBucketInfo().toString());
    }
}


void
FileStorManagerTest::testStateChange()
{
    TestName testName("testStateChange");
    // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager =
            new FileStorManager(config->getConfigId(), _node->getPartitions(),
                                _node->getPersistenceProvider(),
                                _node->getComponentRegister())));
    top.open();

    setClusterState("storage:3 distributor:3");

    CPPUNIT_ASSERT_EQUAL(true, getDummyPersistence().getClusterState().nodeUp());

    setClusterState("storage:3 .0.s:d distributor:3");

    CPPUNIT_ASSERT_EQUAL(false, getDummyPersistence().getClusterState().nodeUp());
}

void
FileStorManagerTest::testRepairNotifiesDistributorOnChange()
{
    // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager =
            new FileStorManager(config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    setClusterState("storage:1 distributor:1");
    top.open();

    createBucket(document::BucketId(16, 1), 0);

    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);

    // Creating a document to test with

    for (uint32_t i = 0; i < 3; ++i) {
        document::DocumentId docId(vespalib::make_string("userdoc:ns:1:%d", i));
        Document::SP doc(new Document(*_testdoctype1, docId));
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(document::BucketId(16, 1)), doc, i + 1));
        cmd->setAddress(address);
        top.sendDown(cmd);
    }

    top.waitForMessages(3, _waitTime);
    top.reset();

    getDummyPersistence().simulateMaintenanceFailure();

    std::shared_ptr<RepairBucketCommand> cmd(
            new RepairBucketCommand(makeDocumentBucket(document::BucketId(16, 1)), 0));
    top.sendDown(cmd);

    top.waitForMessages(2, _waitTime);

    CPPUNIT_ASSERT_EQUAL(
            std::string("NotifyBucketChangeCommand(BucketId(0x4000000000000001), "
                        "BucketInfo(crc 0x2625a314, docCount 2, totDocSize 154, "
                        "ready true, active false))"), top.getReply(0)->toString());

    top.close();
}


void
FileStorManagerTest::testFlush()
{
    TestName testName("testFlush");
        // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager = new FileStorManager(
                config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);
        // Creating a document to test with

    document::DocumentId docId("doc:crawler:http://www.ntnu.no/");
    Document::SP doc(new Document(*_testdoctype1, docId));
    document::BucketId bid(4000);

    static const uint32_t msgCount = 10;

    // Generating many put commands
    std::vector<std::shared_ptr<api::StorageCommand> > _commands;
    for (uint32_t i=0; i<msgCount; ++i) {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, i+1));
        cmd->setAddress(address);
        _commands.push_back(cmd);
    }
    for (uint32_t i=0; i<msgCount; ++i) {
        top.sendDown(_commands[i]);
    }
    top.close();
    top.flush();
    CPPUNIT_ASSERT_EQUAL((size_t) msgCount, top.getNumReplies());
}

void
FileStorManagerTest::testHandlerPriority()
{
    TestName testName("testHandlerPriority");
    // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(
                          dummyManager = new DummyStorageLink));
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    // Since we fake time with small numbers, we need to make sure we dont
    // compact them away, as they will seem to be from 1970

    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);

    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    filestorHandler.setGetNextMessageTimeout(50);
    uint32_t stripeId = filestorHandler.getNextStripeId(0);
    CPPUNIT_ASSERT_EQUAL(0u, stripeId);

    std::string content("Here is some content which is in all documents");
    std::ostringstream uri;

    Document::SP doc(createDocument(content, "userdoc:footype:1234:bar").release());

    document::BucketIdFactory factory;
    document::BucketId bucket(16, factory.getBucketId(doc->getId()).getRawId());

    // Populate bucket with the given data
    for (uint32_t i = 1; i < 6; i++) {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), doc, 100);
        auto address = std::make_shared<api::StorageMessageAddress>("storage", lib::NodeType::STORAGE, 3);
        cmd->setAddress(*address);
        cmd->setPriority(i * 15);
        filestorHandler.schedule(cmd, 0);
    }

    CPPUNIT_ASSERT_EQUAL(15, (int)filestorHandler.getNextMessage(0, stripeId).second->getPriority());
    CPPUNIT_ASSERT_EQUAL(30, (int)filestorHandler.getNextMessage(0, stripeId).second->getPriority());
    CPPUNIT_ASSERT_EQUAL(45, (int)filestorHandler.getNextMessage(0, stripeId).second->getPriority());
    CPPUNIT_ASSERT_EQUAL(60, (int)filestorHandler.getNextMessage(0, stripeId).second->getPriority());
    CPPUNIT_ASSERT_EQUAL(75, (int)filestorHandler.getNextMessage(0, stripeId).second->getPriority());
}

class MessagePusherThread : public document::Runnable
{
public:
    FileStorHandler& _handler;
    Document::SP _doc;
    bool _done;
    bool _threadDone;

    MessagePusherThread(FileStorHandler& handler, Document::SP doc);
    ~MessagePusherThread();

    void run() override {
        while (!_done) {
            document::BucketIdFactory factory;
            document::BucketId bucket(16, factory.getBucketId(_doc->getId()).getRawId());

            auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), _doc, 100);
            _handler.schedule(cmd, 0);
            FastOS_Thread::Sleep(1);
        }

        _threadDone = true;
    }
};

MessagePusherThread::MessagePusherThread(FileStorHandler& handler, Document::SP doc)
    : _handler(handler), _doc(doc), _done(false), _threadDone(false)
{}
MessagePusherThread::~MessagePusherThread() = default;

class MessageFetchingThread : public document::Runnable {
public:
    const uint32_t _threadId;
    FileStorHandler& _handler;
    std::atomic<uint32_t> _config;
    uint32_t _fetchedCount;
    bool _done;
    bool _failed;
    bool _threadDone;

    MessageFetchingThread(FileStorHandler& handler)
        : _threadId(handler.getNextStripeId(0)), _handler(handler), _config(0), _fetchedCount(0), _done(false),
          _failed(false), _threadDone(false)
    {}

    void run() override {
        while (!_done) {
            FileStorHandler::LockedMessage msg = _handler.getNextMessage(0, _threadId);
            if (msg.second.get()) {
                uint32_t originalConfig = _config.load();
                _fetchedCount++;
                FastOS_Thread::Sleep(5);

                if (_config.load() != originalConfig) {
                    _failed = true;
                }
            } else {
                FastOS_Thread::Sleep(1);
            }
        }

        _threadDone = true;
    };
};

void
FileStorManagerTest::testHandlerPausedMultiThread()
{
    TestName testName("testHandlerPausedMultiThread");
    // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(
                          dummyManager = new DummyStorageLink));
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    // Since we fake time with small numbers, we need to make sure we dont
    // compact them away, as they will seem to be from 1970

    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);

    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    filestorHandler.setGetNextMessageTimeout(50);

    std::string content("Here is some content which is in all documents");
    std::ostringstream uri;

    Document::SP doc(createDocument(content, "userdoc:footype:1234:bar").release());

    FastOS_ThreadPool pool(512 * 1024);
    MessagePusherThread pushthread(filestorHandler, doc);
    pushthread.start(pool);

    MessageFetchingThread thread(filestorHandler);
    thread.start(pool);

    for (uint32_t i = 0; i < 50; ++i) {
        FastOS_Thread::Sleep(2);
        ResumeGuard guard = filestorHandler.pause();
        thread._config.fetch_add(1);
        uint32_t count = thread._fetchedCount;
        CPPUNIT_ASSERT_EQUAL(count, thread._fetchedCount);
    }

    pushthread._done = true;
    thread._done = true;
    CPPUNIT_ASSERT(!thread._failed);

    while (!pushthread._threadDone || !thread._threadDone) {
        FastOS_Thread::Sleep(1);
    }
}


void
FileStorManagerTest::testHandlerPause()
{
    TestName testName("testHandlerPriority");
    // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(dummyManager = new DummyStorageLink));
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    // Since we fake time with small numbers, we need to make sure we dont
    // compact them away, as they will seem to be from 1970

    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);

    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    filestorHandler.setGetNextMessageTimeout(50);
    uint32_t stripeId = filestorHandler.getNextStripeId(0);

    std::string content("Here is some content which is in all documents");
    std::ostringstream uri;

    Document::SP doc(createDocument(content, "userdoc:footype:1234:bar").release());

    document::BucketIdFactory factory;
    document::BucketId bucket(16, factory.getBucketId(doc->getId()).getRawId());

    // Populate bucket with the given data
    for (uint32_t i = 1; i < 6; i++) {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), doc, 100);
        auto address = std::make_unique<api::StorageMessageAddress>("storage", lib::NodeType::STORAGE, 3);
        cmd->setAddress(*address);
        cmd->setPriority(i * 15);
        filestorHandler.schedule(cmd, 0);
    }

    CPPUNIT_ASSERT_EQUAL(15, (int)filestorHandler.getNextMessage(0, stripeId).second->getPriority());

    {
        ResumeGuard guard = filestorHandler.pause();
        (void)guard;
        CPPUNIT_ASSERT(filestorHandler.getNextMessage(0, stripeId).second.get() == NULL);
    }

    CPPUNIT_ASSERT_EQUAL(30, (int)filestorHandler.getNextMessage(0, stripeId).second->getPriority());
}

namespace {

uint64_t getPutTime(api::StorageMessage::SP& msg)
{
    if (!msg.get()) {
        return (uint64_t)-1;
    }

    return static_cast<api::PutCommand*>(msg.get())->getTimestamp();
};

}

void
FileStorManagerTest::testRemapSplit()
{
    TestName testName("testRemapSplit");
    // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(dummyManager = new DummyStorageLink));
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    // Since we fake time with small numbers, we need to make sure we dont
    // compact them away, as they will seem to be from 1970

    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);

    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    filestorHandler.setGetNextMessageTimeout(50);

    std::string content("Here is some content which is in all documents");

    Document::SP doc1(createDocument(content, "userdoc:footype:1234:bar").release());

    Document::SP doc2(createDocument(content, "userdoc:footype:4567:bar").release());

    document::BucketIdFactory factory;
    document::BucketId bucket1(16, 1234);
    document::BucketId bucket2(16, 4567);

    // Populate bucket with the given data
    for (uint32_t i = 1; i < 4; i++) {
        filestorHandler.schedule(std::make_shared<api::PutCommand>(makeDocumentBucket(bucket1), doc1, i), 0);
        filestorHandler.schedule(std::make_shared<api::PutCommand>(makeDocumentBucket(bucket2), doc2, i + 10), 0);
    }

    CPPUNIT_ASSERT_EQUAL(std::string("BucketId(0x40000000000004d2): Put(BucketId(0x40000000000004d2), userdoc:footype:1234:bar, timestamp 1, size 108) (priority: 127)\n"
                                     "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), userdoc:footype:4567:bar, timestamp 11, size 108) (priority: 127)\n"
                                     "BucketId(0x40000000000004d2): Put(BucketId(0x40000000000004d2), userdoc:footype:1234:bar, timestamp 2, size 108) (priority: 127)\n"
                                     "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), userdoc:footype:4567:bar, timestamp 12, size 108) (priority: 127)\n"
                                     "BucketId(0x40000000000004d2): Put(BucketId(0x40000000000004d2), userdoc:footype:1234:bar, timestamp 3, size 108) (priority: 127)\n"
                                     "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), userdoc:footype:4567:bar, timestamp 13, size 108) (priority: 127)\n"),
                         filestorHandler.dumpQueue(0));

    FileStorHandler::RemapInfo a(makeDocumentBucket(document::BucketId(17, 1234)), 0);
    FileStorHandler::RemapInfo b(makeDocumentBucket(document::BucketId(17, 1234 | 1 << 16)), 0);
    filestorHandler.remapQueueAfterSplit(FileStorHandler::RemapInfo(makeDocumentBucket(bucket1), 0), a, b);

    CPPUNIT_ASSERT(a.foundInQueue);
    CPPUNIT_ASSERT(!b.foundInQueue);

    CPPUNIT_ASSERT_EQUAL(std::string(
                                 "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), userdoc:footype:4567:bar, timestamp 11, size 108) (priority: 127)\n"
                                 "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), userdoc:footype:4567:bar, timestamp 12, size 108) (priority: 127)\n"
                                 "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), userdoc:footype:4567:bar, timestamp 13, size 108) (priority: 127)\n"
                                 "BucketId(0x44000000000004d2): Put(BucketId(0x44000000000004d2), userdoc:footype:1234:bar, timestamp 1, size 108) (priority: 127)\n"
                                 "BucketId(0x44000000000004d2): Put(BucketId(0x44000000000004d2), userdoc:footype:1234:bar, timestamp 2, size 108) (priority: 127)\n"
                                 "BucketId(0x44000000000004d2): Put(BucketId(0x44000000000004d2), userdoc:footype:1234:bar, timestamp 3, size 108) (priority: 127)\n"),
             filestorHandler.dumpQueue(0));

}

void
FileStorManagerTest::testHandlerMulti()
{
    TestName testName("testHandlerMulti");
    // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(
                          dummyManager = new DummyStorageLink));
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    // Since we fake time with small numbers, we need to make sure we dont
    // compact them away, as they will seem to be from 1970

    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);

    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    filestorHandler.setGetNextMessageTimeout(50);
    uint32_t stripeId = filestorHandler.getNextStripeId(0);

    std::string content("Here is some content which is in all documents");

    Document::SP doc1(createDocument(content, "userdoc:footype:1234:bar").release());

    Document::SP doc2(createDocument(content, "userdoc:footype:4567:bar").release());

    document::BucketIdFactory factory;
    document::BucketId bucket1(16, factory.getBucketId(doc1->getId()).getRawId());
    document::BucketId bucket2(16, factory.getBucketId(doc2->getId()).getRawId());

    // Populate bucket with the given data
    for (uint32_t i = 1; i < 10; i++) {
        filestorHandler.schedule(
                api::StorageMessage::SP(new api::PutCommand(makeDocumentBucket(bucket1), doc1, i)), 0);
        filestorHandler.schedule(
                api::StorageMessage::SP(new api::PutCommand(makeDocumentBucket(bucket2), doc2, i + 10)), 0);
    }

    {
        FileStorHandler::LockedMessage lock = filestorHandler.getNextMessage(0, stripeId);
        CPPUNIT_ASSERT_EQUAL((uint64_t)1, getPutTime(lock.second));

        lock = filestorHandler.getNextMessage(0, stripeId, lock);
        CPPUNIT_ASSERT_EQUAL((uint64_t)2, getPutTime(lock.second));

        lock = filestorHandler.getNextMessage(0, stripeId, lock);
        CPPUNIT_ASSERT_EQUAL((uint64_t)3, getPutTime(lock.second));
    }

    {
        FileStorHandler::LockedMessage lock = filestorHandler.getNextMessage(0, stripeId);
        CPPUNIT_ASSERT_EQUAL((uint64_t)11, getPutTime(lock.second));

        lock = filestorHandler.getNextMessage(0, stripeId, lock);
        CPPUNIT_ASSERT_EQUAL((uint64_t)12, getPutTime(lock.second));
    }
}


void
FileStorManagerTest::testHandlerTimeout()
{
    TestName testName("testHandlerTimeout");
    // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(dummyManager = new DummyStorageLink));
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);

    // Since we fake time with small numbers, we need to make sure we dont
    // compact them away, as they will seem to be from 1970

    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(),1,  1);

    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    filestorHandler.setGetNextMessageTimeout(50);
    uint32_t stripeId = filestorHandler.getNextStripeId(0);

    std::string content("Here is some content which is in all documents");
    std::ostringstream uri;

    Document::SP doc(createDocument(content, "userdoc:footype:1234:bar").release());

    document::BucketIdFactory factory;
    document::BucketId bucket(16, factory.getBucketId(doc->getId()).getRawId());

    // Populate bucket with the given data
    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), doc, 100);
        auto address = std::make_unique<api::StorageMessageAddress>("storage", lib::NodeType::STORAGE, 3);
        cmd->setAddress(*address);
        cmd->setPriority(0);
        cmd->setTimeout(50);
        filestorHandler.schedule(cmd, 0);
    }

    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), doc, 100);
        auto address = std::make_unique<api::StorageMessageAddress>("storage", lib::NodeType::STORAGE, 3);
        cmd->setAddress(*address);
        cmd->setPriority(200);
        cmd->setTimeout(10000);
        filestorHandler.schedule(cmd, 0);
    }

    FastOS_Thread::Sleep(51);
    for (;;) {
        auto lock = filestorHandler.getNextMessage(0, stripeId);
        if (lock.first.get()) {
            CPPUNIT_ASSERT_EQUAL(uint8_t(200), lock.second->getPriority());
            break;
        }
    }

    CPPUNIT_ASSERT_EQUAL(size_t(1), top.getNumReplies());
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::TIMEOUT,
                         static_cast<api::StorageReply&>(*top.getReply(0)).getResult().getResult());
}

void
FileStorManagerTest::testPriority()
{
    TestName testName("testPriority");
    // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(
                          dummyManager = new DummyStorageLink));
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    // Since we fake time with small numbers, we need to make sure we dont
    // compact them away, as they will seem to be from 1970

    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(),1,  2);

    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    std::unique_ptr<DiskThread> thread(createThread(
            *config, *_node, _node->getPersistenceProvider(),
            filestorHandler, *metrics.disks[0]->threads[0], 0));
    std::unique_ptr<DiskThread> thread2(createThread(
            *config, *_node, _node->getPersistenceProvider(),
            filestorHandler, *metrics.disks[0]->threads[1], 0));

    // Creating documents to test with. Different gids, 2 locations.
    std::vector<document::Document::SP > documents;
    for (uint32_t i=0; i<50; ++i) {
        std::string content("Here is some content which is in all documents");
        std::ostringstream uri;

        uri << "userdoc:footype:" << (i % 3 == 0 ? 0x10001 : 0x0100001)<< ":mydoc-" << i;
        Document::SP doc(createDocument(content, uri.str()).release());
        documents.push_back(doc);
    }

    document::BucketIdFactory factory;

    // Create buckets in separate, initial pass to avoid races with puts
    for (uint32_t i=0; i<documents.size(); ++i) {
        document::BucketId bucket(16, factory.getBucketId(documents[i]->getId()).getRawId());

        spi::Context context(defaultLoadType, spi::Priority(0), spi::Trace::TraceLevel(0));

        _node->getPersistenceProvider().createBucket(makeSpiBucket(bucket), context);
    }

    // Populate bucket with the given data
    for (uint32_t i=0; i<documents.size(); ++i) {
        document::BucketId bucket(16, factory.getBucketId(documents[i]->getId()).getRawId());

        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bucket), documents[i], 100 + i));
        std::unique_ptr<api::StorageMessageAddress> address(
                new api::StorageMessageAddress(
                        "storage", lib::NodeType::STORAGE, 3));
        cmd->setAddress(*address);
        cmd->setPriority(i * 2);
        filestorHandler.schedule(cmd, 0);
    }

    filestorHandler.flush(true);

    // Wait until everything is done.
    int count = 0;
    while (documents.size() != top.getNumReplies() && count < 1000) {
        FastOS_Thread::Sleep(100);
        count++;
    }
    CPPUNIT_ASSERT(count < 1000);

    for (uint32_t i = 0; i < documents.size(); i++) {
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                        top.getReply(i)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
    }

    // Verify that thread 1 gets documents over 50 pri
    CPPUNIT_ASSERT_EQUAL(uint64_t(documents.size()),
            metrics.disks[0]->threads[0]->operations.getValue()
            + metrics.disks[0]->threads[1]->operations.getValue());
    // Closing file stor handler before threads are deleted, such that
    // file stor threads getNextMessage calls returns.
    filestorHandler.close();
}

void
FileStorManagerTest::testSplit1()
{
    TestName testName("testSplit1");
        // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(
                dummyManager = new DummyStorageLink));
    setClusterState("storage:2 distributor:1");
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);
    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    std::unique_ptr<DiskThread> thread(createThread(
            *config, *_node, _node->getPersistenceProvider(),
            filestorHandler, *metrics.disks[0]->threads[0], 0));
        // Creating documents to test with. Different gids, 2 locations.
    std::vector<document::Document::SP > documents;
    for (uint32_t i=0; i<20; ++i) {
        std::string content("Here is some content which is in all documents");
        std::ostringstream uri;

        uri << "userdoc:footype:" << (i % 3 == 0 ? 0x10001 : 0x0100001)
                               << ":mydoc-" << i;
        Document::SP doc(createDocument(
                content, uri.str()).release());
        documents.push_back(doc);
    }
    document::BucketIdFactory factory;
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    {
        // Populate bucket with the given data
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(16, factory.getBucketId(
                                             documents[i]->getId()).getRawId());

            _node->getPersistenceProvider().createBucket(
                    makeSpiBucket(bucket), context);

            std::shared_ptr<api::PutCommand> cmd(
                    new api::PutCommand(makeDocumentBucket(bucket), documents[i], 100 + i));
            std::unique_ptr<api::StorageMessageAddress> address(
                    new api::StorageMessageAddress(
                        "storage", lib::NodeType::STORAGE, 3));
            cmd->setAddress(*address);
            cmd->setSourceIndex(0);

            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            LOG(debug, "Got %" PRIu64 " replies", top.getNumReplies());
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::PutReply> reply(
                    std::dynamic_pointer_cast<api::PutReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
            top.reset();

            // Delete every 5th document to have delete entries in file too
            if (i % 5 == 0) {
                std::shared_ptr<api::RemoveCommand> rcmd(
                        new api::RemoveCommand(
                            makeDocumentBucket(bucket), documents[i]->getId(), 1000000 + 100 + i));
                rcmd->setAddress(*address);
                filestorHandler.schedule(rcmd, 0);
                filestorHandler.flush(true);
                CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
                std::shared_ptr<api::RemoveReply> rreply(
                        std::dynamic_pointer_cast<api::RemoveReply>(
                            top.getReply(0)));
                CPPUNIT_ASSERT_MSG(top.getReply(0)->getType().toString(),
                                   rreply.get());
                CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                     rreply->getResult());
                top.reset();
            }
        }

        // Perform a split, check that locations are split
        {
            std::shared_ptr<api::SplitBucketCommand> cmd(
                    new api::SplitBucketCommand(makeDocumentBucket(document::BucketId(16, 1))));
            cmd->setSourceIndex(0);
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::SplitBucketReply> reply(
                    std::dynamic_pointer_cast<api::SplitBucketReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
            top.reset();
        }

        // Test that the documents have gotten into correct parts.
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(
                        17, i % 3 == 0 ? 0x10001 : 0x0100001);
            std::shared_ptr<api::GetCommand> cmd(
                    new api::GetCommand(makeDocumentBucket(bucket), documents[i]->getId(), "[all]"));
            api::StorageMessageAddress address(
                    "storage", lib::NodeType::STORAGE, 3);
            cmd->setAddress(address);
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::GetReply> reply(
                    std::dynamic_pointer_cast<api::GetReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(i % 5 != 0 ? true : false, reply->wasFound());
            top.reset();
        }

        // Keep splitting location 1 until we gidsplit
        for (int i=17; i<=32; ++i) {
            std::shared_ptr<api::SplitBucketCommand> cmd(
                    new api::SplitBucketCommand(
                        makeDocumentBucket(document::BucketId(i, 0x0100001))));
            cmd->setSourceIndex(0);
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::SplitBucketReply> reply(
                    std::dynamic_pointer_cast<api::SplitBucketReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
            top.reset();
        }

        // Test that the documents have gotten into correct parts.
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket;
            if (i % 3 == 0) {
                bucket = document::BucketId(17, 0x10001);
            } else {
                bucket = document::BucketId(33, factory.getBucketId(
                                    documents[i]->getId()).getRawId());
            }
            std::shared_ptr<api::GetCommand> cmd(
                    new api::GetCommand(makeDocumentBucket(bucket), documents[i]->getId(), "[all]"));
            api::StorageMessageAddress address(
                    "storage", lib::NodeType::STORAGE, 3);
            cmd->setAddress(address);
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::GetReply> reply(
                    std::dynamic_pointer_cast<api::GetReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(i % 5 != 0 ? true : false, reply->wasFound());
            top.reset();
        }
    }
        // Closing file stor handler before threads are deleted, such that
        // file stor threads getNextMessage calls returns.
    filestorHandler.close();
}

void
FileStorManagerTest::testSplitSingleGroup()
{
    TestName testName("testSplitSingleGroup");
        // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(
                dummyManager = new DummyStorageLink));
    setClusterState("storage:2 distributor:1");
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(),1,  1);
    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    spi::Context context(defaultLoadType, spi::Priority(0), spi::Trace::TraceLevel(0));
    for (uint32_t j=0; j<1; ++j) {
        // Test this twice, once where all the data ends up in file with
        // splitbit set, and once where all the data ends up in file with
        // splitbit unset
        bool state = (j == 0);

        std::unique_ptr<DiskThread> thread(createThread(
                *config, *_node, _node->getPersistenceProvider(),
                filestorHandler, *metrics.disks[0]->threads[0], 0));
            // Creating documents to test with. Different gids, 2 locations.
        std::vector<document::Document::SP > documents;
        for (uint32_t i=0; i<20; ++i) {
            std::string content("Here is some content for all documents");
            std::ostringstream uri;

            uri << "userdoc:footype:" << (state ? 0x10001 : 0x0100001)
                                   << ":mydoc-" << i;
            Document::SP doc(createDocument(
                    content, uri.str()).release());
            documents.push_back(doc);
        }
        document::BucketIdFactory factory;

            // Populate bucket with the given data
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(16, factory.getBucketId(
                                documents[i]->getId()).getRawId());

            _node->getPersistenceProvider().createBucket(
                    makeSpiBucket(bucket), context);

            std::shared_ptr<api::PutCommand> cmd(
                    new api::PutCommand(makeDocumentBucket(bucket), documents[i], 100 + i));
            api::StorageMessageAddress address(
                    "storage", lib::NodeType::STORAGE, 3);
            cmd->setAddress(address);
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::PutReply> reply(
                    std::dynamic_pointer_cast<api::PutReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
            top.reset();
        }
            // Perform a split, check that locations are split
        {
            std::shared_ptr<api::SplitBucketCommand> cmd(
                    new api::SplitBucketCommand(makeDocumentBucket(document::BucketId(16, 1))));
            cmd->setSourceIndex(0);
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::SplitBucketReply> reply(
                    std::dynamic_pointer_cast<api::SplitBucketReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
            top.reset();
        }


        // Test that the documents are all still there
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(17, state ? 0x10001 : 0x00001);
            std::shared_ptr<api::GetCommand> cmd(
                    new api::GetCommand(makeDocumentBucket(bucket), documents[i]->getId(), "[all]"));
            api::StorageMessageAddress address(
                    "storage", lib::NodeType::STORAGE, 3);
            cmd->setAddress(address);
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::GetReply> reply(
                    std::dynamic_pointer_cast<api::GetReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
            top.reset();
        }
            // Closing file stor handler before threads are deleted, such that
            // file stor threads getNextMessage calls returns.
        filestorHandler.close();
    }
}

void
FileStorManagerTest::putDoc(DummyStorageLink& top,
                            FileStorHandler& filestorHandler,
                            const document::BucketId& target,
                            uint32_t docNum)
{
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);
    spi::Context context(defaultLoadType, spi::Priority(0),
                         spi::Trace::TraceLevel(0));
    document::BucketIdFactory factory;
    document::DocumentId docId(vespalib::make_string("userdoc:ns:%zu:%d", target.getId(), docNum));
    document::BucketId bucket(16, factory.getBucketId(docId).getRawId());
    //std::cerr << "doc bucket is " << bucket << " vs source " << source << "\n";
    _node->getPersistenceProvider().createBucket(
            makeSpiBucket(target), context);
    Document::SP doc(new Document(*_testdoctype1, docId));
    std::shared_ptr<api::PutCommand> cmd(
            new api::PutCommand(makeDocumentBucket(target), doc, docNum+1));
    cmd->setAddress(address);
    cmd->setPriority(120);
    filestorHandler.schedule(cmd, 0);
    filestorHandler.flush(true);
    CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
    std::shared_ptr<api::PutReply> reply(
            std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
    CPPUNIT_ASSERT(reply.get());
    CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                         reply->getResult());
    top.reset();
}

void
FileStorManagerTest::testSplitEmptyTargetWithRemappedOps()
{
    TestName testName("testSplitEmptyTargetWithRemappedOps");

    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(
                dummyManager = new DummyStorageLink));
    setClusterState("storage:2 distributor:1");
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);
    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    std::unique_ptr<DiskThread> thread(createThread(
            *config, *_node, _node->getPersistenceProvider(),
            filestorHandler, *metrics.disks[0]->threads[0], 0));

    document::BucketId source(16, 0x10001);

    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);

    for (uint32_t i=0; i<10; ++i) {
        putDoc(top, filestorHandler, source, i);
    }

    // Send split followed by a put that is bound for a target bucket that
    // will end up empty in the split itself. The split should notice this
    // and create the bucket explicitly afterwards in order to compensate for
    // the persistence provider deleting it internally.
    // Make sure we block the operation queue until we've scheduled all
    // the operations.
    std::unique_ptr<ResumeGuard> resumeGuard(
            new ResumeGuard(filestorHandler.pause()));

    std::shared_ptr<api::SplitBucketCommand> splitCmd(
            new api::SplitBucketCommand(makeDocumentBucket(source)));
    splitCmd->setPriority(120);
    splitCmd->setSourceIndex(0);

    document::DocumentId docId(
            vespalib::make_string("userdoc:ns:%d:1234", 0x100001));
    Document::SP doc(new Document(*_testdoctype1, docId));
    std::shared_ptr<api::PutCommand> putCmd(
            new api::PutCommand(makeDocumentBucket(source), doc, 1001));
    putCmd->setAddress(address);
    putCmd->setPriority(120);

    filestorHandler.schedule(splitCmd, 0);
    filestorHandler.schedule(putCmd, 0);
    resumeGuard.reset(0); // Unpause
    filestorHandler.flush(true);

    top.waitForMessages(2, _waitTime);

    CPPUNIT_ASSERT_EQUAL((size_t) 2, top.getNumReplies());
    {
        std::shared_ptr<api::SplitBucketReply> reply(
                std::dynamic_pointer_cast<api::SplitBucketReply>(
                        top.getReply(0)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                             reply->getResult());
    }
    {
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                        top.getReply(1)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                             reply->getResult());
    }

    top.reset();
}

void
FileStorManagerTest::testNotifyOnSplitSourceOwnershipChanged()
{
    TestName testName("testSplit1");
    // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(dummyManager = new DummyStorageLink));
    setClusterState("storage:2 distributor:2");
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);
    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);
    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    std::unique_ptr<DiskThread> thread(createThread(
            *config, *_node, _node->getPersistenceProvider(),
            filestorHandler, *metrics.disks[0]->threads[0], 0));

    document::BucketId source(getFirstBucketNotOwnedByDistributor(0));
    createBucket(source, 0);
    for (uint32_t i=0; i<10; ++i) {
        putDoc(top, filestorHandler, source, i);
    }

    std::shared_ptr<api::SplitBucketCommand> splitCmd(
            new api::SplitBucketCommand(makeDocumentBucket(source)));
    splitCmd->setPriority(120);
    splitCmd->setSourceIndex(0); // Source not owned by this distributor.

    filestorHandler.schedule(splitCmd, 0);
    filestorHandler.flush(true);
    top.waitForMessages(4, _waitTime); // 3 notify cmds + split reply

    CPPUNIT_ASSERT_EQUAL(size_t(4), top.getNumReplies());
    for (int i = 0; i < 3; ++i) {
        CPPUNIT_ASSERT_EQUAL(api::MessageType::NOTIFYBUCKETCHANGE,
                             top.getReply(i)->getType());
    }

    std::shared_ptr<api::SplitBucketReply> reply(
            std::dynamic_pointer_cast<api::SplitBucketReply>(
                    top.getReply(3)));
    CPPUNIT_ASSERT(reply.get());
    CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                         reply->getResult());
}

void
FileStorManagerTest::testJoin()
{
    TestName testName("testJoin");
        // Setup a filestorthread to test
    DummyStorageLink top;
    DummyStorageLink *dummyManager;
    top.push_back(std::unique_ptr<StorageLink>(
                dummyManager = new DummyStorageLink));
    top.open();
    ForwardingMessageSender messageSender(*dummyManager);

    documentapi::LoadTypeSet loadTypes("raw:");
    FileStorMetrics metrics(loadTypes.getMetricLoadTypes());
    metrics.initDiskMetrics(_node->getPartitions().size(), loadTypes.getMetricLoadTypes(), 1, 1);
    FileStorHandler filestorHandler(messageSender, metrics, _node->getPartitions(), _node->getComponentRegister());
    std::unique_ptr<DiskThread> thread(createThread(
            *config, *_node, _node->getPersistenceProvider(),
            filestorHandler, *metrics.disks[0]->threads[0], 0));
        // Creating documents to test with. Different gids, 2 locations.
    std::vector<document::Document::SP > documents;
    for (uint32_t i=0; i<20; ++i) {
        std::string content("Here is some content which is in all documents");
        std::ostringstream uri;

        uri << "userdoc:footype:" << (i % 3 == 0 ? 0x10001 : 0x0100001) << ":mydoc-" << i;
        Document::SP doc(createDocument(content, uri.str()).release());
        documents.push_back(doc);
    }
    document::BucketIdFactory factory;

    createBucket(document::BucketId(17, 0x00001), 0);
    createBucket(document::BucketId(17, 0x10001), 0);

    {
            // Populate bucket with the given data
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(17, factory.getBucketId(documents[i]->getId()).getRawId());
            auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), documents[i], 100 + i);
            auto address = std::make_unique<api::StorageMessageAddress>("storage", lib::NodeType::STORAGE, 3);
            cmd->setAddress(*address);
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
            top.reset();
                // Delete every 5th document to have delete entries in file too
            if (i % 5 == 0) {
                auto rcmd = std::make_shared<api::RemoveCommand>(makeDocumentBucket(bucket),
                                                                 documents[i]->getId(), 1000000 + 100 + i);
                rcmd->setAddress(*address);
                filestorHandler.schedule(rcmd, 0);
                filestorHandler.flush(true);
                CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
                auto rreply = std::dynamic_pointer_cast<api::RemoveReply>(top.getReply(0));
                CPPUNIT_ASSERT_MSG(top.getReply(0)->getType().toString(),
                                   rreply.get());
                CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                     rreply->getResult());
                top.reset();
            }
        }
        LOG(debug, "Starting the actual join after populating data");
            // Perform a join, check that other files are gone
        {
            std::shared_ptr<api::JoinBucketsCommand> cmd(
                    new api::JoinBucketsCommand(makeDocumentBucket(document::BucketId(16, 1))));
            cmd->getSourceBuckets().push_back(document::BucketId(17, 0x00001));
            cmd->getSourceBuckets().push_back(document::BucketId(17, 0x10001));
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::JoinBucketsReply> reply(
                    std::dynamic_pointer_cast<api::JoinBucketsReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
            top.reset();
        }
            // Test that the documents have gotten into the file.
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(16, 1);
            std::shared_ptr<api::GetCommand> cmd(
                    new api::GetCommand(makeDocumentBucket(bucket), documents[i]->getId(), "[all]"));
            api::StorageMessageAddress address(
                    "storage", lib::NodeType::STORAGE, 3);
            cmd->setAddress(address);
            filestorHandler.schedule(cmd, 0);
            filestorHandler.flush(true);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<api::GetReply> reply(
                    std::dynamic_pointer_cast<api::GetReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(i % 5 != 0 ? true : false, reply->wasFound());
            top.reset();
        }
    }
        // Closing file stor handler before threads are deleted, such that
        // file stor threads getNextMessage calls returns.
    filestorHandler.close();
}

namespace {

spi::IteratorId
createIterator(DummyStorageLink& link,
               const document::BucketId& bucketId,
               const std::string& docSel,
               framework::MicroSecTime fromTime = framework::MicroSecTime(0),
               framework::MicroSecTime toTime = framework::MicroSecTime::max(),
               bool headerOnly = false)
{
    spi::Bucket bucket(makeSpiBucket(bucketId));

    spi::Selection selection =
        spi::Selection(spi::DocumentSelection(docSel));
    selection.setFromTimestamp(spi::Timestamp(fromTime.getTime()));
    selection.setToTimestamp(spi::Timestamp(toTime.getTime()));
    CreateIteratorCommand::SP createIterCmd(
            new CreateIteratorCommand(makeDocumentBucket(bucket),
                                      selection,
                                      headerOnly ? "[header]" : "[all]",
                                      spi::NEWEST_DOCUMENT_ONLY));
    link.sendDown(createIterCmd);
    link.waitForMessages(1, FileStorManagerTest::LONG_WAITTIME);
    CPPUNIT_ASSERT_EQUAL(size_t(1), link.getNumReplies());
    std::shared_ptr<CreateIteratorReply> reply(
            std::dynamic_pointer_cast<CreateIteratorReply>(
                    link.getReply(0)));
    CPPUNIT_ASSERT(reply.get());
    link.reset();
    CPPUNIT_ASSERT(reply->getResult().success());
    return reply->getIteratorId();
}

}

void
FileStorManagerTest::testVisiting()
{
    TestName testName("testVisiting");
        // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager = new FileStorManager(
            smallConfig->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
        // Adding documents to two buckets which we are going to visit
        // We want one bucket in one slotfile, and one bucket with a file split
    uint32_t docCount = 50;
    std::vector<document::BucketId> ids(2);
    ids[0] = document::BucketId(16, 1);
    ids[1] = document::BucketId(16, 2);

    createBucket(ids[0], 0);
    createBucket(ids[1], 0);

    lib::RandomGen randomizer(523);
    for (uint32_t i=0; i<docCount; ++i) {
        std::string content("Here is some content which is in all documents");
        std::ostringstream uri;

        uri << "userdoc:crawler:" << (i < 3 ? 1 : 2) << ":"
            << randomizer.nextUint32() << ".html";
        Document::SP doc(createDocument(
                content, uri.str()).release());
        const document::DocumentType& type(doc->getType());
        if (i < 30) {
            doc->setValue(type.getField("hstringval"),
                          document::StringFieldValue("John Doe"));
        } else {
            doc->setValue(type.getField("hstringval"),
                          document::StringFieldValue("Jane Doe"));
        }
        std::shared_ptr<api::PutCommand> cmd(new api::PutCommand(
                    makeDocumentBucket(ids[i < 3 ? 0 : 1]), doc, i+1));
        top.sendDown(cmd);
    }
    top.waitForMessages(docCount, _waitTime);
    CPPUNIT_ASSERT_EQUAL((size_t) docCount, top.getNumReplies());
        // Check nodestate with splitting
    {
        api::BucketInfo info;
        for (uint32_t i=3; i<docCount; ++i) {
            std::shared_ptr<api::BucketInfoReply> reply(
                    std::dynamic_pointer_cast<api::BucketInfoReply>(
                        top.getReply(i)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_MESSAGE(reply->getResult().toString(),
                                   reply->getResult().success());

            info = reply->getBucketInfo();
        }
        CPPUNIT_ASSERT_EQUAL(docCount-3, info.getDocumentCount());
    }
    top.reset();
        // Visit bucket with no split, using no selection
    {
        spi::IteratorId iterId(createIterator(top, ids[0], "true"));
        auto cmd = std::make_shared<GetIterCommand>(makeDocumentBucket(ids[0]), iterId, 16*1024);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL(size_t(1), top.getNumReplies());
        std::shared_ptr<GetIterReply> reply(
                std::dynamic_pointer_cast<GetIterReply>(top.getReply(0)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        CPPUNIT_ASSERT_EQUAL(ids[0], reply->getBucketId());
        CPPUNIT_ASSERT_EQUAL(size_t(3), reply->getEntries().size());
        top.reset();
    }
        // Visit bucket with split, using selection
    {
        uint32_t totalDocs = 0;
        spi::IteratorId iterId(
                createIterator(top,
                               ids[1],
                               "testdoctype1.hstringval = \"John Doe\""));
        while (true) {
            auto cmd = std::make_shared<GetIterCommand>(makeDocumentBucket(ids[1]), iterId, 16*1024);
            top.sendDown(cmd);
            top.waitForMessages(1, _waitTime);
            CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
            std::shared_ptr<GetIterReply> reply(
                    std::dynamic_pointer_cast<GetIterReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
            CPPUNIT_ASSERT_EQUAL(ids[1], reply->getBucketId());
            totalDocs += reply->getEntries().size();
            top.reset();
            if (reply->isCompleted()) {
                break;
            }
        }
        CPPUNIT_ASSERT_EQUAL(27u, totalDocs);
    }
        // Visit bucket with min and max timestamps set, headers only
    {
        document::BucketId bucket(16, 2);
        spi::IteratorId iterId(
                createIterator(top,
                               ids[1],
                               "",
                               framework::MicroSecTime(30),
                               framework::MicroSecTime(40),
                               true));
        uint32_t totalDocs = 0;
        while (true) {
            auto cmd = std::make_shared<GetIterCommand>(makeDocumentBucket(ids[1]), iterId, 16*1024);
            top.sendDown(cmd);
            top.waitForMessages(1, _waitTime);
            CPPUNIT_ASSERT_EQUAL(size_t(1), top.getNumReplies());
            std::shared_ptr<GetIterReply> reply(
                    std::dynamic_pointer_cast<GetIterReply>(
                        top.getReply(0)));
            CPPUNIT_ASSERT(reply.get());
            CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                                 reply->getResult());
            CPPUNIT_ASSERT_EQUAL(bucket, reply->getBucketId());
/* Header only is a VDS-specific thing.

            for (size_t i = 0; i < reply->getEntries().size(); ++i) {
                CPPUNIT_ASSERT(reply->getEntries()[i]->getDocument()
                               ->getBody().empty());
            }
*/
            totalDocs += reply->getEntries().size();
            top.reset();
            if (reply->isCompleted()) {
                break;
            }
        }
        CPPUNIT_ASSERT_EQUAL(11u, totalDocs);
    }

}

void
FileStorManagerTest::testRemoveLocation()
{
    TestName testName("testRemoveLocation");
        // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager =
            new FileStorManager(config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);
    document::BucketId bid(8, 0);

    createBucket(bid, 0);

    // Adding some documents to be removed later
    for (uint32_t i=0; i<=10; ++i) {
        std::ostringstream docid;
        docid << "userdoc:ns:" << (i << 8) << ":foo";
        Document::SP doc(createDocument(
                    "some content", docid.str()).release());
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 1000 + i));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        CPPUNIT_ASSERT_EQUAL(i + 1u, reply->getBucketInfo().getDocumentCount());
    }
        // Issuing remove location command
    {
        std::shared_ptr<api::RemoveLocationCommand> cmd(
                new api::RemoveLocationCommand("id.user % 512 == 0", makeDocumentBucket(bid)));
                //new api::RemoveLocationCommand("id.user == 1", bid));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::RemoveLocationReply> reply(
                std::dynamic_pointer_cast<api::RemoveLocationReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        CPPUNIT_ASSERT_EQUAL(5u, reply->getBucketInfo().getDocumentCount());
    }
}

void FileStorManagerTest::testDeleteBucket()
{
    TestName testName("testDeleteBucket");
        // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager = new FileStorManager(
                    config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address(
            "storage", lib::NodeType::STORAGE, 2);
        // Creating a document to test with
    document::DocumentId docId("userdoc:crawler:4000:http://www.ntnu.no/");
    Document::SP doc(new Document(*_testdoctype1, docId));
    document::BucketId bid(16, 4000);

    createBucket(bid, 0);

    api::BucketInfo bucketInfo;
    // Putting it
    {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 105));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());

        CPPUNIT_ASSERT_EQUAL(1, (int)reply->getBucketInfo().getDocumentCount());
        bucketInfo = reply->getBucketInfo();
        top.reset();
    }

    // Delete bucket
    {
        std::shared_ptr<api::DeleteBucketCommand> cmd(
                new api::DeleteBucketCommand(makeDocumentBucket(bid)));
        cmd->setAddress(address);
        cmd->setBucketInfo(bucketInfo);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::DeleteBucketReply> reply(
                std::dynamic_pointer_cast<api::DeleteBucketReply>(
                    top.getReply(0)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
    }
}

void
FileStorManagerTest::testDeleteBucketRejectOutdatedBucketInfo()
{
    TestName testName("testDeleteBucketRejectOutdatedBucketInfo");
    // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager = new FileStorManager(
                    config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address(
            "storage", lib::NodeType::STORAGE, 2);
    // Creating a document to test with
    document::DocumentId docId("userdoc:crawler:4000:http://www.ntnu.no/");
    Document::SP doc(new Document(*_testdoctype1, docId));
    document::BucketId bid(16, 4000);

    createBucket(bid, 0);

    api::BucketInfo bucketInfo;

    // Putting it
    {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 105));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());

        CPPUNIT_ASSERT_EQUAL(1, (int)reply->getBucketInfo().getDocumentCount());
        bucketInfo = reply->getBucketInfo();
        top.reset();
    }

    // Attempt to delete bucket, but with non-matching bucketinfo
    {
        std::shared_ptr<api::DeleteBucketCommand> cmd(
                new api::DeleteBucketCommand(makeDocumentBucket(bid)));
        cmd->setBucketInfo(api::BucketInfo(0xf000baaa, 1, 123, 1, 456));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::DeleteBucketReply> reply(
                std::dynamic_pointer_cast<api::DeleteBucketReply>(
                    top.getReply(0)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(
                ReturnCode::REJECTED,
                reply->getResult().getResult());
        CPPUNIT_ASSERT_EQUAL(bucketInfo, reply->getBucketInfo());
    }
}

/**
 * Test that receiving a DeleteBucketCommand with invalid
 * BucketInfo deletes the bucket and does not fail the operation.
 */
void
FileStorManagerTest::testDeleteBucketWithInvalidBucketInfo()
{
    TestName testName("testDeleteBucketWithInvalidBucketInfo");
    // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager = new FileStorManager(
                    config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address(
            "storage", lib::NodeType::STORAGE, 2);
    // Creating a document to test with
    document::DocumentId docId("userdoc:crawler:4000:http://www.ntnu.no/");
    Document::SP doc(new Document(*_testdoctype1, docId));
    document::BucketId bid(16, 4000);

    createBucket(bid, 0);

    // Putting it
    {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 105));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        CPPUNIT_ASSERT_EQUAL(1, (int)reply->getBucketInfo().getDocumentCount());
        top.reset();
    }

    // Attempt to delete bucket with invalid bucketinfo
    {
        std::shared_ptr<api::DeleteBucketCommand> cmd(
                new api::DeleteBucketCommand(makeDocumentBucket(bid)));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::DeleteBucketReply> reply(
                std::dynamic_pointer_cast<api::DeleteBucketReply>(
                    top.getReply(0)));
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(
                ReturnCode::OK,
                reply->getResult().getResult());
        CPPUNIT_ASSERT_EQUAL(api::BucketInfo(), reply->getBucketInfo());
    }
}

namespace {

    /**
     * Utility storage link, sending data to the given links instead of through
     * a regular chain.
     */
    struct MidLink : public StorageLink {
        StorageLink& _up;

    public:
        MidLink(std::unique_ptr<StorageLink> down, StorageLink& up)
            : StorageLink("MidLink"), _up(up)
        {
            push_back(std::move(down));
        }
        ~MidLink() {
            closeNextLink();
        }

        void print(std::ostream& out, bool, const std::string&) const override { out << "MidLink"; }
        bool onUp(const std::shared_ptr<api::StorageMessage> & msg) override {
            if (!StorageLinkTest::callOnUp(_up, msg)) _up.sendUp(msg);
            return true;
        }

    };

    /**
     * Utility class, connecting two storage links below it, sending
     * messages coming up from one down the other (providing address is set
     * correctly.)
     */
    class BinaryStorageLink : public DummyStorageLink {
        vespalib::Lock _lock;
        std::set<api::StorageMessage::Id> _seen;
        MidLink _left;
        MidLink _right;
        uint16_t _leftAddr;
        uint16_t _rightAddr;

    public:
        BinaryStorageLink(uint16_t leftAddr, std::unique_ptr<StorageLink> left,
                          uint16_t rightAddr, std::unique_ptr<StorageLink> right)
            : _left(std::move(left), *this),
              _right(std::move(right), *this),
              _leftAddr(leftAddr),
              _rightAddr(rightAddr) {}

        void print(std::ostream& out, bool, const std::string&) const override { out << "BinaryStorageLink"; }

        bool onDown(const std::shared_ptr<api::StorageMessage> & msg) override {
//            LOG(debug, "onDown Received msg: ->%s, %s %llu\n", msg->getAddress() ? msg->getAddress()->toString().c_str() : "(null)", msg->toString().c_str(), msg->getMsgId());

            vespalib::LockGuard lock(_lock);
            _seen.insert(msg->getMsgId());
            return sendOn(msg);
        }

        bool sendOn(const std::shared_ptr<api::StorageMessage> & msg) {
            if (msg->getAddress()) {
                uint16_t address = msg->getAddress()->getIndex();
                if ((address == _leftAddr && !msg->getType().isReply()) ||
                    (address == _rightAddr && msg->getType().isReply()))
                {
                    if (!StorageLinkTest::callOnDown(_left, msg)) {
                        _left.sendDown(msg);
                    }
                } else if ((address == _rightAddr && !msg->getType().isReply()) ||
                           (address == _leftAddr && msg->getType().isReply()))
                {
                    if (!StorageLinkTest::callOnDown(_right, msg)) {
                        _right.sendDown(msg);
                    }
                } else {
                    std::ostringstream ost;
                    ost << "Address " << address << " is neither " << _leftAddr
                        << " or " << _rightAddr << " in message " << *msg
                        << ".\n";
                    CPPUNIT_FAIL(ost.str());
                }
            }
            return true;
        }

        bool onUp(const std::shared_ptr<api::StorageMessage> & msg) override {
            // LOG(debug, "onUp Received msg: ->%s, %s %llu\n", msg->getAddress() ? msg->getAddress()->toString().c_str() : "(null)", msg->toString().c_str(), msg->getMsgId());

            vespalib::LockGuard lock(_lock);
            std::set<api::StorageMessage::Id>::iterator it
                    = _seen.find(msg->getMsgId());
                // If message originated from the outside
            if (it != _seen.end()) {
                LOG(debug, "Have seen this message before, storing");

                _seen.erase(it);
                return DummyStorageLink::onUp(msg);
                // If it originated from below, send it down again.
            } else if (msg->getType() == api::MessageType::NOTIFYBUCKETCHANGE) {
                // Just throw away notify bucket change
                return true;
            } else {
                LOG(debug, "Never seen %s, sending on!",
                    msg->toString().c_str());

                return sendOn(msg);
            }
        }

        void onFlush(bool downwards) override {
            if (downwards) {
                _left.flush();
                _right.flush();
            }
        }
        void onOpen() override {
            _left.open();
            _right.open();
        }
        void onClose() override {
            _left.close();
            _right.close();
        }
    };
}

void
FileStorManagerTest::testNoTimestamps()
{
    TestName testName("testNoTimestamps");
        // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager =
            new FileStorManager(config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address(
            "storage", lib::NodeType::STORAGE, 3);
        // Creating a document to test with
    Document::SP doc(createDocument(
                "some content", "doc:crawler:http://www.ntnu.no/").release());
    document::BucketId bid(16, 4000);

    createBucket(bid, 0);

    // Putting it
    {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 0));
        cmd->setAddress(address);
        CPPUNIT_ASSERT_EQUAL((api::Timestamp)0, cmd->getTimestamp());
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode::REJECTED,
                             reply->getResult().getResult());
    }
        // Removing it
    {
        std::shared_ptr<api::RemoveCommand> cmd(
                new api::RemoveCommand(makeDocumentBucket(bid), doc->getId(), 0));
        cmd->setAddress(address);
        CPPUNIT_ASSERT_EQUAL((api::Timestamp)0, cmd->getTimestamp());
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::RemoveReply> reply(
                std::dynamic_pointer_cast<api::RemoveReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode::REJECTED,
                             reply->getResult().getResult());
    }
}

void
FileStorManagerTest::testEqualTimestamps()
{
    TestName testName("testEqualTimestamps");
        // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager =
            new FileStorManager(config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address(
            "storage", lib::NodeType::STORAGE, 3);
        // Creating a document to test with
    document::BucketId bid(16, 4000);

    createBucket(bid, 0);

    // Putting it
    {
        Document::SP doc(createDocument(
                "some content", "userdoc:crawler:4000:http://www.ntnu.no/")
                .release());
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 100));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode::OK, reply->getResult().getResult());
    }

    // Putting it on same timestamp again
    // (ok as doc is the same. Since merge can move doc to other copy we
    // have to accept this)
    {
        Document::SP doc(createDocument(
                "some content", "userdoc:crawler:4000:http://www.ntnu.no/")
                .release());
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 100));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode::OK, reply->getResult().getResult());
    }

    // Putting the doc with other id. Now we should fail
    {
        Document::SP doc(createDocument(
                "some content", "userdoc:crawler:4000:http://www.ntnu.nu/")
                .release());
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), doc, 100));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode::TIMESTAMP_EXIST,
                             reply->getResult().getResult());
    }
}

void
FileStorManagerTest::testGetIter()
{
    TestName testName("testGetIter");
        // Setting up manager
    DummyStorageLink top;
    FileStorManager *manager;
    top.push_back(unique_ptr<StorageLink>(manager =
            new FileStorManager(config->getConfigId(), _node->getPartitions(), _node->getPersistenceProvider(), _node->getComponentRegister())));
    top.open();
    api::StorageMessageAddress address(
            "storage", lib::NodeType::STORAGE, 3);
    document::BucketId bid(16, 4000);

    createBucket(bid, 0);

    std::vector<Document::SP > docs;
        // Creating some documents to test with
    for (uint32_t i=0; i<10; ++i) {
        std::ostringstream id;
        id << "userdoc:crawler:4000:http://www.ntnu.no/" << i;
        docs.push_back(
                Document::SP(
                    _node->getTestDocMan().createRandomDocumentAtLocation(
                        4000, i, 400, 400)));
    }
    api::BucketInfo bucketInfo;
        // Putting all docs to have something to visit
    for (uint32_t i=0; i<docs.size(); ++i) {
        std::shared_ptr<api::PutCommand> cmd(
                new api::PutCommand(makeDocumentBucket(bid), docs[i], 100 + i));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::PutReply> reply(
                std::dynamic_pointer_cast<api::PutReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        bucketInfo = reply->getBucketInfo();
    }
        // Sending a getiter request that will only visit some of the docs
    spi::IteratorId iterId(createIterator(top, bid, ""));
    {
        std::shared_ptr<GetIterCommand> cmd(
                new GetIterCommand(makeDocumentBucket(bid), iterId, 2048));
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<GetIterReply> reply(
                std::dynamic_pointer_cast<GetIterReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
        CPPUNIT_ASSERT(reply->getEntries().size() > 0);
        CPPUNIT_ASSERT(reply->getEntries().size() < docs.size());
    }
        // Normal case of get iter is testing through visitor tests.
        // Testing specific situation where file is deleted while visiting here
    {
        std::shared_ptr<api::DeleteBucketCommand> cmd(
                new api::DeleteBucketCommand(makeDocumentBucket(bid)));
        cmd->setBucketInfo(bucketInfo);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::DeleteBucketReply> reply(
                std::dynamic_pointer_cast<api::DeleteBucketReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
    }
    {
        auto cmd = std::make_shared<GetIterCommand>(makeDocumentBucket(bid), iterId, 2048);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL(size_t(1), top.getNumReplies());
        std::shared_ptr<GetIterReply> reply(
                std::dynamic_pointer_cast<GetIterReply>(
                    top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode::BUCKET_NOT_FOUND,
                             reply->getResult().getResult());
        CPPUNIT_ASSERT(reply->getEntries().empty());
    }
}

void
FileStorManagerTest::testSetBucketActiveState()
{
    TestName testName("testSetBucketActiveState");
    DummyStorageLink top;
    FileStorManager* manager(
            new FileStorManager(config->getConfigId(),
                                _node->getPartitions(),
                                _node->getPersistenceProvider(),
                                _node->getComponentRegister()));
    top.push_back(unique_ptr<StorageLink>(manager));
    setClusterState("storage:4 distributor:1");
    top.open();
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);

    document::BucketId bid(16, 4000);

    const uint16_t disk = 0;
    createBucket(bid, disk);
    spi::dummy::DummyPersistence& provider(
            dynamic_cast<spi::dummy::DummyPersistence&>(_node->getPersistenceProvider()));
    CPPUNIT_ASSERT(!provider.isActive(makeSpiBucket(bid, spi::PartitionId(disk))));

    {
        std::shared_ptr<api::SetBucketStateCommand> cmd(
                new api::SetBucketStateCommand(
                        makeDocumentBucket(bid), api::SetBucketStateCommand::ACTIVE));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::SetBucketStateReply> reply(
                std::dynamic_pointer_cast<api::SetBucketStateReply>(
                        top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
    }

    CPPUNIT_ASSERT(provider.isActive(makeSpiBucket(bid, spi::PartitionId(disk))));
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(
                        bid, "foo"));
        CPPUNIT_ASSERT(entry->info.isActive());
    }
    // Trigger bucket info to be read back into the database
    {
        std::shared_ptr<ReadBucketInfo> cmd(
                new ReadBucketInfo(makeDocumentBucket(bid)));
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<ReadBucketInfoReply> reply(
                std::dynamic_pointer_cast<ReadBucketInfoReply>(
                        top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
    }
    // Should not have lost active flag
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(
                        bid, "foo"));
        CPPUNIT_ASSERT(entry->info.isActive());
    }

    {
        std::shared_ptr<api::SetBucketStateCommand> cmd(
                new api::SetBucketStateCommand(
                        makeDocumentBucket(bid), api::SetBucketStateCommand::INACTIVE));
        cmd->setAddress(address);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, top.getNumReplies());
        std::shared_ptr<api::SetBucketStateReply> reply(
                std::dynamic_pointer_cast<api::SetBucketStateReply>(
                        top.getReply(0)));
        top.reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK), reply->getResult());
    }

    CPPUNIT_ASSERT(!provider.isActive(makeSpiBucket(bid, spi::PartitionId(disk))));
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(
                        bid, "foo"));
        CPPUNIT_ASSERT(!entry->info.isActive());
    }
}

void
FileStorManagerTest::testNotifyOwnerDistributorOnOutdatedSetBucketState()
{
    TestName testName("testNotifyOwnerDistributorOnOutdatedSetBucketState");
    DummyStorageLink top;
    FileStorManager* manager(
            new FileStorManager(config->getConfigId(),
                                _node->getPartitions(),
                                _node->getPersistenceProvider(),
                                _node->getComponentRegister()));
    top.push_back(unique_ptr<StorageLink>(manager));

    setClusterState("storage:2 distributor:2");
    top.open();
    
    document::BucketId bid(getFirstBucketNotOwnedByDistributor(0));
    CPPUNIT_ASSERT(bid.getRawId() != 0);
    createBucket(bid, 0);

    std::shared_ptr<api::SetBucketStateCommand> cmd(
            new api::SetBucketStateCommand(
                    makeDocumentBucket(bid), api::SetBucketStateCommand::ACTIVE));
    cmd->setAddress(api::StorageMessageAddress(
                            "cluster", lib::NodeType::STORAGE, 1));
    cmd->setSourceIndex(0);

    top.sendDown(cmd);
    top.waitForMessages(2, _waitTime);

    CPPUNIT_ASSERT_EQUAL(size_t(2), top.getNumReplies());
    // Not necessarily deterministic order.
    int idxOffset = 0;
    if (top.getReply(0)->getType() != api::MessageType::NOTIFYBUCKETCHANGE) {
        ++idxOffset;
    }
    std::shared_ptr<api::NotifyBucketChangeCommand> notifyCmd(
            std::dynamic_pointer_cast<api::NotifyBucketChangeCommand>(
                    top.getReply(idxOffset)));
    std::shared_ptr<api::SetBucketStateReply> stateReply(
            std::dynamic_pointer_cast<api::SetBucketStateReply>(
                    top.getReply(1 - idxOffset)));

    CPPUNIT_ASSERT(stateReply.get());
    CPPUNIT_ASSERT_EQUAL(ReturnCode(ReturnCode::OK),
                         stateReply->getResult());

    CPPUNIT_ASSERT(notifyCmd.get());
    CPPUNIT_ASSERT_EQUAL(uint16_t(1), notifyCmd->getAddress()->getIndex());
    // Not necessary for this to be set since distributor does not insert this
    // info into its db, but useful for debugging purposes.
    CPPUNIT_ASSERT(notifyCmd->getBucketInfo().isActive());
}

void
FileStorManagerTest::testGetBucketDiffImplicitCreateBucket()
{
    TestName testName("testGetBucketDiffImplicitCreateBucket");
    DummyStorageLink top;
    FileStorManager* manager(
            new FileStorManager(config->getConfigId(),
                                _node->getPartitions(),
                                _node->getPersistenceProvider(),
                                _node->getComponentRegister()));
    top.push_back(unique_ptr<StorageLink>(manager));
    setClusterState("storage:2 distributor:1");
    top.open();

    document::BucketId bid(16, 4000);

    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(1);
    nodes.push_back(0);

    std::shared_ptr<api::GetBucketDiffCommand> cmd(
            new api::GetBucketDiffCommand(makeDocumentBucket(bid), nodes, Timestamp(1000)));
    cmd->setAddress(api::StorageMessageAddress(
                            "cluster", lib::NodeType::STORAGE, 1));
    cmd->setSourceIndex(0);
    top.sendDown(cmd);

    api::GetBucketDiffReply* reply;
    ASSERT_SINGLE_REPLY(api::GetBucketDiffReply, reply, top, _waitTime);
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::OK),
                         reply->getResult());
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(
                        bid, "foo"));
        CPPUNIT_ASSERT(entry.exist());
        CPPUNIT_ASSERT(entry->info.isReady());
    }
}

void
FileStorManagerTest::testMergeBucketImplicitCreateBucket()
{
    TestName testName("testMergeBucketImplicitCreateBucket");
    DummyStorageLink top;
    FileStorManager* manager(
            new FileStorManager(config->getConfigId(),
                                _node->getPartitions(),
                                _node->getPersistenceProvider(),
                                _node->getComponentRegister()));
    top.push_back(unique_ptr<StorageLink>(manager));
    setClusterState("storage:3 distributor:1");
    top.open();

    document::BucketId bid(16, 4000);

    std::vector<api::MergeBucketCommand::Node> nodes;
    nodes.push_back(1);
    nodes.push_back(2);

    std::shared_ptr<api::MergeBucketCommand> cmd(
            new api::MergeBucketCommand(makeDocumentBucket(bid), nodes, Timestamp(1000)));
    cmd->setAddress(api::StorageMessageAddress(
                            "cluster", lib::NodeType::STORAGE, 1));
    cmd->setSourceIndex(0);
    top.sendDown(cmd);

    api::GetBucketDiffCommand* diffCmd;
    ASSERT_SINGLE_REPLY(api::GetBucketDiffCommand, diffCmd, top, _waitTime);
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(
                        bid, "foo"));
        CPPUNIT_ASSERT(entry.exist());
        CPPUNIT_ASSERT(entry->info.isReady());
    }
}

void
FileStorManagerTest::testNewlyCreatedBucketIsReady()
{
    TestName testName("testNewlyCreatedBucketIsReady");
    DummyStorageLink top;
    FileStorManager* manager(
            new FileStorManager(config->getConfigId(),
                                _node->getPartitions(),
                                _node->getPersistenceProvider(),
                                _node->getComponentRegister()));
    top.push_back(unique_ptr<StorageLink>(manager));
    setClusterState("storage:2 distributor:1");
    top.open();

    document::BucketId bid(16, 4000);

    std::shared_ptr<api::CreateBucketCommand> cmd(
            new api::CreateBucketCommand(makeDocumentBucket(bid)));
    cmd->setAddress(api::StorageMessageAddress(
                            "cluster", lib::NodeType::STORAGE, 1));
    cmd->setSourceIndex(0);
    top.sendDown(cmd);

    api::CreateBucketReply* reply;
    ASSERT_SINGLE_REPLY(api::CreateBucketReply, reply, top, _waitTime);
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::OK),
                         reply->getResult());
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(
                        bid, "foo"));
        CPPUNIT_ASSERT(entry.exist());
        CPPUNIT_ASSERT(entry->info.isReady());
        CPPUNIT_ASSERT(!entry->info.isActive());
    }
}

void
FileStorManagerTest::testCreateBucketSetsActiveFlagInDatabaseAndReply()
{
    TestFileStorComponents c(*this, "testNotifyOnSplitSourceOwnershipChanged");
    setClusterState("storage:2 distributor:1");

    document::BucketId bid(16, 4000);
    std::shared_ptr<api::CreateBucketCommand> cmd(
            new api::CreateBucketCommand(makeDocumentBucket(bid)));
    cmd->setAddress(api::StorageMessageAddress(
                            "cluster", lib::NodeType::STORAGE, 1));
    cmd->setSourceIndex(0);
    cmd->setActive(true);
    c.top.sendDown(cmd);

    api::CreateBucketReply* reply;
    ASSERT_SINGLE_REPLY(api::CreateBucketReply, reply, c.top, _waitTime);
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::OK),
                         reply->getResult());
    {
        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(
                        bid, "foo"));
        CPPUNIT_ASSERT(entry.exist());
        CPPUNIT_ASSERT(entry->info.isReady());
        CPPUNIT_ASSERT(entry->info.isActive());
    }
}

template <typename Metric>
void FileStorManagerTest::assert_request_size_set(TestFileStorComponents& c, std::shared_ptr<api::StorageMessage> cmd, const Metric& metric) {
    api::StorageMessageAddress address("storage", lib::NodeType::STORAGE, 3);
    cmd->setApproxByteSize(54321);
    cmd->setAddress(address);
    c.top.sendDown(cmd);
    c.top.waitForMessages(1, _waitTime);
    CPPUNIT_ASSERT_EQUAL(static_cast<int64_t>(cmd->getApproxByteSize()), metric.request_size.getLast());
}

void FileStorManagerTest::put_command_size_is_added_to_metric() {
    TestFileStorComponents c(*this, "put_command_size_is_added_to_metric");
    document::BucketId bucket(16, 4000);
    createBucket(bucket, 0);
    auto cmd = std::make_shared<api::PutCommand>(
            makeDocumentBucket(bucket), _node->getTestDocMan().createRandomDocument(), api::Timestamp(12345));

    assert_request_size_set(c, std::move(cmd), thread_metrics_of(*c.manager)->put[defaultLoadType]);
}

void FileStorManagerTest::update_command_size_is_added_to_metric() {
    TestFileStorComponents c(*this, "update_command_size_is_added_to_metric");
    document::BucketId bucket(16, 4000);
    createBucket(bucket, 0);
    auto update = std::make_shared<document::DocumentUpdate>(
            _node->getTestDocMan().createRandomDocument()->getType(),
            document::DocumentId("id:foo:testdoctype1::bar"));
    auto cmd = std::make_shared<api::UpdateCommand>(
            makeDocumentBucket(bucket), std::move(update), api::Timestamp(123456));

    assert_request_size_set(c, std::move(cmd), thread_metrics_of(*c.manager)->update[defaultLoadType]);
}

void FileStorManagerTest::remove_command_size_is_added_to_metric() {
    TestFileStorComponents c(*this, "remove_command_size_is_added_to_metric");
    document::BucketId bucket(16, 4000);
    createBucket(bucket, 0);
    auto cmd = std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(bucket), document::DocumentId("id:foo:testdoctype1::bar"), api::Timestamp(123456));

    assert_request_size_set(c, std::move(cmd), thread_metrics_of(*c.manager)->remove[defaultLoadType]);
}

void FileStorManagerTest::get_command_size_is_added_to_metric() {
    TestFileStorComponents c(*this, "get_command_size_is_added_to_metric");
    document::BucketId bucket(16, 4000);
    createBucket(bucket, 0);
    auto cmd = std::make_shared<api::GetCommand>(
            makeDocumentBucket(bucket), document::DocumentId("id:foo:testdoctype1::bar"), "[all]");

    assert_request_size_set(c, std::move(cmd), thread_metrics_of(*c.manager)->get[defaultLoadType]);
}

} // storage
