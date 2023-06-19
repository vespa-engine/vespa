// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <tests/common/teststorageapp.h>
#include <tests/persistence/filestorage/forwardingmessagesender.h>
#include <vespa/config/common/exceptions.h>
#include <vespa/document/fieldset/fieldsets.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/select/parser.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/update/assignvalueupdate.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/fieldvalue/stringfieldvalue.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/fastos/file.h>
#include <vespa/persistence/dummyimpl/dummypersistence.h>
#include <vespa/persistence/spi/test.h>
#include <vespa/persistence/spi/bucket_tasks.h>
#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/persistence/bucketownershipnotifier.h>
#include <vespa/storage/persistence/filestorage/filestorhandlerimpl.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storage/persistence/persistencethread.h>
#include <vespa/storage/persistence/persistencehandler.h>
#include <vespa/storage/storageserver/statemanager.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/size_literals.h>
#include <atomic>
#include <thread>

#include <vespa/log/log.h>
LOG_SETUP(".filestormanagertest");

using std::unique_ptr;
using document::Document;
using namespace storage::api;
using storage::spi::test::makeSpiBucket;
using document::test::makeDocumentBucket;
using vespalib::IDestructorCallback;
using namespace ::testing;

#define ASSERT_SINGLE_REPLY(replytype, reply, link, time) \
reply = nullptr; \
try{ \
    link.waitForMessages(1, time); \
    ASSERT_EQ(1, link.getNumReplies()); \
    reply = dynamic_cast<replytype*>(link.getReply(0).get()); \
    if (reply == nullptr) { \
        FAIL() << "Got reply of unexpected type: " \
               << link.getReply(0)->getType().toString(); \
    } \
} catch (vespalib::Exception& e) { \
    reply = nullptr; \
    FAIL() << "Failed to find single reply in time"; \
}

namespace storage {

namespace {

vespalib::string _Cluster("cluster");
vespalib::string _Storage("storage");
api::StorageMessageAddress _Storage2(&_Storage, lib::NodeType::STORAGE, 2);
api::StorageMessageAddress _Storage3(&_Storage, lib::NodeType::STORAGE, 3);
api::StorageMessageAddress _Cluster1(&_Cluster, lib::NodeType::STORAGE, 1);

struct TestFileStorComponents;

document::Bucket
make_bucket_for_doc(const document::DocumentId& docid)
{
    document::BucketIdFactory factory;
    document::BucketId bucket_id(16, factory.getBucketId(docid).getRawId());
    return makeDocumentBucket(bucket_id);
}

}

struct FileStorTestBase : Test {
    enum {LONG_WAITTIME=60};
    unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<vdstestlib::DirConfig> config;
    std::unique_ptr<vdstestlib::DirConfig> config2;
    std::unique_ptr<vdstestlib::DirConfig> smallConfig;
    const uint32_t _waitTime;
    const document::DocumentType* _testdoctype1;

    FileStorTestBase() : _node(), _waitTime(LONG_WAITTIME) {}
    ~FileStorTestBase();

    void SetUp() override;
    void TearDown() override;

    void createBucket(document::BucketId bid) {
        _node->getPersistenceProvider().createBucket(makeSpiBucket(bid));

        StorBucketDatabase::WrappedEntry entry(
                _node->getStorageBucketDatabase().get(bid, "foo", StorBucketDatabase::CREATE_IF_NONEXISTING));
        entry->info = api::BucketInfo(0, 0, 0, 0, 0, true, false);
        entry.write();
    }

    document::Document::UP createDocument(const std::string& content, const std::string& id) {
        return _node->getTestDocMan().createDocument(content, id);
    }

    std::shared_ptr<api::PutCommand> make_put_command(StorageMessage::Priority pri = 20,
                                                      const std::string& docid = "id:foo:testdoctype1::bar",
                                                      Timestamp timestamp = 100) {
        Document::SP doc(createDocument("my content", docid));
        auto bucket = make_bucket_for_doc(doc->getId());
        auto cmd = std::make_shared<api::PutCommand>(bucket, std::move(doc), timestamp);
        cmd->setPriority(pri);
        return cmd;
    }

    std::shared_ptr<api::GetCommand> make_get_command(StorageMessage::Priority pri,
                                                      const std::string& docid = "id:foo:testdoctype1::bar") {
        document::DocumentId did(docid);
        auto bucket = make_bucket_for_doc(did);
        auto cmd = std::make_shared<api::GetCommand>(bucket, did, document::AllFields::NAME);
        cmd->setPriority(pri);
        return cmd;
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
        return dynamic_cast<spi::dummy::DummyPersistence&>(_node->getPersistenceProvider());
    }

    void setClusterState(const std::string& state) {
        _node->getStateUpdater().setClusterState(
                lib::ClusterState::CSP(
                        new lib::ClusterState(state)));
    }

    void setupDisks() {
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
            _node = std::make_unique<TestServiceLayerApp>(NodeIndex(0), config->getConfigId());
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
        return manager.get_metrics().threads[0];
    }
};

FileStorTestBase::~FileStorTestBase() = default;

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

std::unique_ptr<DiskThread>
createThread(PersistenceHandler & persistenceHandler,
             FileStorHandler& filestorHandler,
             framework::Component & component)
{
    return std::make_unique<PersistenceThread>(persistenceHandler, filestorHandler, 0, component);
}

namespace {

struct TestFileStorComponents {
    DummyStorageLink top;
    FileStorManager* manager;

    explicit TestFileStorComponents(FileStorTestBase& test, bool use_small_config = false)
        : manager(nullptr)
    {
        auto fsm = std::make_unique<FileStorManager>(config::ConfigUri((use_small_config ? test.smallConfig : test.config)->getConfigId()),
                                                     test._node->getPersistenceProvider(),
                                                     test._node->getComponentRegister(), *test._node, test._node->get_host_info());
        manager = fsm.get();
        top.push_back(std::move(fsm));
        top.open();
    }
};

struct FileStorHandlerComponents {
    DummyStorageLink top;
    DummyStorageLink* dummyManager;
    ForwardingMessageSender messageSender;
    FileStorMetrics metrics;
    std::unique_ptr<FileStorHandler> filestorHandler;

    FileStorHandlerComponents(FileStorTestBase& test, uint32_t threadsPerDisk = 1)
        : top(),
          dummyManager(new DummyStorageLink),
          messageSender(*dummyManager),
          metrics(),
          filestorHandler()
    {
        top.push_back(std::unique_ptr<StorageLink>(dummyManager));
        top.open();

        metrics.initDiskMetrics(1, threadsPerDisk);

        filestorHandler = std::make_unique<FileStorHandlerImpl>(messageSender, metrics, test._node->getComponentRegister());
        filestorHandler->setGetNextMessageTimeout(50ms);
    }
    ~FileStorHandlerComponents();
};

FileStorHandlerComponents::~FileStorHandlerComponents() = default;

struct PersistenceHandlerComponents : public FileStorHandlerComponents {
    vespalib::ISequencedTaskExecutor& executor;
    ServiceLayerComponent component;
    BucketOwnershipNotifier bucketOwnershipNotifier;
    std::unique_ptr<PersistenceHandler> persistenceHandler;

    PersistenceHandlerComponents(FileStorTestBase& test)
        : FileStorHandlerComponents(test),
          executor(test._node->executor()),
          component(test._node->getComponentRegister(), "test"),
          bucketOwnershipNotifier(component, messageSender),
          persistenceHandler()
    {
        vespa::config::content::StorFilestorConfig cfg;
        persistenceHandler =
                std::make_unique<PersistenceHandler>(executor, component, cfg,
                                                     test._node->getPersistenceProvider(),
                                                     *filestorHandler, bucketOwnershipNotifier,
                                                     *metrics.threads[0]);
    }
    ~PersistenceHandlerComponents();
    std::unique_ptr<DiskThread> make_disk_thread() {
        return createThread(*persistenceHandler, *filestorHandler, component);
    }
};

PersistenceHandlerComponents::~PersistenceHandlerComponents()
{
    // Ensure any pending tasks have completed before destroying any resources they
    // may implicitly depend on.
    executor.sync_all();
}

}

void
FileStorTestBase::SetUp()
{
    setupDisks();
}

void
FileStorTestBase::TearDown()
{
    _node.reset();
}

struct FileStorManagerTest : public FileStorTestBase {

};

TEST_F(FileStorManagerTest, header_only_put) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    // Creating a document to test with
    Document::SP doc(createDocument(
                "some content", "id:crawler:testdoctype1:n=4000:foo").release());

