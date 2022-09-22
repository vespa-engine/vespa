// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/helper/configgetter.hpp>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/common/global_bucket_space_distribution_converter.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/metrics/updatehook.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/vdslib/distribution/distribution.h>
#include <vespa/vdslib/state/clusterstate.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <future>

#include <vespa/log/log.h>
LOG_SETUP(".test.bucketdb.bucketmanager");

using config::ConfigGetter;
using config::FileSpec;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;
using namespace ::testing;

namespace storage {

struct TestBucketInfo {
    uint32_t crc;
    uint32_t size;
    uint32_t count;
    uint32_t partition;

    api::BucketInfo getInfo() const
        { return api::BucketInfo(crc, count, size); }
};

std::ostream& operator<<(std::ostream& out, const TestBucketInfo& info) {
    out << "TestBucketInfo(" << info.crc << ", " << info.size
        << ", " << info.count << ", " << info.partition << ")";
    return out;
}

class ConcurrentOperationFixture;
struct TestParams;

struct BucketManagerTest : public Test {
public:
    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _top;
    BucketManager *_manager;
    DummyStorageLink* _bottom;
    std::map<document::BucketId, TestBucketInfo> _bucketInfo;
    uint32_t _emptyBuckets;
    document::Document::SP _document;

    ~BucketManagerTest();

    void setupTestEnvironment(bool fakePersistenceLayer = true,
                              bool noDelete = false);
    void addBucketsToDB(uint32_t count);
    bool wasBlockedDueToLastModified(api::StorageMessage* msg,
                                     uint64_t lastModified);
    void insertSingleBucket(const document::BucketId& bucket,
                            const api::BucketInfo& info);
    void waitUntilRequestsAreProcessing(size_t nRequests = 1);
    void doTestMutationOrdering(
            ConcurrentOperationFixture& fixture,
            const TestParams& params);
    void doTestConflictingReplyIsEnqueued(
            const document::BucketId& bucket,
            const api::StorageCommand::SP& treeMutationCmd,
            const api::MessageType& treeMutationReplyType);

    void scheduleBucketInfoRequestWithConcurrentOps(
            ConcurrentOperationFixture& fixture,
            const document::BucketId& bucketForRemove,
            const document::BucketId& bucketForSplit,
            api::Timestamp mutationTimestamp);
    void sendSingleBucketInfoRequest(const document::BucketId& id);
    void assertRequestWithBadHashIsRejected(
            ConcurrentOperationFixture& fixture);

protected:
    void update_min_used_bits() {
        _manager->updateMinUsedBits();
    }
    void trigger_metric_manager_update() {
        std::mutex l;
        _manager->updateMetrics(BucketManager::MetricLockGuard(l));
    }

    const BucketManagerMetrics& bucket_manager_metrics() const {
        return *_manager->_metrics;
    }

public:
    static constexpr uint32_t MESSAGE_WAIT_TIME = 60*2;

    void SetUp() override {
        _emptyBuckets = 0;
    }