    document::BucketId bid(16, 4000);

    createBucket(bid);

    // Putting it
    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 105);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
        EXPECT_EQ(1, reply->getBucketInfo().getDocumentCount());
    }
    doc->setValue(doc->getField("headerval"), document::IntFieldValue(42));
    // Putting it again, this time with header only
    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 124);
        cmd->setUpdateTimestamp(105);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode::OK, reply->getResult().getResult());
    }
    // Getting it
    {
        auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(bid), doc->getId(), document::AllFields::NAME);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        std::shared_ptr<api::GetReply> reply2(
                std::dynamic_pointer_cast<api::GetReply>(
                    top.getReply(0)));
        top.reset();
        ASSERT_TRUE(reply2.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply2->getResult());
        EXPECT_EQ(doc->getId().toString(), reply2->getDocumentId().toString());
        // Ensure partial update was done, but other things are equal
        auto value = reply2->getDocument()->getValue(doc->getField("headerval"));
        ASSERT_TRUE(value.get());
        EXPECT_EQ(42, dynamic_cast<document::IntFieldValue&>(*value).getAsInt());
        reply2->getDocument()->remove("headerval");
        doc->remove("headerval");
        EXPECT_EQ(*doc, *reply2->getDocument());
    }
}

TEST_F(FileStorManagerTest, put) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    // Creating a document to test with
    Document::SP doc(createDocument(
                "some content", "id:crawler:testdoctype1:n=4000:foo").release());

    document::BucketId bid(16, 4000);

    createBucket(bid);

    // Putting it
    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 105);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
        EXPECT_EQ(1, reply->getBucketInfo().getDocumentCount());
    }
}

TEST_F(FileStorManagerTest, running_task_against_unknown_bucket_fails) {
    TestFileStorComponents c(*this);

    setClusterState("storage:3 distributor:3");
    EXPECT_TRUE(getDummyPersistence().getClusterState().nodeUp());

    auto executor = getDummyPersistence().get_bucket_executor();
    ASSERT_TRUE(executor);

    spi::Bucket b1 = makeSpiBucket(document::BucketId(1));
    std::atomic<size_t> success(0);
    std::atomic<size_t> failures(0);
    auto task = spi::makeBucketTask([&success](const spi::Bucket &, std::shared_ptr<IDestructorCallback>) { success++;},
                                    [&failures](const spi::Bucket &) { failures++; });
    executor->execute(b1, std::move(task));
    EXPECT_EQ(0, success);
    EXPECT_EQ(1, failures);
}

TEST_F(FileStorManagerTest, running_task_against_existing_bucket_works) {
    TestFileStorComponents c(*this);

    setClusterState("storage:3 distributor:3");
    EXPECT_TRUE(getDummyPersistence().getClusterState().nodeUp());

    auto executor = getDummyPersistence().get_bucket_executor();
    ASSERT_TRUE(executor);

    spi::Bucket b1 = makeSpiBucket(document::BucketId(1));

    createBucket(b1.getBucketId());

    std::atomic<size_t> success(0);
    std::atomic<size_t> failures(0);
    vespalib::Gate gate;
    executor->execute(b1, spi::makeBucketTask([&success, &gate](const spi::Bucket &, std::shared_ptr<IDestructorCallback>) {
        success++;
        gate.countDown();
    }, [&failures](const spi::Bucket &) { failures++; }));
    gate.await();
    EXPECT_EQ(1, success);
    EXPECT_EQ(0, failures);
}

TEST_F(FileStorManagerTest, state_change) {
    TestFileStorComponents c(*this);

    setClusterState("storage:3 distributor:3");
    EXPECT_TRUE(getDummyPersistence().getClusterState().nodeUp());

    setClusterState("storage:3 .0.s:d distributor:3");
    EXPECT_FALSE(getDummyPersistence().getClusterState().nodeUp());
}

TEST_F(FileStorManagerTest, flush) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    // Creating a document to test with

    document::DocumentId docId("id:ns:testdoctype1::crawler:http://www.ntnu.no/");
    auto doc = std::make_shared<Document>(*_node->getTypeRepo(), *_testdoctype1, docId);
    document::BucketId bid(4000);

    static const uint32_t msgCount = 10;

    // Generating many put commands
    std::vector<std::shared_ptr<api::StorageCommand> > _commands;
    for (uint32_t i=0; i<msgCount; ++i) {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, i+1);
        cmd->setAddress(_Storage3);
        _commands.push_back(cmd);
    }
    for (uint32_t i=0; i<msgCount; ++i) {
        top.sendDown(_commands[i]);
    }
    top.close();
    top.flush();
    EXPECT_EQ(msgCount, top.getNumReplies());
}

TEST_F(FileStorManagerTest, handler_priority) {
    FileStorHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;
    uint32_t stripeId = 0;

    std::string content("Here is some content which is in all documents");
    std::ostringstream uri;

    Document::SP doc(createDocument(content, "id:footype:testdoctype1:n=1234:bar").release());

    document::BucketIdFactory factory;
    document::BucketId bucket(16, factory.getBucketId(doc->getId()).getRawId());

    // Populate bucket with the given data
    for (uint32_t i = 1; i < 6; i++) {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), doc, 100);
        cmd->setAddress(_Storage3);
        cmd->setPriority(i * 15);
        filestorHandler.schedule(cmd);
    }

    ASSERT_EQ(15, filestorHandler.getNextMessage(stripeId).msg->getPriority());
    ASSERT_EQ(30, filestorHandler.getNextMessage(stripeId).msg->getPriority());
    ASSERT_EQ(45, filestorHandler.getNextMessage(stripeId).msg->getPriority());
    ASSERT_EQ(60, filestorHandler.getNextMessage(stripeId).msg->getPriority());
    ASSERT_EQ(75, filestorHandler.getNextMessage(stripeId).msg->getPriority());
}

class MessagePusherThread {
public:
    FileStorHandler& _handler;
    Document::SP _doc;
    std::atomic<bool> _done;
    std::atomic<bool> _threadDone;
    std::thread _thread;
    
    MessagePusherThread(FileStorHandler& handler, Document::SP doc);
    ~MessagePusherThread();

    void run() {
        while (!_done) {
            document::BucketIdFactory factory;
            document::BucketId bucket(16, factory.getBucketId(_doc->getId()).getRawId());

            auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), _doc, 100);
            _handler.schedule(cmd);
            std::this_thread::sleep_for(1ms);
        }

        _threadDone = true;
    }
};

MessagePusherThread::MessagePusherThread(FileStorHandler& handler, Document::SP doc)
  : _handler(handler), _doc(std::move(doc)), _done(false), _threadDone(false), _thread()
{
    _thread = std::thread([this](){run();});
}
MessagePusherThread::~MessagePusherThread()
{
    _thread.join();
}

class MessageFetchingThread {
public:
    const uint32_t _threadId;
    FileStorHandler& _handler;
    std::atomic<uint32_t> _config;
    std::atomic<uint32_t> _fetchedCount;
    std::atomic<bool> _done;
    std::atomic<bool> _failed;
    std::atomic<bool> _threadDone;
    std::thread _thread;
    
    explicit MessageFetchingThread(FileStorHandler& handler)
        : _threadId(0), _handler(handler), _config(0), _fetchedCount(0), _done(false),
          _failed(false), _threadDone(false), _thread()
    {
        _thread = std::thread([this](){run();});
    }
    ~MessageFetchingThread();
    
    void run() {
        while (!_done) {
            FileStorHandler::LockedMessage msg = _handler.getNextMessage(_threadId);
            if (msg.msg.get()) {
                uint32_t originalConfig = _config.load();
                _fetchedCount++;
                std::this_thread::sleep_for(5ms);

                if (_config.load() != originalConfig) {
                    _failed = true;
                }
            } else {
                std::this_thread::sleep_for(1ms);
            }
        }

        _threadDone = true;
    };
};
MessageFetchingThread::~MessageFetchingThread()
{
    _thread.join();
}

TEST_F(FileStorManagerTest, handler_paused_multi_thread) {
    FileStorHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;

    std::string content("Here is some content which is in all documents");
    std::ostringstream uri;

    Document::SP doc(createDocument(content, "id:footype:testdoctype1:n=1234:bar").release());

    MessagePusherThread pushthread(filestorHandler, doc);
    MessageFetchingThread thread(filestorHandler);

    for (uint32_t i = 0; i < 50; ++i) {
        std::this_thread::sleep_for(2ms);
        ResumeGuard guard = filestorHandler.pause();
        thread._config.fetch_add(1);
        uint32_t count = thread._fetchedCount;
        ASSERT_EQ(count, thread._fetchedCount.load());
    }

    pushthread._done = true;
    thread._done = true;
    ASSERT_FALSE(thread._failed);

    while (!pushthread._threadDone || !thread._threadDone) {
        std::this_thread::sleep_for(1ms);
    }
}

TEST_F(FileStorManagerTest, handler_pause) {
    FileStorHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;
    uint32_t stripeId = 0;

    std::string content("Here is some content which is in all documents");
    std::ostringstream uri;

    Document::SP doc(createDocument(content, "id:footype:testdoctype1:n=1234:bar").release());

    document::BucketIdFactory factory;
    document::BucketId bucket(16, factory.getBucketId(doc->getId()).getRawId());

    // Populate bucket with the given data
    for (uint32_t i = 1; i < 6; i++) {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), doc, 100);
        cmd->setAddress(_Storage3);
        cmd->setPriority(i * 15);
        filestorHandler.schedule(cmd);
    }

    ASSERT_EQ(15, filestorHandler.getNextMessage(stripeId).msg->getPriority());

    {
        ResumeGuard guard = filestorHandler.pause();
        (void)guard;
        ASSERT_EQ(filestorHandler.getNextMessage(stripeId).msg.get(), nullptr);
    }

    ASSERT_EQ(30, filestorHandler.getNextMessage(stripeId).msg->getPriority());
}

TEST_F(FileStorManagerTest, remap_split) {
    FileStorHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;

    std::string content("Here is some content which is in all documents");

    Document::SP doc1(createDocument(content, "id:footype:testdoctype1:n=1234:bar").release());

    Document::SP doc2(createDocument(content, "id:footype:testdoctype1:n=4567:bar").release());

    document::BucketIdFactory factory;
    document::BucketId bucket1(16, 1234);
    document::BucketId bucket2(16, 4567);

    // Populate bucket with the given data
    for (uint32_t i = 1; i < 4; i++) {
        filestorHandler.schedule(std::make_shared<api::PutCommand>(makeDocumentBucket(bucket1), doc1, i));
        filestorHandler.schedule(std::make_shared<api::PutCommand>(makeDocumentBucket(bucket2), doc2, i + 10));
    }

    EXPECT_EQ("BucketId(0x40000000000004d2): Put(BucketId(0x40000000000004d2), id:footype:testdoctype1:n=1234:bar, timestamp 1, size 118) (priority: 127)\n"
              "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), id:footype:testdoctype1:n=4567:bar, timestamp 11, size 118) (priority: 127)\n"
              "BucketId(0x40000000000004d2): Put(BucketId(0x40000000000004d2), id:footype:testdoctype1:n=1234:bar, timestamp 2, size 118) (priority: 127)\n"
              "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), id:footype:testdoctype1:n=4567:bar, timestamp 12, size 118) (priority: 127)\n"
              "BucketId(0x40000000000004d2): Put(BucketId(0x40000000000004d2), id:footype:testdoctype1:n=1234:bar, timestamp 3, size 118) (priority: 127)\n"
              "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), id:footype:testdoctype1:n=4567:bar, timestamp 13, size 118) (priority: 127)\n",
              filestorHandler.dumpQueue());

    FileStorHandler::RemapInfo a(makeDocumentBucket(document::BucketId(17, 1234)));
    FileStorHandler::RemapInfo b(makeDocumentBucket(document::BucketId(17, 1234 | 1 << 16)));
    filestorHandler.remapQueueAfterSplit(FileStorHandler::RemapInfo(makeDocumentBucket(bucket1)), a, b);

    ASSERT_TRUE(a.foundInQueue);
    ASSERT_FALSE(b.foundInQueue);

    EXPECT_EQ("BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), id:footype:testdoctype1:n=4567:bar, timestamp 11, size 118) (priority: 127)\n"
              "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), id:footype:testdoctype1:n=4567:bar, timestamp 12, size 118) (priority: 127)\n"
              "BucketId(0x40000000000011d7): Put(BucketId(0x40000000000011d7), id:footype:testdoctype1:n=4567:bar, timestamp 13, size 118) (priority: 127)\n"
              "BucketId(0x44000000000004d2): Put(BucketId(0x44000000000004d2), id:footype:testdoctype1:n=1234:bar, timestamp 1, size 118) (priority: 127)\n"
              "BucketId(0x44000000000004d2): Put(BucketId(0x44000000000004d2), id:footype:testdoctype1:n=1234:bar, timestamp 2, size 118) (priority: 127)\n"
              "BucketId(0x44000000000004d2): Put(BucketId(0x44000000000004d2), id:footype:testdoctype1:n=1234:bar, timestamp 3, size 118) (priority: 127)\n",
              filestorHandler.dumpQueue());
}

TEST_F(FileStorManagerTest, handler_timeout) {
    FileStorHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;
    uint32_t stripeId = 0;

    std::string content("Here is some content which is in all documents");
    std::ostringstream uri;

    Document::SP doc(createDocument(content, "id:footype:testdoctype1:n=1234:bar").release());

    document::BucketIdFactory factory;
    document::BucketId bucket(16, factory.getBucketId(doc->getId()).getRawId());

    // Populate bucket with the given data
    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), doc, 100);
        cmd->setAddress(_Storage3);
        cmd->setPriority(0);
        cmd->setTimeout(50ms);
        filestorHandler.schedule(cmd);
    }

    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), doc, 100);
        cmd->setAddress(_Storage3);
        cmd->setPriority(200);
        cmd->setTimeout(10000ms);
        filestorHandler.schedule(cmd);
    }

    std::this_thread::sleep_for(51ms);
    for (;;) {
        auto lock = filestorHandler.getNextMessage(stripeId);
        if (lock.lock.get()) {
            ASSERT_EQ(200, lock.msg->getPriority());
            break;
        }
    }

    ASSERT_EQ(1, c.top.getNumReplies());
    EXPECT_EQ(api::ReturnCode::TIMEOUT,
              static_cast<api::StorageReply&>(*c.top.getReply(0)).getResult().getResult());
}

TEST_F(FileStorManagerTest, priority) {
    FileStorHandlerComponents c(*this, 2);
    auto& filestorHandler = *c.filestorHandler;
    auto& metrics = c.metrics;

    ServiceLayerComponent component(_node->getComponentRegister(), "test");
    BucketOwnershipNotifier bucketOwnershipNotifier(component, c.messageSender);
    vespa::config::content::StorFilestorConfig cfg;
    PersistenceHandler persistenceHandler(_node->executor(), component, cfg, _node->getPersistenceProvider(),
                                          filestorHandler, bucketOwnershipNotifier, *metrics.threads[0]);
    std::unique_ptr<DiskThread> thread(createThread(persistenceHandler, filestorHandler, component));

    PersistenceHandler persistenceHandler2(_node->executor(), component, cfg, _node->getPersistenceProvider(),
                                           filestorHandler, bucketOwnershipNotifier, *metrics.threads[1]);
    std::unique_ptr<DiskThread> thread2(createThread(persistenceHandler2, filestorHandler, component));

    // Creating documents to test with. Different gids, 2 locations.
    std::vector<document::Document::SP > documents;
    for (uint32_t i=0; i<50; ++i) {
        std::string content("Here is some content which is in all documents");
        std::ostringstream uri;

        uri << "id:footype:testdoctype1:n=" << (i % 3 == 0 ? 0x10001 : 0x0100001)<< ":mydoc-" << i;
        Document::SP doc(createDocument(content, uri.str()).release());
        documents.push_back(doc);
    }

    document::BucketIdFactory factory;

    // Create buckets in separate, initial pass to avoid races with puts
    for (uint32_t i=0; i<documents.size(); ++i) {
        document::BucketId bucket(16, factory.getBucketId(documents[i]->getId()).getRawId());
        _node->getPersistenceProvider().createBucket(makeSpiBucket(bucket));
    }

    // Populate bucket with the given data
    for (uint32_t i=0; i<documents.size(); ++i) {
        document::BucketId bucket(16, factory.getBucketId(documents[i]->getId()).getRawId());

        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), documents[i], 100 + i);
        cmd->setAddress(_Storage3);
        cmd->setPriority(i * 2);
        filestorHandler.schedule(cmd);
    }

    filestorHandler.flush(true);

    // Wait until everything is done.
    int count = 0;
    while (documents.size() != c.top.getNumReplies() && count < 10000) {
        std::this_thread::sleep_for(10ms);
        count++;
    }
    ASSERT_LT(count, 10000);

    for (uint32_t i = 0; i < documents.size(); i++) {
        std::shared_ptr<api::PutReply> reply(std::dynamic_pointer_cast<api::PutReply>(c.top.getReply(i)));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
    }

    // Verify that thread 1 gets documents over 50 pri
    EXPECT_EQ(documents.size(),
              metrics.threads[0]->operations.getValue()
              + metrics.threads[1]->operations.getValue());
    // Closing file stor handler before threads are deleted, such that
    // file stor threads getNextMessage calls returns.
    filestorHandler.close();
    // Sync any tasks that may be pending for the handlers on the stack.
    _node->executor().sync_all();
}