    friend class ConcurrentOperationFixture;
};

BucketManagerTest::~BucketManagerTest() = default;

#define ASSERT_DUMMYLINK_REPLY_COUNT(link, count) \
    if (link->getNumReplies() != count) { \
        std::ostringstream ost; \
        ost << "Expected there to be " << count << " replies in link, but " \
            << "found " << link->getNumReplies() << ":\n"; \
        for (uint32_t i=0; i<link->getNumReplies(); ++i) { \
            ost << link->getReply(i)->getType() << "\n"; \
        } \
        FAIL() << ost.str(); \
    }

std::string getMkDirDisk(const std::string & rootFolder, int disk) {
    std::ostringstream os;
    os << "mkdir -p " << rootFolder << "/disks/d" << disk;
    return os.str();
}

void BucketManagerTest::setupTestEnvironment(bool fakePersistenceLayer,
                                             bool noDelete)
{
    vdstestlib::DirConfig config(getStandardConfig(true, "bucketmanagertest"));
    std::string rootFolder = getRootFolder(config);
    if (!noDelete) {
        assert(system(("rm -rf " + rootFolder).c_str()) == 0);
    }
    assert(system(getMkDirDisk(rootFolder, 0).c_str()) == 0);
    assert(system(getMkDirDisk(rootFolder, 1).c_str()) == 0);

    auto repo = std::make_shared<const DocumentTypeRepo>(
                *ConfigGetter<DocumenttypesConfig>::getConfig(
                    "config-doctypes", FileSpec("../config-doctypes.cfg")));
    _top = std::make_unique<DummyStorageLink>();
    _node = std::make_unique<TestServiceLayerApp>(NodeIndex(0), config.getConfigId());
    _node->setTypeRepo(repo);
    _node->setupDummyPersistence();
    // Set up the 3 links
    auto manager = std::make_unique<BucketManager>(config::ConfigUri(config.getConfigId()), _node->getComponentRegister());
    _manager = manager.get();
    _top->push_back(std::move(manager));
    if (fakePersistenceLayer) {
        auto bottom = std::make_unique<DummyStorageLink>();
        _bottom = bottom.get();
        _top->push_back(std::move(bottom));
    } else {
        auto bottom = std::make_unique<FileStorManager>(
                config::ConfigUri(config.getConfigId()),
                _node->getPersistenceProvider(), _node->getComponentRegister(), *_node, _node->get_host_info());
        _top->push_back(std::move(bottom));
    }
    // Generate a doc to use for testing..
    const DocumentType &type(*_node->getTypeRepo()->getDocumentType("text/html"));
    _document = std::make_shared<document::Document>(type, document::DocumentId("id:ns:text/html::ntnu"));
}

void BucketManagerTest::addBucketsToDB(uint32_t count)
{
    _bucketInfo.clear();
    _emptyBuckets = 0;
    lib::RandomGen randomizer(25423);
    while (_bucketInfo.size() < count) {
        document::BucketId id(16, randomizer.nextUint32());
        id = id.stripUnused();
        if (_bucketInfo.empty()) {
            id = _node->getBucketIdFactory().getBucketId(
                    _document->getId()).stripUnused();
        }
        TestBucketInfo info;
        info.crc = randomizer.nextUint32();
        info.size = randomizer.nextUint32();
        info.count = randomizer.nextUint32(1, 0xFFFF);

        info.partition = 0u;
        _bucketInfo[id] = info;
    }

    // Make sure we have at least one empty bucket
    TestBucketInfo& info = (++_bucketInfo.begin())->second;
    assert(info.size != 0);
    info.size = 0;
    info.count = 0;
    info.crc = 0;
    ++_emptyBuckets;
    for (const auto& bi : _bucketInfo) {
        bucketdb::StorageBucketInfo entry;
        entry.setBucketInfo(api::BucketInfo(bi.second.crc,
                                            bi.second.count,
                                            bi.second.size));
        _node->getStorageBucketDatabase().insert(bi.first, entry, "foo");
    }
}

bool
BucketManagerTest::wasBlockedDueToLastModified(api::StorageMessage* msg,
                                               uint64_t lastModified)
{
    setupTestEnvironment();
    document::BucketId id(16, 1);
    api::BucketInfo info(1, 2, 3);
    info.setLastModified(api::Timestamp(1234));

    {
        bucketdb::StorageBucketInfo entry;
        entry.setBucketInfo(info);
        _node->getStorageBucketDatabase().insert(id, entry, "foo");
    }

    _top->open();

    _top->sendDown(api::StorageMessage::SP(msg));
    if (_top->getNumReplies() == 1) {
        assert(_bottom->getNumCommands() == 0);
        assert(!dynamic_cast<api::StorageReply&>(*_top->getReply(0)).getResult().success());
        return true;
    } else {
        assert(_top->getNumReplies() == 0);

        // Check that bucket database now has the operation's timestamp as last modified.
        {
            StorBucketDatabase::WrappedEntry entry(
                    _node->getStorageBucketDatabase().get(id, "foo"));
            assert(entry->info.getLastModified() == lastModified);
        }

        return false;
    }
}

TEST_F(BucketManagerTest, remove_last_modified_ok) {
    EXPECT_FALSE(wasBlockedDueToLastModified(
                           new api::RemoveCommand(makeDocumentBucket(document::BucketId(16, 1)),
                                   document::DocumentId("id:m:test:n=1:foo"),
                                   api::Timestamp(1235)),
                           1235));
}


TEST_F(BucketManagerTest, remove_last_modified_failed) {
    EXPECT_TRUE(wasBlockedDueToLastModified(
                           new api::RemoveCommand(makeDocumentBucket(document::BucketId(16, 1)),
                                   document::DocumentId("id:m:test:n=1:foo"),
                                   api::Timestamp(1233)),
                           1233));
}

TEST_F(BucketManagerTest, distribution_bit_generation_empty) {
    setupTestEnvironment();
    _manager->doneInit();
    trigger_metric_manager_update();
    EXPECT_EQ(58u, _node->getStateUpdater().getReportedNodeState()->getMinUsedBits());
}

TEST_F(BucketManagerTest, distribution_bit_change_on_create_bucket){
    setupTestEnvironment();
    addBucketsToDB(30);
    _top->open();
    _node->getDoneInitializeHandler().notifyDoneInitializing();
    _manager->doneInit();
    update_min_used_bits();
    EXPECT_EQ(16u, _node->getStateUpdater().getReportedNodeState()->getMinUsedBits());

    std::shared_ptr<api::CreateBucketCommand> cmd(
            new api::CreateBucketCommand(makeDocumentBucket(document::BucketId(4, 5678))));
    _top->sendDown(cmd);
    EXPECT_EQ(4u, _node->getStateUpdater().getReportedNodeState()->getMinUsedBits());
}

TEST_F(BucketManagerTest, min_used_bits_from_component_is_honored) {
    setupTestEnvironment();
    // Let these differ in order to test state update behavior.
    _node->getComponentRegister().getMinUsedBitsTracker().setMinUsedBits(10);
    lib::NodeState ns(
            *_node->getStateUpdater().getReportedNodeState());
    ns.setMinUsedBits(13);
    _node->getStateUpdater().setReportedNodeState(ns);
    addBucketsToDB(30);
    _top->open();
    // Don't update metrics, as these will always overwrite the min used bits
    // if it differs from the db.

    // 12 >= 10, so no update of reported state (left at 13; this should of
    // course not happen in practice, but used for faking in the test)
    std::shared_ptr<api::CreateBucketCommand> cmd(
            new api::CreateBucketCommand(makeDocumentBucket(document::BucketId(12, 5678))));
    _top->sendDown(cmd);
    EXPECT_EQ(13u, _node->getStateUpdater().getReportedNodeState()->getMinUsedBits());
}

// FIXME: non-deterministic test
TEST_F(BucketManagerTest, DISABLED_request_bucket_info_with_state) {
    // Test prior to building bucket cache
    setupTestEnvironment();
    addBucketsToDB(30);

    std::vector<lib::ClusterState> states;
    states.emplace_back("version:0");
    states.emplace_back("version:1 distributor:1 storage:1");
    states.emplace_back("version:2 distributor:3 .1.s:i .2.s:d storage:4");
    states.emplace_back("version:3 distributor:3 .1.s:i .2.s:d storage:4 .3.s:d");
    states.emplace_back("version:4 distributor:3 .1.s:i .2.s:d storage:4");

    _node->setClusterState(states.back());
    for (uint32_t i=0; i<states.size(); ++i) {
        api::SetSystemStateCommand::SP cmd(
                new api::SetSystemStateCommand(states[i]));
        _manager->onDown(cmd);
    }

    // Send a request bucket info command that will be outdated and failed.
    std::shared_ptr<api::RequestBucketInfoCommand> cmd1(
            new api::RequestBucketInfoCommand(makeBucketSpace(), 0, states[1]));
    // Send two request bucket info commands that will be processed together
    // when the bucket manager is idle, as states are equivalent
    std::shared_ptr<api::RequestBucketInfoCommand> cmd2(
            new api::RequestBucketInfoCommand(makeBucketSpace(), 0, states[2]));
    std::shared_ptr<api::RequestBucketInfoCommand> cmd3(
            new api::RequestBucketInfoCommand(makeBucketSpace(), 0, states[3]));

    // Tag server initialized before starting
    _top->open();
    _manager->startWorkerThread();
    _node->getDoneInitializeHandler().notifyDoneInitializing();
    _manager->doneInit();

    LOG(info, "Sending 3 different request bucket info messages");
    _top->sendDown(cmd1);
    _top->sendDown(cmd2);
    _top->sendDown(cmd3);

    {
        LOG(info, "Waiting for response from 3 request bucket info messages");
        _top->waitForMessages(3, 5);
        ASSERT_DUMMYLINK_REPLY_COUNT(_top, 3);
        std::map<uint64_t, api::RequestBucketInfoReply::SP> replies;
        for (uint32_t i=0; i<3; ++i) {
            replies[_top->getReply(i)->getMsgId()]
                    = std::dynamic_pointer_cast<api::RequestBucketInfoReply>(
                            _top->getReply(i));
        }
        std::shared_ptr<api::RequestBucketInfoReply> reply1(
                replies[cmd1->getMsgId()]);
        std::shared_ptr<api::RequestBucketInfoReply> reply2(
                replies[cmd2->getMsgId()]);
        std::shared_ptr<api::RequestBucketInfoReply> reply3(
                replies[cmd3->getMsgId()]);
        _top->reset();
        ASSERT_TRUE(reply1.get());
        ASSERT_TRUE(reply2.get());
        ASSERT_TRUE(reply3.get());
        EXPECT_EQ(api::ReturnCode(api::ReturnCode::REJECTED,
                "Ignoring bucket info request for cluster state version 1 as "
                "versions from version 2 differs from this state."),
                             reply1->getResult());
        EXPECT_EQ(api::ReturnCode(api::ReturnCode::REJECTED,
                "There is already a newer bucket info request for "
                "this node from distributor 0"),
                             reply2->getResult());
        EXPECT_EQ(api::ReturnCode(api::ReturnCode::OK),
                             reply3->getResult());
        api::RequestBucketInfoReply::Entry entry;

        ASSERT_EQ(18u, reply3->getBucketInfo().size());
        entry = api::RequestBucketInfoReply::Entry(
                document::BucketId(16, 0xe8c8), api::BucketInfo(0x79d04f78, 11153, 1851385240u));
        EXPECT_EQ(entry, reply3->getBucketInfo()[0]);
    }
}

TEST_F(BucketManagerTest, request_bucket_info_with_list) {
    setupTestEnvironment();
    addBucketsToDB(30);
    _top->open();
    _node->getDoneInitializeHandler().notifyDoneInitializing();
    _top->doneInit();
    {
        std::vector<document::BucketId> bids;
        bids.emplace_back(16, 0xe8c8);

        auto cmd = std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), bids);