TEST_F(FileStorManagerTest, split1) {
    PersistenceHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;
    auto& top = c.top;
    auto thread = c.make_disk_thread();

    setClusterState("storage:2 distributor:1");
    // Creating documents to test with. Different gids, 2 locations.
    std::vector<document::Document::SP > documents;
    for (uint32_t i=0; i<20; ++i) {
        std::string content("Here is some content which is in all documents");
        std::ostringstream uri;

        uri << "id:footype:testdoctype1:n=" << (i % 3 == 0 ? 0x10001 : 0x0100001)
                               << ":mydoc-" << i;
        Document::SP doc(createDocument(content, uri.str()).release());
        documents.push_back(doc);
    }
    document::BucketIdFactory factory;
    {
        // Populate bucket with the given data
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(16, factory.getBucketId(documents[i]->getId()).getRawId());

            _node->getPersistenceProvider().createBucket(makeSpiBucket(bucket));

            auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), documents[i], 100 + i);
            cmd->setAddress(_Storage3);
            cmd->setSourceIndex(0);

            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            LOG(debug, "Got %zu replies", top.getNumReplies());
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            top.reset();

            // Delete every 5th document to have delete entries in file too
            if (i % 5 == 0) {
                auto rcmd = std::make_shared<api::RemoveCommand>(makeDocumentBucket(bucket), documents[i]->getId(), 1000000 + 100 + i);
                rcmd->setAddress(_Storage3);
                filestorHandler.schedule(rcmd);
                filestorHandler.flush(true);
                ASSERT_EQ(1, top.getNumReplies());
                auto rreply = std::dynamic_pointer_cast<api::RemoveReply>(top.getReply(0));
                ASSERT_TRUE(rreply.get()) << top.getReply(0)->getType().toString();
                EXPECT_EQ(ReturnCode(ReturnCode::OK), rreply->getResult());
                top.reset();
            }
        }

        // Perform a split, check that locations are split
        {
            auto cmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(document::BucketId(16, 1)));
            cmd->setSourceIndex(0);
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::SplitBucketReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            top.reset();
        }

        // Test that the documents have gotten into correct parts.
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(17, i % 3 == 0 ? 0x10001 : 0x0100001);
            auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(bucket), documents[i]->getId(), document::AllFields::NAME);
            cmd->setAddress(_Storage3);
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::GetReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(((i % 5) != 0), reply->wasFound());
            top.reset();
        }

        // Keep splitting location 1 until we gidsplit
        for (int i=17; i<=32; ++i) {
            auto cmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(document::BucketId(i, 0x0100001)));
            cmd->setSourceIndex(0);
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::SplitBucketReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            top.reset();
        }

        // Test that the documents have gotten into correct parts.
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket;
            if (i % 3 == 0) {
                bucket = document::BucketId(17, 0x10001);
            } else {
                bucket = document::BucketId(33, factory.getBucketId(documents[i]->getId()).getRawId());
            }
            auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(bucket), documents[i]->getId(), document::AllFields::NAME);
            cmd->setAddress(_Storage3);
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::GetReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(((i % 5) != 0), reply->wasFound());
            top.reset();
        }
    }
    // Closing file stor handler before threads are deleted, such that
    // file stor threads getNextMessage calls returns.
    filestorHandler.close();
}

TEST_F(FileStorManagerTest, split_single_group) {
    PersistenceHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;
    auto& top = c.top;

    setClusterState("storage:2 distributor:1");
    for (uint32_t j=0; j<1; ++j) {
        // Test this twice, once where all the data ends up in file with
        // splitbit set, and once where all the data ends up in file with
        // splitbit unset
        bool state = (j == 0);

        auto thread = c.make_disk_thread();
        // Creating documents to test with. Different gids, 2 locations.
        std::vector<document::Document::SP> documents;
        for (uint32_t i=0; i<20; ++i) {
            std::string content("Here is some content for all documents");
            std::ostringstream uri;

            uri << "id:footype:testdoctype1:n=" << (state ? 0x10001 : 0x0100001) << ":mydoc-" << i;
            documents.emplace_back(createDocument(content, uri.str()));
        }
        document::BucketIdFactory factory;

        // Populate bucket with the given data
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(16, factory.getBucketId(documents[i]->getId()).getRawId());

            _node->getPersistenceProvider().createBucket(makeSpiBucket(bucket));

            auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), documents[i], 100 + i);
            cmd->setAddress(_Storage3);
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            top.reset();
        }
        // Perform a split, check that locations are split
        {
            auto cmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(document::BucketId(16, 1)));
            cmd->setSourceIndex(0);
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::SplitBucketReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            top.reset();
        }

        // Test that the documents are all still there
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(17, state ? 0x10001 : 0x00001);
            auto cmd = std::make_shared<api::GetCommand>(makeDocumentBucket(bucket), documents[i]->getId(), document::AllFields::NAME);
            cmd->setAddress(_Storage3);
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::GetReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            top.reset();
        }
        // Closing file stor handler before threads are deleted, such that
        // file stor threads getNextMessage calls returns.
        filestorHandler.close();
    }
}

void
FileStorTestBase::putDoc(DummyStorageLink& top,
                         FileStorHandler& filestorHandler,
                         const document::BucketId& target,
                         uint32_t docNum)
{
    document::BucketIdFactory factory;
    document::DocumentId docId(vespalib::make_string("id:ns:testdoctype1:n=%" PRIu64 ":%d", target.getId(), docNum));
    document::BucketId bucket(16, factory.getBucketId(docId).getRawId());
    //std::cerr << "doc bucket is " << bucket << " vs source " << source << "\n";
    _node->getPersistenceProvider().createBucket(makeSpiBucket(target));
    auto doc = std::make_shared<Document>(*_node->getTypeRepo(), *_testdoctype1, docId);
    auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(target), doc, docNum+1);
    cmd->setAddress(_Storage3);
    cmd->setPriority(120);
    filestorHandler.schedule(cmd);
    filestorHandler.flush(true);
    ASSERT_EQ(1, top.getNumReplies());
    std::shared_ptr<api::PutReply> reply(std::dynamic_pointer_cast<api::PutReply>(top.getReply(0)));
    ASSERT_TRUE(reply.get());
    ASSERT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
    top.reset();
}

TEST_F(FileStorManagerTest, split_empty_target_with_remapped_ops) {
    PersistenceHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;
    auto& top = c.top;

    setClusterState("storage:2 distributor:1");
    auto thread = c.make_disk_thread();

    document::BucketId source(16, 0x10001);

    for (uint32_t i=0; i<10; ++i) {
        ASSERT_NO_FATAL_FAILURE(putDoc(top, filestorHandler, source, i));
    }

    // Send split followed by a put that is bound for a target bucket that
    // will end up empty in the split itself. The split should notice this
    // and create the bucket explicitly afterwards in order to compensate for
    // the persistence provider deleting it internally.
    // Make sure we block the operation queue until we've scheduled all
    // the operations.
    auto resumeGuard = std::make_unique<ResumeGuard>(filestorHandler.pause());

    auto splitCmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(source));
    splitCmd->setPriority(120);
    splitCmd->setSourceIndex(0);

    document::DocumentId docId(
            vespalib::make_string("id:ns:testdoctype1:n=%d:1234", 0x100001));
    auto doc = std::make_shared<Document>(*_node->getTypeRepo(), *_testdoctype1, docId);
    auto putCmd = std::make_shared<api::PutCommand>(makeDocumentBucket(source), doc, 1001);
    putCmd->setAddress(_Storage3);
    putCmd->setPriority(120);

    filestorHandler.schedule(splitCmd);
    filestorHandler.schedule(putCmd);
    resumeGuard.reset(); // Unpause
    filestorHandler.flush(true);

    top.waitForMessages(2, _waitTime);

    ASSERT_EQ(2, top.getNumReplies());
    {
        auto reply = std::dynamic_pointer_cast<api::SplitBucketReply>(top.getReply(0));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
    }
    {
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(1));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
    }

    top.reset();
}

TEST_F(FileStorManagerTest, notify_on_split_source_ownership_changed) {
    PersistenceHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;
    auto& top = c.top;

    setClusterState("storage:2 distributor:2");
    auto thread = c.make_disk_thread();

    document::BucketId source(getFirstBucketNotOwnedByDistributor(0));
    createBucket(source);
    for (uint32_t i=0; i<10; ++i) {
        ASSERT_NO_FATAL_FAILURE(putDoc(top, filestorHandler, source, i));
    }

    auto splitCmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(source));
    splitCmd->setPriority(120);
    splitCmd->setSourceIndex(0); // Source not owned by this distributor.

    filestorHandler.schedule(splitCmd);
    filestorHandler.flush(true);
    top.waitForMessages(4, _waitTime); // 3 notify cmds + split reply

    ASSERT_EQ(4, top.getNumReplies());
    for (int i = 0; i < 3; ++i) {
        ASSERT_EQ(api::MessageType::NOTIFYBUCKETCHANGE, top.getReply(i)->getType());
    }

    auto reply = std::dynamic_pointer_cast<api::SplitBucketReply>(top.getReply(3));
    ASSERT_TRUE(reply.get());
    EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
}

TEST_F(FileStorManagerTest, join) {
    PersistenceHandlerComponents c(*this);
    auto& filestorHandler = *c.filestorHandler;
    auto& top = c.top;

    auto thread = c.make_disk_thread();
    // Creating documents to test with. Different gids, 2 locations.
    std::vector<document::Document::SP > documents;
    for (uint32_t i=0; i<20; ++i) {
        std::string content("Here is some content which is in all documents");
        std::ostringstream uri;
        uri << "id:footype:testdoctype1:n=" << (i % 3 == 0 ? 0x10001 : 0x0100001) << ":mydoc-" << i;
        documents.emplace_back(createDocument(content, uri.str()));
    }
    document::BucketIdFactory factory;

    createBucket(document::BucketId(17, 0x00001));
    createBucket(document::BucketId(17, 0x10001));

    {
        // Populate bucket with the given data
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(17, factory.getBucketId(documents[i]->getId()).getRawId());
            auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), documents[i], 100 + i);
            cmd->setAddress(_Storage3);
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            top.reset();
            // Delete every 5th document to have delete entries in file too
            if ((i % 5) == 0) {
                auto rcmd = std::make_shared<api::RemoveCommand>(
                        makeDocumentBucket(bucket), documents[i]->getId(), 1000000 + 100 + i);
                rcmd->setAddress(_Storage3);
                filestorHandler.schedule(rcmd);
                filestorHandler.flush(true);
                ASSERT_EQ(1, top.getNumReplies());
                auto rreply = std::dynamic_pointer_cast<api::RemoveReply>(top.getReply(0));
                ASSERT_TRUE(rreply.get()) << top.getReply(0)->getType().toString();
                EXPECT_EQ(ReturnCode(ReturnCode::OK), rreply->getResult());
                top.reset();
            }
        }
        LOG(debug, "Starting the actual join after populating data");
        // Perform a join, check that other files are gone
        {
            auto cmd = std::make_shared<api::JoinBucketsCommand>(makeDocumentBucket(document::BucketId(16, 1)));
            cmd->getSourceBuckets().emplace_back(document::BucketId(17, 0x00001));
            cmd->getSourceBuckets().emplace_back(document::BucketId(17, 0x10001));
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::JoinBucketsReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            top.reset();
        }
            // Test that the documents have gotten into the file.
        for (uint32_t i=0; i<documents.size(); ++i) {
            document::BucketId bucket(16, 1);
            auto cmd = std::make_shared<api::GetCommand>(
                    makeDocumentBucket(bucket), documents[i]->getId(), document::AllFields::NAME);
            cmd->setAddress(_Storage3);
            filestorHandler.schedule(cmd);
            filestorHandler.flush(true);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<api::GetReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(((i % 5) != 0), reply->wasFound());
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
               framework::MicroSecTime toTime = framework::MicroSecTime::max())
{
    spi::Bucket bucket(makeSpiBucket(bucketId));

    spi::Selection selection = spi::Selection(spi::DocumentSelection(docSel));
    selection.setFromTimestamp(spi::Timestamp(fromTime.getTime()));
    selection.setToTimestamp(spi::Timestamp(toTime.getTime()));
    auto createIterCmd = std::make_shared<CreateIteratorCommand>(
            makeDocumentBucket(bucket), selection,
            document::AllFields::NAME,
            spi::NEWEST_DOCUMENT_ONLY);
    link.sendDown(createIterCmd);
    link.waitForMessages(1, FileStorManagerTest::LONG_WAITTIME);
    assert(link.getNumReplies() == 1);
    auto reply = std::dynamic_pointer_cast<CreateIteratorReply>(link.getReply(0));
    assert(reply.get());
    link.reset();
    assert(reply->getResult().success());
    return reply->getIteratorId();
}

}

TEST_F(FileStorManagerTest, visiting) {
    TestFileStorComponents c(*this, true);
    auto& top = c.top;
    // Adding documents to two buckets which we are going to visit
    // We want one bucket in one slotfile, and one bucket with a file split
    uint32_t docCount = 50;
    std::vector<document::BucketId> ids = {
        document::BucketId(16, 1),
        document::BucketId(16, 2)
    };

    createBucket(ids[0]);
    createBucket(ids[1]);

    lib::RandomGen randomizer(523);
    for (uint32_t i=0; i<docCount; ++i) {
        std::string content("Here is some content which is in all documents");
        std::ostringstream uri;

        uri << "id:crawler:testdoctype1:n=" << (i < 3 ? 1 : 2) << ":"
            << randomizer.nextUint32() << ".html";
        Document::SP doc(createDocument(content, uri.str()));
        const document::DocumentType& type(doc->getType());
        if (i < 30) {
            doc->setValue(type.getField("hstringval"),
                          document::StringFieldValue("John Doe"));
        } else {
            doc->setValue(type.getField("hstringval"),
                          document::StringFieldValue("Jane Doe"));
        }
        auto cmd = std::make_shared<api::PutCommand>(
                makeDocumentBucket(ids[(i < 3) ? 0 : 1]), doc, i+1);
        top.sendDown(cmd);
    }
    top.waitForMessages(docCount, _waitTime);
    ASSERT_EQ(docCount, top.getNumReplies());
    // Check nodestate with splitting
    {
        api::BucketInfo info;
        for (uint32_t i=3; i<docCount; ++i) {
            auto reply = std::dynamic_pointer_cast<api::BucketInfoReply>(top.getReply(i));
            ASSERT_TRUE(reply.get());
            ASSERT_TRUE(reply->getResult().success()) << reply->getResult();

            info = reply->getBucketInfo();
        }
        EXPECT_EQ(docCount - 3, info.getDocumentCount());
    }
    top.reset();
    // Visit bucket with no split, using no selection
    {
        spi::IteratorId iterId(createIterator(top, ids[0], "true"));
        auto cmd = std::make_shared<GetIterCommand>(makeDocumentBucket(ids[0]), iterId, 16_Ki);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<GetIterReply>(top.getReply(0));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
        EXPECT_EQ(ids[0], reply->getBucketId());
        EXPECT_EQ(3, reply->getEntries().size());
        top.reset();
    }
    // Visit bucket with split, using selection
    {
        uint32_t totalDocs = 0;
        spi::IteratorId iterId(createIterator(top, ids[1], "testdoctype1.hstringval = \"John Doe\""));
        while (true) {
            auto cmd = std::make_shared<GetIterCommand>(makeDocumentBucket(ids[1]), iterId, 16_Ki);
            top.sendDown(cmd);
            top.waitForMessages(1, _waitTime);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<GetIterReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            EXPECT_EQ(ids[1], reply->getBucketId());
            totalDocs += reply->getEntries().size();
            top.reset();
            if (reply->isCompleted()) {
                break;
            }
        }
        EXPECT_EQ(27u, totalDocs);
    }
    // Visit bucket with min and max timestamps set
    {
        document::BucketId bucket(16, 2);
        spi::IteratorId iterId(
                createIterator(top,
                               ids[1],
                               "",
                               framework::MicroSecTime(30),
                               framework::MicroSecTime(40)));
        uint32_t totalDocs = 0;
        while (true) {
            auto cmd = std::make_shared<GetIterCommand>(makeDocumentBucket(ids[1]), iterId, 16_Ki);
            top.sendDown(cmd);
            top.waitForMessages(1, _waitTime);
            ASSERT_EQ(1, top.getNumReplies());
            auto reply = std::dynamic_pointer_cast<GetIterReply>(top.getReply(0));
            ASSERT_TRUE(reply.get());
            EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
            EXPECT_EQ(bucket, reply->getBucketId());
            totalDocs += reply->getEntries().size();
            top.reset();
            if (reply->isCompleted()) {
                break;
            }
        }
        EXPECT_EQ(11u, totalDocs);
    }

}