        _top->sendDown(cmd);
        _top->waitForMessages(1, 5);
        ASSERT_DUMMYLINK_REPLY_COUNT(_top, 1);
        auto reply = std::dynamic_pointer_cast<api::RequestBucketInfoReply>(_top->getReply(0));
        _top->reset();
        ASSERT_TRUE(reply.get());
        EXPECT_EQ(api::ReturnCode(api::ReturnCode::OK), reply->getResult());
        ASSERT_EQ(1u, reply->getBucketInfo().size());
        api::RequestBucketInfoReply::Entry entry(
                document::BucketId(16, 0xe8c8),
                api::BucketInfo(0x79d04f78, 11153, 1851385240u));
        EXPECT_EQ(entry, reply->getBucketInfo()[0]);
    }
}

TEST_F(BucketManagerTest, swallow_notify_bucket_change_reply) {
    setupTestEnvironment();
    addBucketsToDB(30);
    _top->open();
    _node->getDoneInitializeHandler().notifyDoneInitializing();
    _top->doneInit();

    api::NotifyBucketChangeCommand cmd(makeDocumentBucket(document::BucketId(1, 16)),
                                       api::BucketInfo());
    auto reply = std::make_shared<api::NotifyBucketChangeReply>(cmd);

    _top->sendDown(reply);
    // Should not leave the bucket manager.
    EXPECT_EQ(0u, _bottom->getNumCommands());
}

TEST_F(BucketManagerTest, metrics_generation) {
    setupTestEnvironment();
    _top->open();
    // Add 3 buckets; 2 ready, 1 active. 300 docs total, 600 bytes total.
    for (int i = 0; i < 3; ++i) {
        bucketdb::StorageBucketInfo entry;
        api::BucketInfo info(50, 100, 200);
        if (i > 0) {
            info.setReady();
            if (i == 2) {
                info.setActive();
            }
        }
        entry.setBucketInfo(info);
        _node->getStorageBucketDatabase().insert(document::BucketId(16, i),
                                                 entry, "foo");
     }
    _node->getDoneInitializeHandler().notifyDoneInitializing();
    _top->doneInit();
    trigger_metric_manager_update();

    ASSERT_TRUE(bucket_manager_metrics().disk);
    const DataStoredMetrics& m(*bucket_manager_metrics().disk);
    EXPECT_EQ(3, m.buckets.getLast());
    EXPECT_EQ(300, m.docs.getLast());
    EXPECT_EQ(600, m.bytes.getLast());
    EXPECT_EQ(1, m.active.getLast());
    EXPECT_EQ(2, m.ready.getLast());
}

namespace {

void verify_db_memory_metrics_present(const ContentBucketDbMetrics& db_metrics) {
    auto* m = db_metrics.memory_usage.getMetric("allocated_bytes");
    ASSERT_TRUE(m != nullptr);
    // Actual values are very much implementation defined, so just check for non-zero.
    EXPECT_GT(m->getLongValue("last"), 0);
    m = db_metrics.memory_usage.getMetric("used_bytes");
    ASSERT_TRUE(m != nullptr);
    EXPECT_GT(m->getLongValue("last"), 0);
}

}

TEST_F(BucketManagerTest, metrics_are_tracked_per_bucket_space) {
    setupTestEnvironment();
    _top->open();
    auto& repo = _node->getComponentRegister().getBucketSpaceRepo();
    {
        bucketdb::StorageBucketInfo entry;
        api::BucketInfo info(50, 100, 200);
        info.setReady(true);
        entry.setBucketInfo(info);
        repo.get(document::FixedBucketSpaces::default_space()).bucketDatabase()
                .insert(document::BucketId(16, 1234), entry, "foo");
    }
    {
        bucketdb::StorageBucketInfo entry;
        api::BucketInfo info(60, 150, 300);
        info.setActive(true);
        entry.setBucketInfo(info);
        repo.get(document::FixedBucketSpaces::global_space()).bucketDatabase()
                .insert(document::BucketId(16, 1234), entry, "foo");
    }
    _node->getDoneInitializeHandler().notifyDoneInitializing();
    _top->doneInit();
    trigger_metric_manager_update();

    auto& spaces = bucket_manager_metrics().bucket_spaces;
    auto default_m = spaces.find(document::FixedBucketSpaces::default_space());
    ASSERT_TRUE(default_m != spaces.end());
    EXPECT_EQ(1,   default_m->second->buckets_total.getLast());
    EXPECT_EQ(100, default_m->second->docs.getLast());
    EXPECT_EQ(200, default_m->second->bytes.getLast());
    EXPECT_EQ(0,   default_m->second->active_buckets.getLast());
    EXPECT_EQ(1,   default_m->second->ready_buckets.getLast());

    verify_db_memory_metrics_present(default_m->second->bucket_db_metrics);

    auto global_m = spaces.find(document::FixedBucketSpaces::global_space());
    ASSERT_TRUE(global_m != spaces.end());
    EXPECT_EQ(1,   global_m->second->buckets_total.getLast());
    EXPECT_EQ(150, global_m->second->docs.getLast());
    EXPECT_EQ(300, global_m->second->bytes.getLast());
    EXPECT_EQ(1,   global_m->second->active_buckets.getLast());
    EXPECT_EQ(0,   global_m->second->ready_buckets.getLast());

    verify_db_memory_metrics_present(global_m->second->bucket_db_metrics);
}

void
BucketManagerTest::insertSingleBucket(const document::BucketId& bucket,
                                      const api::BucketInfo& info)
{
    bucketdb::StorageBucketInfo entry;
    entry.setBucketInfo(info);
    _node->getStorageBucketDatabase().insert(bucket, entry, "foo");
}

void
BucketManagerTest::waitUntilRequestsAreProcessing(size_t nRequests)
{
    while (_manager->bucketInfoRequestsCurrentlyProcessing() != nRequests) {
        std::this_thread::yield();
    }
}

namespace {

struct WithBuckets {
    std::map<document::BucketId, api::BucketInfo> _bucketsAndInfo;

    WithBuckets& add(const document::BucketId& id,
                     const api::BucketInfo& info)
    {
        _bucketsAndInfo[id] = info;
        return *this;
    }
};

} // anon ns

class ConcurrentOperationFixture {
public:
    explicit ConcurrentOperationFixture(BucketManagerTest& self)
        : _self(self),
          _state(std::make_shared<lib::ClusterState>("distributor:1 storage:1"))
    {
        _self.setupTestEnvironment();
        _self._top->open();
        _self._node->getDoneInitializeHandler().notifyDoneInitializing();
        _self._manager->startWorkerThread();
        _self._top->doneInit();

        // Need a cluster state to work with initially, so that processing
        // bucket requests can calculate a target distributor.
        update_internal_cluster_state_with_current();
    }

    void setUp(const WithBuckets& buckets) {
        for (auto& b : buckets._bucketsAndInfo) {
            _self.insertSingleBucket(b.first, b.second);
        }
    }

    void update_internal_cluster_state_with_current() {
        _self._node->setClusterState(*_state);
        auto cmd = std::make_shared<api::SetSystemStateCommand>(*_state);
        _self._manager->onDown(cmd);
        // Also send up reply to release internal state transition barrier.
        // We expect there to be no other pending messages at this point.
        std::shared_ptr<api::StorageReply> reply(cmd->makeReply());
        auto as_state_reply = std::dynamic_pointer_cast<api::SetSystemStateReply>(reply);
        assert(as_state_reply);
        assert(_self._top->getNumReplies() == 0);
        _self._manager->onUp(as_state_reply);
        assert(_self._top->getNumReplies() == 1);
        (void)_self._top->getRepliesOnce(); // Clear state reply sent up chain
    }

    void update_cluster_state(const lib::ClusterState& state) {
        _state = std::make_shared<lib::ClusterState>(state);
        update_internal_cluster_state_with_current();
    }

    auto acquireBucketLock(const document::BucketId& bucket) {
        return _self._node->getStorageBucketDatabase().get(bucket, "foo");
    }

    auto createRemoveCommand(const document::BucketId& bucket,
                             api::Timestamp timestamp = 123456) const
    {
        // Note: this is a dummy message; its contained document ID will not
        // map to the provided bucket ID (at least it's extremely unlikely..)
        return std::make_shared<api::RemoveCommand>(
                makeDocumentBucket(bucket),
                document::DocumentId("id:foo:testdoctype1::bar"),
                timestamp);
    }

    auto createPutCommand(const document::BucketId& bucket) const {
        auto doc = _self._node->getTestDocMan().createDocument(
                "a foo walks into a bar", "id:foo:testdoctype1::bar1");
        return std::make_shared<api::PutCommand>(
                makeDocumentBucket(bucket), std::move(doc), api::Timestamp(123456));
    }

    auto createUpdateCommand(const document::BucketId& bucket) const {
        auto update = std::make_shared<document::DocumentUpdate>(
                _self._node->getTestDocMan().getTypeRepo(),
                *_self._node->getTestDocMan().getTypeRepo()
                    .getDocumentType("testdoctype1"),
                document::DocumentId("id:foo:testdoctype1::bar2"));
        return std::make_shared<api::UpdateCommand>(
                makeDocumentBucket(bucket), update, api::Timestamp(123456));
    }