TEST_F(FileStorManagerTest, remove_location) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    document::BucketId bid(8, 0);

    createBucket(bid);

    // Adding some documents to be removed later
    for (uint32_t i=0; i<=10; ++i) {
        std::ostringstream docid;
        docid << "id:ns:testdoctype1:n=" << (i << 8) << ":foo";
        Document::SP doc(createDocument("some content", docid.str()));
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 1000 + i);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
        EXPECT_EQ(i + 1u, reply->getBucketInfo().getDocumentCount());
    }
    // Issuing remove location command
    {
        auto cmd = std::make_shared<api::RemoveLocationCommand>("id.user % 512 == 0", makeDocumentBucket(bid));
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::RemoveLocationReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
        EXPECT_EQ(5u, reply->getBucketInfo().getDocumentCount());
    }
}

TEST_F(FileStorManagerTest, delete_bucket) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    // Creating a document to test with
    document::DocumentId docId("id:crawler:testdoctype1:n=4000:http://www.ntnu.no/");
    auto doc = std::make_shared<Document>(*_node->getTypeRepo(), *_testdoctype1, docId);
    document::BucketId bid(16, 4000);

    createBucket(bid);

    api::BucketInfo bucketInfo;
    // Putting it
    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 105);
        cmd->setAddress(_Storage2);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());

        EXPECT_EQ(1, reply->getBucketInfo().getDocumentCount());
        bucketInfo = reply->getBucketInfo();
        top.reset();
    }

    // Delete bucket
    {
        auto cmd = std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bid));
        cmd->setAddress(_Storage2);
        cmd->setBucketInfo(bucketInfo);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::DeleteBucketReply>(top.getReply(0));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
    }
}

TEST_F(FileStorManagerTest, delete_bucket_rejects_outdated_bucket_info) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    // Creating a document to test with
    document::DocumentId docId("id:crawler:testdoctype1:n=4000:http://www.ntnu.no/");
    auto doc = std::make_shared<Document>(*_node->getTypeRepo(), *_testdoctype1, docId);
    document::BucketId bid(16, 4000);

    createBucket(bid);

    api::BucketInfo bucketInfo;

    // Putting it
    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 105);
        cmd->setAddress(_Storage2);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());

        EXPECT_EQ(1, reply->getBucketInfo().getDocumentCount());
        bucketInfo = reply->getBucketInfo();
        top.reset();
    }

    // Attempt to delete bucket, but with non-matching bucketinfo
    {
        auto cmd = std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bid));
        cmd->setBucketInfo(api::BucketInfo(0xf000baaa, 1, 123, 1, 456));
        cmd->setAddress(_Storage2);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::DeleteBucketReply>(top.getReply(0));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode::REJECTED, reply->getResult().getResult());
        EXPECT_EQ(bucketInfo, reply->getBucketInfo());
    }
}

/**
 * Test that receiving a DeleteBucketCommand with invalid
 * BucketInfo deletes the bucket and does not fail the operation.
 */
TEST_F(FileStorManagerTest, delete_bucket_with_invalid_bucket_info){
    TestFileStorComponents c(*this);
    auto& top = c.top;
    // Creating a document to test with
    document::DocumentId docId("id:crawler:testdoctype1:n=4000:http://www.ntnu.no/");
    auto doc = std::make_shared<Document>(*_node->getTypeRepo(), *_testdoctype1, docId);
    document::BucketId bid(16, 4000);

    createBucket(bid);

    // Putting it
    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 105);
        cmd->setAddress(_Storage2);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
        EXPECT_EQ(1, reply->getBucketInfo().getDocumentCount());
        top.reset();
    }

    // Attempt to delete bucket with invalid bucketinfo
    {
        auto cmd = std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bid));
        cmd->setAddress(_Storage2);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::DeleteBucketReply>(top.getReply(0));
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode::OK, reply->getResult().getResult());
        EXPECT_EQ(api::BucketInfo(), reply->getBucketInfo());
    }
}

TEST_F(FileStorManagerTest, no_timestamps) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    // Creating a document to test with
    Document::SP doc(createDocument(
                "some content", "id:ns:testdoctype1::crawler:http://www.ntnu.no/").release());
    document::BucketId bid(16, 4000);

    createBucket(bid);

    // Putting it
    {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 0);
        cmd->setAddress(_Storage3);
        EXPECT_EQ(api::Timestamp(0), cmd->getTimestamp());
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode::REJECTED, reply->getResult().getResult());
    }
    // Removing it
    {
        auto cmd = std::make_shared<api::RemoveCommand>(makeDocumentBucket(bid), doc->getId(), 0);
        cmd->setAddress(_Storage3);
        EXPECT_EQ(api::Timestamp(0), cmd->getTimestamp());
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::RemoveReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode::REJECTED, reply->getResult().getResult());
    }
}

TEST_F(FileStorManagerTest, equal_timestamps) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    // Creating a document to test with
    document::BucketId bid(16, 4000);

    createBucket(bid);

    // Putting it
    {
        Document::SP doc(createDocument(
                "some content", "id:crawler:testdoctype1:n=4000:http://www.ntnu.no/"));
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 100);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode::OK, reply->getResult().getResult());
    }

    // Putting it on same timestamp again
    // (ok as doc is the same. Since merge can move doc to other copy we
    // have to accept this)
    {
        Document::SP doc(createDocument(
                "some content", "id:crawler:testdoctype1:n=4000:http://www.ntnu.no/"));
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 100);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode::OK, reply->getResult().getResult());
    }

    // Putting the doc with other id. Now we should fail
    {
        Document::SP doc(createDocument(
                "some content", "id:crawler:testdoctype1:n=4000:http://www.ntnu.nu/"));
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), doc, 100);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode::TIMESTAMP_EXIST, reply->getResult().getResult());
    }
}

TEST_F(FileStorManagerTest, get_iter) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    document::BucketId bid(16, 4000);

    createBucket(bid);

    std::vector<Document::SP > docs;
    // Creating some documents to test with
    for (uint32_t i=0; i<10; ++i) {
        std::ostringstream id;
        id << "id:crawler:testdoctype1:n=4000:http://www.ntnu.no/" << i;
        docs.emplace_back(
                Document::SP(
                    _node->getTestDocMan().createRandomDocumentAtLocation(
                        4000, i, 400, 400)));
    }
    api::BucketInfo bucketInfo;
    // Putting all docs to have something to visit
    for (uint32_t i=0; i<docs.size(); ++i) {
        auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bid), docs[i], 100 + i);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::PutReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
        bucketInfo = reply->getBucketInfo();
    }
    // Sending a getiter request that will only visit some of the docs
    spi::IteratorId iterId(createIterator(top, bid, ""));
    {
        auto cmd = std::make_shared<GetIterCommand>(makeDocumentBucket(bid), iterId, 2_Ki);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<GetIterReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
        EXPECT_GT(reply->getEntries().size(), 0);
        EXPECT_LT(reply->getEntries().size(), docs.size());
    }
    // Normal case of get iter is testing through visitor tests.
    // Testing specific situation where file is deleted while visiting here
    {
        auto cmd = std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bid));
        cmd->setBucketInfo(bucketInfo);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::DeleteBucketReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
    }
    {
        auto cmd = std::make_shared<GetIterCommand>(makeDocumentBucket(bid), iterId, 2_Ki);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<GetIterReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode::BUCKET_NOT_FOUND, reply->getResult().getResult());
        EXPECT_TRUE(reply->getEntries().empty());
    }
}