    auto createFullFetchCommand() const {
        return std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), 0, *_state);
    }

    auto createFullFetchCommand(const lib::ClusterState& explicit_state) const {
        return std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), 0, explicit_state);
    }

    auto createFullFetchCommandWithHash(vespalib::stringref hash) const {
        return std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), 0, *_state, hash);
    }

    auto createFullFetchCommandWithHash(document::BucketSpace space, vespalib::stringref hash) const {
        return std::make_shared<api::RequestBucketInfoCommand>(space, 0, *_state, hash);
    }

    auto acquireBucketLockAndSendInfoRequest(const document::BucketId& bucket) {
        auto guard = acquireBucketLock(bucket);
        // Send down processing command which will block.
        _self._top->sendDown(createFullFetchCommand());
        // Have to wait until worker thread has started chewing on request
        // before we can continue, or we can end up in a race where processing
        // does not start until _after_ we've sent up our bucket-deleting
        // message. Since we hold a bucket lock, the below function can never
        // transition false->true->false under our feet, only false->true.
        _self.waitUntilRequestsAreProcessing(1);
        return guard;
    }

    // Currently assumes there is only 1 command of cmd's message type in
    // the bottom storage link.
    void bounceWithReply(api::StorageCommand& cmd,
                         api::ReturnCode::Result code = api::ReturnCode::OK,
                         const document::BucketId& remapTo = document::BucketId())
    {
        _self._bottom->waitForMessages(1, BucketManagerTest::MESSAGE_WAIT_TIME);
        // Bounce it back up with an implicitly OK status. This should cause the
        // bucket manager to avoid reporting deleted buckets in its result set
        // since these have been "tainted" by a concurrent removal.
        std::unique_ptr<api::StorageReply> reply(cmd.makeReply());
        if (remapTo.getRawId() != 0) {
            dynamic_cast<api::BucketReply&>(*reply).remapBucketId(remapTo);
        }
        reply->setResult(code);
        _self._bottom->getAndRemoveMessage(cmd.getType());
        _self._bottom->sendUp(std::move(reply));
    }

    auto awaitAndGetReplies(size_t nReplies) {
        _self._top->waitForMessages(
                nReplies, BucketManagerTest::MESSAGE_WAIT_TIME);
        return _self._top->getReplies();
    }

    void assertOrderedAfterBucketReply(size_t nBucketReplies,
                                       const api::MessageType& msgType)
    {
        const size_t nTotal = nBucketReplies + 1;
        auto replies = awaitAndGetReplies(nTotal);
        ASSERT_EQ(nTotal, replies.size());
        for (size_t i = 0; i < nBucketReplies; ++i) {
            ASSERT_EQ(api::MessageType::REQUESTBUCKETINFO_REPLY, replies[i]->getType());
        }
        ASSERT_EQ(msgType, replies[nBucketReplies]->getType());
    }

    void assertReplyOrdering(
            const std::vector<const api::MessageType*>& replyTypes)
    {
        auto replies = awaitAndGetReplies(replyTypes.size());
        ASSERT_EQ(replyTypes.size(), replies.size());
        for (size_t i = 0; i < replyTypes.size(); ++i) {
            ASSERT_EQ(*replyTypes[i], replies[i]->getType());
        }
    }

    void clearReceivedReplies() {
        _self._top->getRepliesOnce();
    }

    static std::unique_ptr<lib::Distribution> default_grouped_distribution() {
        return std::make_unique<lib::Distribution>(
                GlobalBucketSpaceDistributionConverter::string_to_config(vespalib::string(
R"(redundancy 2
group[3]
group[0].name "invalid"
group[0].index "invalid"
group[0].partitions 1|*
group[0].nodes[0]
group[1].name rack0
group[1].index 0
group[1].nodes[3]
group[1].nodes[0].index 0
group[1].nodes[1].index 1
group[1].nodes[2].index 2
group[2].name rack1
group[2].index 1
group[2].nodes[3]
group[2].nodes[0].index 3
group[2].nodes[1].index 4
group[2].nodes[2].index 5
)")));
    }

    static std::shared_ptr<lib::Distribution> derived_global_grouped_distribution() {
        auto default_distr = default_grouped_distribution();
        return  GlobalBucketSpaceDistributionConverter::convert_to_global(*default_distr);
    }

    void set_grouped_distribution_configs() {
        auto default_distr = default_grouped_distribution();
        _self._node->getComponentRegister().getBucketSpaceRepo()
                .get(document::FixedBucketSpaces::default_space()).setDistribution(std::move(default_distr));
        auto global_distr = derived_global_grouped_distribution();
        _self._node->getComponentRegister().getBucketSpaceRepo()
                .get(document::FixedBucketSpaces::global_space()).setDistribution(std::move(global_distr));
    }

private:
    BucketManagerTest& _self;
    std::shared_ptr<lib::ClusterState> _state;
};

TEST_F(BucketManagerTest, split_reply_ordered_after_bucket_reply) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucketA(17, 0);
    document::BucketId bucketB(17, 1);
    fixture.setUp(WithBuckets()
                  .add(bucketA, api::BucketInfo(50, 100, 200))
                  .add(bucketB, api::BucketInfo(100, 200, 400)));
    auto guard = fixture.acquireBucketLockAndSendInfoRequest(bucketB);

    // Split bucket A to model a concurrent modification to an already fetched
    // bucket.
    auto splitCmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucketA));
    _top->sendDown(splitCmd);
    fixture.bounceWithReply(*splitCmd);
    // Let bucket manager breathe again.
    guard.unlock();

    fixture.assertOrderedAfterBucketReply(
            1, api::MessageType::SPLITBUCKET_REPLY);
}

TEST_F(BucketManagerTest, join_reply_ordered_after_bucket_reply) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucketA(17, 0);
    document::BucketId bucketB(17, 1 << 16);
    document::BucketId parent(16, 0);
    fixture.setUp(WithBuckets()
                  .add(bucketA, api::BucketInfo(50, 100, 200))
                  .add(bucketB, api::BucketInfo(100, 200, 400)));
    auto guard = fixture.acquireBucketLockAndSendInfoRequest(bucketB);

    auto joinCmd = std::make_shared<api::JoinBucketsCommand>(makeDocumentBucket(parent));
    joinCmd->getSourceBuckets().assign({bucketA, bucketB});
    _top->sendDown(joinCmd);
    fixture.bounceWithReply(*joinCmd);

    guard.unlock();
    fixture.assertOrderedAfterBucketReply(
            1, api::MessageType::JOINBUCKETS_REPLY);
}

// Technically, deletes being ordered after bucket info replies won't help
// correctness since buckets are removed from the distributor DB upon _sending_
// the delete and not receiving it.
TEST_F(BucketManagerTest, delete_reply_ordered_after_bucket_reply) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucketA(17, 0);
    document::BucketId bucketB(17, 1);
    fixture.setUp(WithBuckets()
                  .add(bucketA, api::BucketInfo(50, 100, 200))
                  .add(bucketB, api::BucketInfo(100, 200, 400)));
    auto guard = fixture.acquireBucketLockAndSendInfoRequest(bucketB);

    auto deleteCmd = std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bucketA));
    _top->sendDown(deleteCmd);
    fixture.bounceWithReply(*deleteCmd);

    guard.unlock();

    fixture.assertOrderedAfterBucketReply(
            1, api::MessageType::DELETEBUCKET_REPLY);
}

TEST_F(BucketManagerTest, only_enqueue_when_processing_request) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucketA(17, 0);
    fixture.setUp(WithBuckets()
                  .add(bucketA, api::BucketInfo(50, 100, 200)));

    // Process delete command _before_ processing bucket requests.
    auto deleteCmd = std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bucketA));
    _top->sendDown(deleteCmd);
    fixture.bounceWithReply(*deleteCmd);
    // Should arrive happily on the top.
    _top->waitForMessages(1, MESSAGE_WAIT_TIME);
}

// Bucket info requests that contain a specific set of buckets are handled
// differently than full bucket info fetches and are not delegated to the
// worker thread. We still require that any split/joins etc are ordered after
// this reply if their reply is sent up concurrently.
TEST_F(BucketManagerTest, order_replies_after_bucket_specific_request) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucketA(17, 0);
    fixture.setUp(WithBuckets()
                  .add(bucketA, api::BucketInfo(50, 100, 200)));

    auto guard = fixture.acquireBucketLock(bucketA);

    auto infoRoundtrip = std::async(std::launch::async, [&]() {
        std::vector<document::BucketId> buckets{bucketA};
        auto infoCmd = std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), buckets);
        // Can't complete until `guard` has been unlocked.
        _top->sendDown(infoCmd);
        // Barrier: bucket reply and subsequent split reply
        _top->waitForMessages(2, MESSAGE_WAIT_TIME);
    });
    waitUntilRequestsAreProcessing();
    // Barrier: roundtrip thread now blocked. Send a split whose reply shall
    // be enqueued since there's a RequestBucketInfo currently doing its thing.
    auto splitCmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucketA));
    _top->sendDown(splitCmd);
    // Enqueuing happens synchronously in this thread, so no need for further
    // synchronization.
    fixture.bounceWithReply(*splitCmd);

    guard.unlock();
    infoRoundtrip.get();
    // At this point, we know 2 messages are in the top queue since the
    // async future guarantees this for completion.
    fixture.assertOrderedAfterBucketReply(
            1, api::MessageType::SPLITBUCKET_REPLY);
}

// Test is similar to order_replies_after_bucket_specific_request, but has
// two concurrent bucket info request processing instances going on; one in
// the worker thread and one in the message chain itself. Since we only have
// one queue, we must wait with dispatching replies until _all_ processing
// has ceased.
TEST_F(BucketManagerTest, queued_replies_only_dispatched_when_all_processing_done) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucketA(17, 0);
    fixture.setUp(WithBuckets()
                  .add(bucketA, api::BucketInfo(50, 100, 200)));

    auto guard = fixture.acquireBucketLock(bucketA);

    auto singleBucketInfo = std::async(std::launch::async, [&]() {
        std::vector<document::BucketId> buckets{bucketA};
        auto infoCmd = std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), buckets);
        _top->sendDown(infoCmd);
        _top->waitForMessages(3, MESSAGE_WAIT_TIME);
    });
    waitUntilRequestsAreProcessing(1);
    auto fullFetch = std::async(std::launch::async, [&]() {
        _top->sendDown(fixture.createFullFetchCommand());
        _top->waitForMessages(3, MESSAGE_WAIT_TIME);
    });
    waitUntilRequestsAreProcessing(2);
    auto splitCmd = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucketA));
    _top->sendDown(splitCmd);
    fixture.bounceWithReply(*splitCmd);

    guard.unlock();
    singleBucketInfo.get();
    fullFetch.get();

    fixture.assertOrderedAfterBucketReply(
            2, api::MessageType::SPLITBUCKET_REPLY);
}

// Hide boring, repetetive code to allow for chaining of setters (and auto-
// generation of getters and member vars) behind a macro.
#ifdef BUILDER_PARAM
#  error "Redefinition of existing macro `BUILDER_PARAM`"
#endif
#define BUILDER_PARAM(type, name) \
    type _ ## name; \
    auto& name(const type& name ## _) { _ ## name = name ## _; return *this; } \
    const type & name() const { return _ ## name; }

struct TestParams {
    TestParams();
    TestParams(const TestParams &);
    ~TestParams();
    BUILDER_PARAM(document::BucketId, bucket);
    BUILDER_PARAM(document::BucketId, remappedTo);
    BUILDER_PARAM(api::StorageCommand::SP, documentMutation);
    BUILDER_PARAM(api::StorageCommand::SP, treeMutation);
    BUILDER_PARAM(std::vector<const api::MessageType*>, expectedOrdering);
};

TestParams::TestParams() = default;
TestParams::TestParams(const TestParams &) = default;
TestParams::~TestParams() = default;

void
BucketManagerTest::doTestMutationOrdering(
        ConcurrentOperationFixture& fixture,
        const TestParams& params)
{
    fixture.setUp(WithBuckets()
            .add(params.bucket(), api::BucketInfo(50, 100, 200)));
    // Have to send down mutating command _before_ we take bucket lock, as the
    // bucket manager acquires a lock for bucket on the way down in order to
    // check the timestamp of the message vs the last modified timestamp of
    // the bucket itself (offers some time travelling clock protection).
    _top->sendDown(params.documentMutation());
    auto guard = fixture.acquireBucketLockAndSendInfoRequest(params.bucket());

    _top->sendDown(params.treeMutation());
    // Unless "conflicting" mutation replies are enqueued after splits et al,
    // they will bypass the lock and arrive in an inverse order of execution
    // at the distributor. Note that we send replies in the opposite order their
    // commands were sent down, but this is an artifact of ordering commands
    // to avoid test deadlocks, and priorities may alter the execution order
    // anyway. The important thing is that reply orders are not altered.
    fixture.bounceWithReply(*params.treeMutation());
    fixture.bounceWithReply(*params.documentMutation(),
                            api::ReturnCode::OK,
                            params.remappedTo());
    guard.unlock();

    fixture.assertReplyOrdering(params.expectedOrdering());
}

void
BucketManagerTest::doTestConflictingReplyIsEnqueued(
        const document::BucketId& bucket,
        const api::StorageCommand::SP& treeMutationCmd,
        const api::MessageType& treeMutationReplyType)
{
    ConcurrentOperationFixture fixture(*this);

    // We don't check all combinations of document operation replies vs
    // bucket operation replies, just RemoveReply vs all bucket ops.
    auto params = TestParams()
        .bucket(bucket)
        .documentMutation(fixture.createRemoveCommand(bucket))
        .treeMutation(treeMutationCmd)
        .expectedOrdering({&api::MessageType::REQUESTBUCKETINFO_REPLY,
                           &treeMutationReplyType,
                           &api::MessageType::REMOVE_REPLY});

    doTestMutationOrdering(fixture, params);
}

TEST_F(BucketManagerTest, mutation_replies_for_split_bucket_are_enqueued) {
    document::BucketId bucket(17, 0);
    doTestConflictingReplyIsEnqueued(
            bucket,
            std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucket)),
            api::MessageType::SPLITBUCKET_REPLY);
}

TEST_F(BucketManagerTest, mutation_replies_for_deleted_bucket_are_enqueued) {
    document::BucketId bucket(17, 0);
    doTestConflictingReplyIsEnqueued(
            bucket,
            std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bucket)),
            api::MessageType::DELETEBUCKET_REPLY);
}