TEST_F(FileStorManagerTest, set_bucket_active_state) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    setClusterState("storage:4 distributor:1");

    document::BucketId bid(16, 4000);

    createBucket(bid);
    auto& provider = dynamic_cast<spi::dummy::DummyPersistence&>(_node->getPersistenceProvider());
    EXPECT_FALSE(provider.isActive(makeSpiBucket(bid)));

    {
        auto cmd = std::make_shared<api::SetBucketStateCommand>(makeDocumentBucket(bid), api::SetBucketStateCommand::ACTIVE);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::SetBucketStateReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
    }

    EXPECT_TRUE(provider.isActive(makeSpiBucket(bid)));
    {
        StorBucketDatabase::WrappedEntry entry(_node->getStorageBucketDatabase().get(bid, "foo"));
        EXPECT_TRUE(entry->info.isActive());
    }

    {
        auto cmd = std::make_shared<api::SetBucketStateCommand>(
                makeDocumentBucket(bid), api::SetBucketStateCommand::INACTIVE);
        cmd->setAddress(_Storage3);
        top.sendDown(cmd);
        top.waitForMessages(1, _waitTime);
        ASSERT_EQ(1, top.getNumReplies());
        auto reply = std::dynamic_pointer_cast<api::SetBucketStateReply>(top.getReply(0));
        top.reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(ReturnCode(ReturnCode::OK), reply->getResult());
    }

    EXPECT_FALSE(provider.isActive(makeSpiBucket(bid)));
    {
        StorBucketDatabase::WrappedEntry entry(_node->getStorageBucketDatabase().get(bid, "foo"));
        EXPECT_FALSE(entry->info.isActive());
    }
}

TEST_F(FileStorManagerTest, notify_owner_distributor_on_outdated_set_bucket_state) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    setClusterState("storage:2 distributor:2");

    document::BucketId bid(getFirstBucketNotOwnedByDistributor(0));
    ASSERT_NE(bid.getRawId(), 0);
    createBucket(bid);

    auto cmd = std::make_shared<api::SetBucketStateCommand>(makeDocumentBucket(bid), api::SetBucketStateCommand::ACTIVE);
    cmd->setAddress(_Cluster1);
    cmd->setSourceIndex(0);

    top.sendDown(cmd);
    top.waitForMessages(2, _waitTime);

    ASSERT_EQ(2, top.getNumReplies());
    // Not necessarily deterministic order.
    int idxOffset = 0;
    if (top.getReply(0)->getType() != api::MessageType::NOTIFYBUCKETCHANGE) {
        ++idxOffset;
    }
    auto notifyCmd  = std::dynamic_pointer_cast<api::NotifyBucketChangeCommand>(top.getReply(idxOffset));
    auto stateReply = std::dynamic_pointer_cast<api::SetBucketStateReply>(top.getReply(1 - idxOffset));

    ASSERT_TRUE(stateReply.get());
    EXPECT_EQ(ReturnCode(ReturnCode::OK), stateReply->getResult());

    ASSERT_TRUE(notifyCmd.get());
    EXPECT_EQ(1, notifyCmd->getAddress()->getIndex());
    // Not necessary for this to be set since distributor does not insert this
    // info into its db, but useful for debugging purposes.
    EXPECT_TRUE(notifyCmd->getBucketInfo().isActive());
}

TEST_F(FileStorManagerTest, GetBucketDiff_implicitly_creates_bucket) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    setClusterState("storage:2 distributor:1");

    document::BucketId bid(16, 4000);

    std::vector<api::MergeBucketCommand::Node> nodes = {1, 0};

    auto cmd = std::make_shared<api::GetBucketDiffCommand>(makeDocumentBucket(bid), nodes, Timestamp(1000));
    cmd->setAddress(_Cluster1);
    cmd->setSourceIndex(0);
    top.sendDown(cmd);

    api::GetBucketDiffReply* reply;
    ASSERT_SINGLE_REPLY(api::GetBucketDiffReply, reply, top, _waitTime);
    EXPECT_EQ(api::ReturnCode(api::ReturnCode::OK), reply->getResult());
    {
        StorBucketDatabase::WrappedEntry entry(_node->getStorageBucketDatabase().get(bid, "foo"));
        ASSERT_TRUE(entry.exists());
        EXPECT_TRUE(entry->info.isReady());
    }
}

TEST_F(FileStorManagerTest, merge_bucket_implicitly_creates_bucket) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    setClusterState("storage:3 distributor:1");

    document::BucketId bid(16, 4000);

    std::vector<api::MergeBucketCommand::Node> nodes = {1, 2};

    auto cmd = std::make_shared<api::MergeBucketCommand>(makeDocumentBucket(bid), nodes, Timestamp(1000));
    cmd->setAddress(_Cluster1);
    cmd->setSourceIndex(0);
    top.sendDown(cmd);

    api::GetBucketDiffCommand* diffCmd;
    ASSERT_SINGLE_REPLY(api::GetBucketDiffCommand, diffCmd, top, _waitTime);
    {
        StorBucketDatabase::WrappedEntry entry(_node->getStorageBucketDatabase().get(bid, "foo"));
        ASSERT_TRUE(entry.exists());
        EXPECT_TRUE(entry->info.isReady());
    }
}

TEST_F(FileStorManagerTest, newly_created_bucket_is_ready) {
    TestFileStorComponents c(*this);
    auto& top = c.top;
    setClusterState("storage:2 distributor:1");

    document::BucketId bid(16, 4000);

    auto cmd = std::make_shared<api::CreateBucketCommand>(makeDocumentBucket(bid));
    cmd->setAddress(_Cluster1);
    cmd->setSourceIndex(0);
    top.sendDown(cmd);

    api::CreateBucketReply* reply;
    ASSERT_SINGLE_REPLY(api::CreateBucketReply, reply, top, _waitTime);
    EXPECT_EQ(api::ReturnCode(api::ReturnCode::OK), reply->getResult());
    {
        StorBucketDatabase::WrappedEntry entry(_node->getStorageBucketDatabase().get(bid, "foo"));
        ASSERT_TRUE(entry.exists());
        EXPECT_TRUE(entry->info.isReady());
        EXPECT_FALSE(entry->info.isActive());
    }
}

TEST_F(FileStorManagerTest, create_bucket_sets_active_flag_in_database_and_reply) {
    TestFileStorComponents c(*this);
    setClusterState("storage:2 distributor:1");

    document::BucketId bid(16, 4000);
    auto cmd = std::make_shared<api::CreateBucketCommand>(makeDocumentBucket(bid));
    cmd->setAddress(_Cluster1);
    cmd->setSourceIndex(0);
    cmd->setActive(true);
    c.top.sendDown(cmd);

    api::CreateBucketReply* reply;
    ASSERT_SINGLE_REPLY(api::CreateBucketReply, reply, c.top, _waitTime);
    EXPECT_EQ(api::ReturnCode(api::ReturnCode::OK), reply->getResult());
    {
        StorBucketDatabase::WrappedEntry entry(_node->getStorageBucketDatabase().get(bid, "foo"));
        ASSERT_TRUE(entry.exists());
        EXPECT_TRUE(entry->info.isReady());
        EXPECT_TRUE(entry->info.isActive());
    }
}

template <typename Metric>
void FileStorTestBase::assert_request_size_set(TestFileStorComponents& c, std::shared_ptr<api::StorageMessage> cmd, const Metric& metric) {
    cmd->setApproxByteSize(54321);
    cmd->setAddress(_Storage3);
    c.top.sendDown(cmd);
    c.top.waitForMessages(1, _waitTime);
    EXPECT_EQ(static_cast<int64_t>(cmd->getApproxByteSize()), metric.request_size.getLast());
}

TEST_F(FileStorManagerTest, put_command_size_is_added_to_metric) {
    TestFileStorComponents c(*this);
    document::BucketId bucket(16, 4000);
    createBucket(bucket);
    auto cmd = std::make_shared<api::PutCommand>(
            makeDocumentBucket(bucket), _node->getTestDocMan().createRandomDocument(), api::Timestamp(12345));

    assert_request_size_set(c, std::move(cmd), thread_metrics_of(*c.manager)->put);
}

TEST_F(FileStorManagerTest, update_command_size_is_added_to_metric) {
    TestFileStorComponents c(*this);
    document::BucketId bucket(16, 4000);
    createBucket(bucket);
    auto update = std::make_shared<document::DocumentUpdate>(
            _node->getTestDocMan().getTypeRepo(),
            _node->getTestDocMan().createRandomDocument()->getType(),
            document::DocumentId("id:foo:testdoctype1::bar"));
    auto cmd = std::make_shared<api::UpdateCommand>(
            makeDocumentBucket(bucket), std::move(update), api::Timestamp(123456));

    assert_request_size_set(c, std::move(cmd), thread_metrics_of(*c.manager)->update);
}