TEST_F(BucketManagerTest, mutation_replies_for_joined_bucket_are_enqueued) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucketA(17, 0);
    document::BucketId bucketB(17, 1 << 16);
    document::BucketId parent(16, 0);
    // We only test for the parent bucket, since that's what queued operations
    // will be remapped to after a successful join.
    auto joinCmd = std::make_shared<api::JoinBucketsCommand>(makeDocumentBucket(parent));
    joinCmd->getSourceBuckets().assign({bucketA, bucketB});

    auto params = TestParams()
        .bucket(parent)
        .documentMutation(fixture.createRemoveCommand(parent))
        .treeMutation(joinCmd)
        .expectedOrdering({&api::MessageType::REQUESTBUCKETINFO_REPLY,
                           &api::MessageType::JOINBUCKETS_REPLY,
                           &api::MessageType::REMOVE_REPLY});

    doTestMutationOrdering(fixture, params);
}

TEST_F(BucketManagerTest, conflicting_put_replies_are_enqueued) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucket(17, 0);

    auto params = TestParams()
        .bucket(bucket)
        .documentMutation(fixture.createPutCommand(bucket))
        .treeMutation(std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucket)))
        .expectedOrdering({&api::MessageType::REQUESTBUCKETINFO_REPLY,
                           &api::MessageType::SPLITBUCKET_REPLY,
                           &api::MessageType::PUT_REPLY});

    doTestMutationOrdering(fixture, params);
}

TEST_F(BucketManagerTest, conflicting_update_replies_are_enqueued) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucket(17, 0);

    auto params = TestParams()
        .bucket(bucket)
        .documentMutation(fixture.createUpdateCommand(bucket))
        .treeMutation(std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucket)))
        .expectedOrdering({&api::MessageType::REQUESTBUCKETINFO_REPLY,
                           &api::MessageType::SPLITBUCKET_REPLY,
                           &api::MessageType::UPDATE_REPLY});

    doTestMutationOrdering(fixture, params);
}

/**
 * After a split or join, any messages bound for the original bucket(s) that
 * are currently in the persistence queues will be remapped to the bucket
 * resulting from the operation. We have to make sure remapped operations are
 * enqueued as well.
 */
TEST_F(BucketManagerTest, remapped_mutation_is_checked_against_original_bucket) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucket(17, 0);
    document::BucketId remappedToBucket(18, 0);

    auto params = TestParams()
        .bucket(bucket)
        .documentMutation(fixture.createRemoveCommand(bucket))
        .remappedTo(remappedToBucket)
        .treeMutation(std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucket)))
        .expectedOrdering({&api::MessageType::REQUESTBUCKETINFO_REPLY,
                           &api::MessageType::SPLITBUCKET_REPLY,
                           &api::MessageType::REMOVE_REPLY});

    doTestMutationOrdering(fixture, params);
}

void
BucketManagerTest::scheduleBucketInfoRequestWithConcurrentOps(
        ConcurrentOperationFixture& fixture,
        const document::BucketId& bucketForRemove,
        const document::BucketId& bucketForSplit,
        api::Timestamp mutationTimestamp)
{
    auto mutation(
            fixture.createRemoveCommand(bucketForRemove, mutationTimestamp));
    _top->sendDown(mutation);
    auto guard = fixture.acquireBucketLockAndSendInfoRequest(
            bucketForRemove);

    auto conflictingOp(
            std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucketForSplit)));
    _top->sendDown(conflictingOp);
    fixture.bounceWithReply(*conflictingOp);
    fixture.bounceWithReply(*mutation);
    guard.unlock();
}

TEST_F(BucketManagerTest, bucket_conflict_set_is_cleared_between_blocking_requests) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId firstConflictBucket(17, 0);
    document::BucketId secondConflictBucket(18, 0);

    fixture.setUp(WithBuckets()
                  .add(firstConflictBucket, api::BucketInfo(50, 100, 200))
                  .add(secondConflictBucket, api::BucketInfo(60, 200, 300)));

    // Do a single round of starting and completing a request bucket info
    // command with queueing and adding of `firstConflictBucket` to the set
    // of conflicting buckets.
    scheduleBucketInfoRequestWithConcurrentOps(
            fixture, firstConflictBucket,
            firstConflictBucket, api::Timestamp(1000));

    // Barrier for completion of first round of replies. Subsequently remove
    // all replies to get a clean slate.
    fixture.awaitAndGetReplies(3);
    fixture.clearReceivedReplies();

    // Do a second round with a different bucket as the conflict. The
    // mutation towards the first conflict bucket should now _not_ be queued
    // as it was for an entirely different request bucket round.
    scheduleBucketInfoRequestWithConcurrentOps(
            fixture, firstConflictBucket,
            secondConflictBucket, api::Timestamp(1001));

    // Remove is not ordered after the split here since it should not be
    // queued.
    fixture.assertReplyOrdering({&api::MessageType::REMOVE_REPLY,
                                 &api::MessageType::REQUESTBUCKETINFO_REPLY,
                                 &api::MessageType::SPLITBUCKET_REPLY});
}

void
BucketManagerTest::sendSingleBucketInfoRequest(const document::BucketId& id)
{
    std::vector<document::BucketId> buckets{id};
    auto infoCmd = std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), buckets);
    _top->sendDown(infoCmd);
}