TEST_F(FileStorManagerTest, remove_command_size_is_added_to_metric) {
    TestFileStorComponents c(*this);
    document::BucketId bucket(16, 4000);
    createBucket(bucket);
    auto cmd = std::make_shared<api::RemoveCommand>(
            makeDocumentBucket(bucket), document::DocumentId("id:foo:testdoctype1::bar"), api::Timestamp(123456));

    assert_request_size_set(c, std::move(cmd), thread_metrics_of(*c.manager)->remove);
}

TEST_F(FileStorManagerTest, get_command_size_is_added_to_metric) {
    TestFileStorComponents c(*this);
    document::BucketId bucket(16, 4000);
    createBucket(bucket);
    auto cmd = std::make_shared<api::GetCommand>(
            makeDocumentBucket(bucket), document::DocumentId("id:foo:testdoctype1::bar"), document::AllFields::NAME);

    assert_request_size_set(c, std::move(cmd), thread_metrics_of(*c.manager)->get);
}

TEST_F(FileStorManagerTest, test_and_set_condition_mismatch_not_counted_as_failure) {
    TestFileStorComponents c(*this);
    auto doc = _node->getTestDocMan().createRandomDocument();
    document::BucketId bucket(16, document::BucketIdFactory().getBucketId(doc->getId()).getRawId());
    createBucket(bucket);
    auto cmd = std::make_shared<api::PutCommand>(makeDocumentBucket(bucket), std::move(doc), api::Timestamp(12345));
    cmd->setCondition(TestAndSetCondition("not testdoctype1"));
    cmd->setAddress(_Storage3);
    c.top.sendDown(cmd);

    api::PutReply* reply;
    ASSERT_SINGLE_REPLY(api::PutReply, reply, c.top, _waitTime);
    EXPECT_EQ(reply->getResult().getResult(), api::ReturnCode::TEST_AND_SET_CONDITION_FAILED);

    auto& metrics = thread_metrics_of(*c.manager)->put;
    EXPECT_EQ(metrics.failed.getValue(), 0);
    EXPECT_EQ(metrics.test_and_set_failed.getValue(), 1);
    EXPECT_EQ(thread_metrics_of(*c.manager)->failedOperations.getValue(), 0);
}

namespace {

spi::Bucket make_spi_bucket(uint32_t seed) {
    return spi::Bucket(makeDocumentBucket(document::BucketId(15, seed)));
}

spi::BucketInfo make_dummy_spi_bucket_info(uint32_t seed) {
    return spi::BucketInfo(spi::BucketChecksum(seed + 0x1000), seed, seed * 100, seed, seed * 200);
}

}

TEST_F(FileStorManagerTest, bucket_db_is_populated_from_provider_when_initialize_is_called) {
    TestFileStorComponents c(*this);
    // TODO extend to test global bucket space as well. Dummy provider currently only
    // supports default bucket space. Replace with a better mock.
    std::vector<std::pair<spi::Bucket, spi::BucketInfo>> buckets = {
        {make_spi_bucket(1), make_dummy_spi_bucket_info(1)},
        {make_spi_bucket(2), make_dummy_spi_bucket_info(2)},
        {make_spi_bucket(3), make_dummy_spi_bucket_info(3)},
    };
    std::sort(buckets.begin(), buckets.end(), [](auto& lhs, auto& rhs) {
        return (lhs.first.getBucketId().toKey() < rhs.first.getBucketId().toKey());
    });

    getDummyPersistence().set_fake_bucket_set(buckets);
    c.manager->initialize_bucket_databases_from_provider();
    c.manager->complete_internal_initialization();

    std::vector<std::pair<document::BucketId, api::BucketInfo>> from_db;
    auto populate_from_db = [&from_db](uint64_t key, auto& entry) {
        from_db.emplace_back(document::BucketId::keyToBucketId(key), entry.info);
    };

    auto& default_db = _node->content_bucket_db(document::FixedBucketSpaces::default_space());
    default_db.acquire_read_guard()->for_each(populate_from_db);
    ASSERT_EQ(from_db.size(), buckets.size());
    for (size_t i = 0; i < from_db.size(); ++i) {
        auto& wanted = buckets[i];
        auto& actual = from_db[i];
        EXPECT_EQ(actual.first, wanted.first.getBucket().getBucketId());
        EXPECT_EQ(actual.second, PersistenceUtil::convertBucketInfo(wanted.second));
    }

    from_db.clear();
    auto& global_db = _node->content_bucket_db(document::FixedBucketSpaces::global_space());
    global_db.acquire_read_guard()->for_each(populate_from_db);
    EXPECT_EQ(from_db.size(), 0);

    auto reported_state = _node->getStateUpdater().getReportedNodeState();
    EXPECT_EQ(reported_state->getMinUsedBits(), 15);
    EXPECT_EQ(reported_state->getInitProgress(), 1.0); // Should be exact.
    EXPECT_EQ(reported_state->getState(), lib::State::UP);
}

struct FileStorHandlerTest : public FileStorTestBase {
    std::unique_ptr<FileStorHandlerComponents> c;
    FileStorHandler* handler;
    FileStorHandlerTest()
        : FileStorTestBase(),
          c(),
          handler()
    {}
    void SetUp() override {
        FileStorTestBase::SetUp();
        c = std::make_unique<FileStorHandlerComponents>(*this);
        handler = c->filestorHandler.get();
    }
    FileStorHandler::LockedMessage get_next_message() {
        return handler->getNextMessage(0);
    }
};

void
expect_async_message(StorageMessage::Priority exp_pri,
                     const FileStorHandler::ScheduleAsyncResult& result)
{
    EXPECT_TRUE(result.was_scheduled());
    ASSERT_TRUE(result.has_async_message());
    EXPECT_EQ(exp_pri, result.async_message().msg->getPriority());
}

void
expect_empty_async_message(const FileStorHandler::ScheduleAsyncResult& result)
{
    EXPECT_TRUE(result.was_scheduled());
    EXPECT_FALSE(result.has_async_message());
}

TEST_F(FileStorHandlerTest, message_not_scheduled_if_handler_is_closed)
{
    handler->setDiskState(FileStorHandler::DiskState::CLOSED);
    auto result = handler->schedule_and_get_next_async_message(make_put_command());
    EXPECT_FALSE(result.was_scheduled());
}

TEST_F(FileStorHandlerTest, no_async_message_returned_if_handler_is_paused)
{
    auto guard = handler->pause();
    auto result = handler->schedule_and_get_next_async_message(make_put_command());
    expect_empty_async_message(result);
}

TEST_F(FileStorHandlerTest, async_message_with_lowest_pri_returned_on_schedule)
{
    handler->schedule(make_put_command(20));
    handler->schedule(make_put_command(40));
    {
        auto result = handler->schedule_and_get_next_async_message(make_put_command(30));
        expect_async_message(20, result);
    }
    EXPECT_EQ(30, get_next_message().msg->getPriority());
    EXPECT_EQ(40, get_next_message().msg->getPriority());
}

TEST_F(FileStorHandlerTest, no_async_message_returned_if_lowest_pri_message_is_not_async)
{
    // GET is not an async message.
    handler->schedule(make_get_command(20));

    auto result = handler->schedule_and_get_next_async_message(make_put_command(30));
    expect_empty_async_message(result);

    EXPECT_EQ(20, get_next_message().msg->getPriority());
    EXPECT_EQ(30, get_next_message().msg->getPriority());
}

TEST_F(FileStorHandlerTest, inhibited_operations_are_skipped)
{
    std::string docid_a = "id:foo:testdoctype1::a";
    std::string docid_b = "id:foo:testdoctype1::b";
    handler->schedule(make_put_command(20, docid_a));
    {
        auto locked_msg = get_next_message();
        {
            // Bucket for docid_a is locked and put command for same bucket is inhibited.
            auto result = handler->schedule_and_get_next_async_message(make_put_command(30, docid_a));
            expect_empty_async_message(result);
        }
        {
            // Put command for another bucket is ok.
            auto result = handler->schedule_and_get_next_async_message(make_put_command(40, docid_b));
            expect_async_message(40, result);
        }
    }
    EXPECT_EQ(30, get_next_message().msg->getPriority());
}

} // storage