TEST_F(BucketManagerTest, conflict_set_only_cleared_after_all_bucket_requests_done) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucketA(16, 0);
    document::BucketId bucketB(16, 1);

    fixture.setUp(WithBuckets()
                  .add(bucketA, api::BucketInfo(50, 100, 200))
                  .add(bucketB, api::BucketInfo(60, 200, 300)));

    auto mutation = fixture.createRemoveCommand(bucketA);
    _top->sendDown(mutation);

    auto guardA = fixture.acquireBucketLock(bucketA);
    auto guardB = fixture.acquireBucketLock(bucketB);

    auto singleBucketInfoA = std::async(std::launch::async, [&]() {
        sendSingleBucketInfoRequest(bucketA);
        _top->waitForMessages(4, MESSAGE_WAIT_TIME);
    });
    waitUntilRequestsAreProcessing(1);
    auto singleBucketInfoB = std::async(std::launch::async, [&]() {
        sendSingleBucketInfoRequest(bucketB);
        _top->waitForMessages(4, MESSAGE_WAIT_TIME);
    });
    // Barrier: after this point, both tasks are in the protected section.
    // Neither async bucket info request can proceed as long as there are
    // guards holding their desired bucket locks.
    waitUntilRequestsAreProcessing(2);

    auto conflictingOp = std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucketA));
    _top->sendDown(conflictingOp);
    fixture.bounceWithReply(*conflictingOp);
    // Releasing guard A (and allowing the request for A to go through) should
    // _not_ clear the conflict set. I.e. if we send a mutation reply for a
    // conflicted bucket up at this point, it should be enqueued after the
    // split reply.
    guardA.unlock();
    _top->waitForMessages(1, MESSAGE_WAIT_TIME); // Completion barrier for A.
    fixture.bounceWithReply(*mutation);
    // Allow B to go through. This _should_ clear the conflict set and dequeue
    // any conflicted mutations after their conflicting ops.
    guardB.unlock();
    singleBucketInfoA.get();
    singleBucketInfoB.get();
    // Note: request bucket info reply is dispatched up _before_ protected
    // section guard goes out of scope, so reply is ordered before conflicts.
    fixture.assertReplyOrdering({&api::MessageType::REQUESTBUCKETINFO_REPLY,
                                 &api::MessageType::REQUESTBUCKETINFO_REPLY,
                                 &api::MessageType::SPLITBUCKET_REPLY,
                                 &api::MessageType::REMOVE_REPLY});
}

void
BucketManagerTest::assertRequestWithBadHashIsRejected(
        ConcurrentOperationFixture& fixture)
{
    // Test by default sets up 10 nodes in config. Pretend we only know of 3.
    auto infoCmd = fixture.createFullFetchCommandWithHash("(0;0;1;2)");
    _top->sendDown(infoCmd);
    auto replies = fixture.awaitAndGetReplies(1);
    auto& reply = dynamic_cast<api::RequestBucketInfoReply&>(*replies[0]);
    ASSERT_EQ(api::ReturnCode::REJECTED, reply.getResult().getResult());
}

TEST_F(BucketManagerTest, reject_request_with_mismatching_distribution_hash) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucket(17, 0);
    fixture.setUp(WithBuckets().add(bucket, api::BucketInfo(50, 100, 200)));
    assertRequestWithBadHashIsRejected(fixture);
}

TEST_F(BucketManagerTest, db_not_iterated_when_all_requests_rejected) {
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucket(17, 0);
    fixture.setUp(WithBuckets().add(bucket, api::BucketInfo(50, 100, 200)));
    auto guard = fixture.acquireBucketLock(bucket);
    // We've got a bucket locked, so iff the manager actually starts processing
    // buckets even though it has no requests active, it will stall while
    // waiting for the lock to be released. When we then send down an additional
    // bucket info request, this request will either be rejected immediately (if
    // the db is NOT processed) or time out and fail the test.
    assertRequestWithBadHashIsRejected(fixture);
    fixture.clearReceivedReplies();

    auto infoCmd = fixture.createFullFetchCommandWithHash("(0;0;1;2)");
    _top->sendDown(infoCmd);
    auto replies = fixture.awaitAndGetReplies(1);
}

// It's possible for the request processing thread and onSetSystemState (which use
// the same mutex) to race with the actual internal component cluster state switch-over.
// Ensure we detect and handle this by bouncing the request back to the distributor.
// It's for all intents and purposes guaranteed that the internal state has converged
// once the distributor has gotten around to retrying the operation.
TEST_F(BucketManagerTest, bounce_request_on_internal_cluster_state_version_mismatch) {
    ConcurrentOperationFixture f(*this);

    // Make manager-internal and component-internal version state inconsistent
    f.update_cluster_state(lib::ClusterState("version:2 distributor:1 storage:1"));
    _manager->onDown(std::make_shared<api::SetSystemStateCommand>(lib::ClusterState("version:3 distributor:1 storage:1")));

    // Info command is sent with state version 2, which mismatches that of internal state 3
    // even though it's the same as the component's current version.
    _top->sendDown(f.createFullFetchCommand());

    auto replies = f.awaitAndGetReplies(1);
    auto& reply = dynamic_cast<api::RequestBucketInfoReply&>(*replies[0]);
    EXPECT_EQ(api::ReturnCode::REJECTED, reply.getResult().getResult());
}

// This tests a slightly different inconsistency than the above test; the node has
// locally enabled the cluster state (i.e. initially observed version == enabled version),
// but is not yet done processing side effects from doing so.
// See comments in BucketManager::onSetSystemState[Reply]() for rationale
TEST_F(BucketManagerTest, bounce_request_on_state_change_barrier_not_reached) {
    ConcurrentOperationFixture f(*this);

    // Make manager-internal and component-internal version state inconsistent
    f.update_cluster_state(lib::ClusterState("version:2 distributor:1 storage:1"));
    auto new_state = lib::ClusterState("version:3 distributor:1 storage:1");
    auto state_cmd = std::make_shared<api::SetSystemStateCommand>(new_state);
    _top->sendDown(state_cmd);
    _bottom->waitForMessage(api::MessageType::SETSYSTEMSTATE, MESSAGE_WAIT_TIME);
    (void)_bottom->getCommandsOnce();
    _node->setClusterState(new_state);

    // At this point, the node's internal cluster state matches that of the state command
    // which was observed on the way down. But there may still be side effects pending from
    // enabling the cluster state. So we must still reject requests until we have observed
    // the reply for the state command (which must order after any and all side effects).

    _top->sendDown(f.createFullFetchCommand());
    auto replies = f.awaitAndGetReplies(1);
    {
        auto& reply = dynamic_cast<api::RequestBucketInfoReply&>(*replies[0]);
        EXPECT_EQ(api::ReturnCode::REJECTED, reply.getResult().getResult());
    }
    (void)_top->getRepliesOnce();

    // Once the cluster state reply has been observed, requests can go through as expected.
    _manager->onUp(std::shared_ptr<api::StorageReply>(state_cmd->makeReply()));
    _top->waitForMessage(api::MessageType::SETSYSTEMSTATE_REPLY, MESSAGE_WAIT_TIME);
    (void)_top->getRepliesOnce();

    _top->sendDown(f.createFullFetchCommand(new_state));
    replies = f.awaitAndGetReplies(1);
    {
        auto& reply = dynamic_cast<api::RequestBucketInfoReply&>(*replies[0]);
        EXPECT_EQ(api::ReturnCode::OK, reply.getResult().getResult());
    }
}

} // storage
