// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config/helper/configgetter.h>
#include <cppunit/extensions/HelperMacros.h>
#include <vespa/document/config/config-documenttypes.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/fieldvalue/document.h>
#include <vespa/document/update/documentupdate.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/storage/bucketdb/bucketmanager.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/vdslib/state/random.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/testkit/testapp.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <vespa/config/helper/configgetter.hpp>
#include <future>

#include <vespa/log/log.h>
LOG_SETUP(".test.bucketdb.bucketmanager");

using config::ConfigGetter;
using document::DocumenttypesConfig;
using config::FileSpec;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::test::makeDocumentBucket;
using document::test::makeBucketSpace;

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

struct BucketManagerTest : public CppUnit::TestFixture {
public:
    CPPUNIT_TEST_SUITE(BucketManagerTest);
    CPPUNIT_TEST(testRequestBucketInfoWithList);
    CPPUNIT_TEST(testDistributionBitGenerationEmpty);
    CPPUNIT_TEST(testDistributionBitChangeOnCreateBucket);
    CPPUNIT_TEST(testMinUsedBitsFromComponentIsHonored);
    CPPUNIT_TEST(testRemoveLastModifiedOK);
    CPPUNIT_TEST(testRemoveLastModifiedFailed);
    CPPUNIT_TEST(testSwallowNotifyBucketChangeReply);
    CPPUNIT_TEST(testMetricsGeneration);
    CPPUNIT_TEST(testSplitReplyOrderedAfterBucketReply);
    CPPUNIT_TEST(testJoinReplyOrderedAfterBucketReply);
    CPPUNIT_TEST(testDeleteReplyOrderedAfterBucketReply);
    CPPUNIT_TEST(testOnlyEnqueueWhenProcessingRequest);
    CPPUNIT_TEST(testOrderRepliesAfterBucketSpecificRequest);
    CPPUNIT_TEST(testQueuedRepliesOnlyDispatchedWhenAllProcessingDone);
    CPPUNIT_TEST(testMutationRepliesForSplitBucketAreEnqueued);
    CPPUNIT_TEST(testMutationRepliesForDeletedBucketAreEnqueued);
    CPPUNIT_TEST(testMutationRepliesForJoinedBucketAreEnqueued);
    CPPUNIT_TEST(testConflictingPutRepliesAreEnqueued);
    CPPUNIT_TEST(testConflictingUpdateRepliesAreEnqueued);
    CPPUNIT_TEST(testRemappedMutationIsCheckedAgainstOriginalBucket);
    CPPUNIT_TEST(testBucketConflictSetIsClearedBetweenBlockingRequests);
    CPPUNIT_TEST(testConflictSetOnlyClearedAfterAllBucketRequestsDone);
    CPPUNIT_TEST(testRejectRequestWithMismatchingDistributionHash);
    CPPUNIT_TEST(testDbNotIteratedWhenAllRequestsRejected);

    // FIXME(vekterli): test is not deterministic and enjoys failing
    // sporadically when running under Valgrind. See bug 5932891.
    CPPUNIT_TEST_IGNORED(testRequestBucketInfoWithState);
    CPPUNIT_TEST_SUITE_END();

    std::unique_ptr<TestServiceLayerApp> _node;
    std::unique_ptr<DummyStorageLink> _top;
    BucketManager *_manager;
    DummyStorageLink* _bottom;
    FileStorManager* _filestorManager;
    std::map<document::BucketId, TestBucketInfo> _bucketInfo;
    uint32_t _emptyBuckets;
    document::Document::SP _document;

    void setupTestEnvironment(bool fakePersistenceLayer = true,
                              bool noDelete = false);
    void addBucketsToDB(uint32_t count);
    bool wasBlockedDueToLastModified(api::StorageMessage* msg,
                                     uint64_t lastModified);
    bool wasBlockedDueToLastModified(api::StorageMessage::SP msg);
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


    void testRequestBucketInfoWithState();
    void testRequestBucketInfoWithList();
    void testDistributionBitGenerationEmpty();
    void testDistributionBitChangeOnCreateBucket();
    void testMinUsedBitsFromComponentIsHonored();

    void testRemoveLastModifiedOK();
    void testRemoveLastModifiedFailed();

    void testSwallowNotifyBucketChangeReply();
    void testMetricsGeneration();
    void testSplitReplyOrderedAfterBucketReply();
    void testJoinReplyOrderedAfterBucketReply();
    void testDeleteReplyOrderedAfterBucketReply();
    void testOnlyEnqueueWhenProcessingRequest();
    void testOrderRepliesAfterBucketSpecificRequest();
    void testQueuedRepliesOnlyDispatchedWhenAllProcessingDone();
    void testMutationRepliesForSplitBucketAreEnqueued();
    void testMutationRepliesForDeletedBucketAreEnqueued();
    void testMutationRepliesForJoinedBucketAreEnqueued();
    void testConflictingPutRepliesAreEnqueued();
    void testConflictingUpdateRepliesAreEnqueued();
    void testRemappedMutationIsCheckedAgainstOriginalBucket();
    void testBucketConflictSetIsClearedBetweenBlockingRequests();
    void testConflictSetOnlyClearedAfterAllBucketRequestsDone();
    void testRejectRequestWithMismatchingDistributionHash();
    void testDbNotIteratedWhenAllRequestsRejected();
    void testReceivedDistributionHashIsNormalized();

public:
    static constexpr uint32_t DIR_SPREAD = 3;
    static constexpr uint32_t MESSAGE_WAIT_TIME = 60*2;


    void setUp() override {
        _emptyBuckets = 0;
    }

    void tearDown() override {
    }

    friend class ConcurrentOperationFixture;
};

CPPUNIT_TEST_SUITE_REGISTRATION(BucketManagerTest);

#define ASSERT_DUMMYLINK_REPLY_COUNT(link, count) \
    if (link->getNumReplies() != count) { \
        std::ostringstream ost; \
        ost << "Expected there to be " << count << " replies in link, but " \
            << "found " << link->getNumReplies() << ":\n"; \
        for (uint32_t i=0; i<link->getNumReplies(); ++i) { \
            ost << link->getReply(i)->getType() << "\n"; \
        } \
        CPPUNIT_FAIL(ost.str()); \
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

    std::shared_ptr<const DocumentTypeRepo> repo(new DocumentTypeRepo(
                *ConfigGetter<DocumenttypesConfig>::getConfig(
                    "config-doctypes", FileSpec(TEST_PATH("config-doctypes.cfg")))));
    _top.reset(new DummyStorageLink);
    _node.reset(new TestServiceLayerApp(
                DiskCount(2), NodeIndex(0), config.getConfigId()));
    _node->setTypeRepo(repo);
    _node->setupDummyPersistence();
        // Set up the 3 links
    StorageLink::UP manager(new BucketManager("", _node->getComponentRegister()));
    _manager = (BucketManager*) manager.get();
    _top->push_back(std::move(manager));
    if (fakePersistenceLayer) {
        StorageLink::UP bottom(new DummyStorageLink);
        _bottom = (DummyStorageLink*) bottom.get();
        _top->push_back(std::move(bottom));
    } else {
        StorageLink::UP bottom(new FileStorManager(
                    config.getConfigId(), _node->getPartitions(),
                    _node->getPersistenceProvider(), _node->getComponentRegister()));
        _filestorManager = (FileStorManager*) bottom.get();
        _top->push_back(std::move(bottom));
    }
        // Generate a doc to use for testing..
    const DocumentType &type(*_node->getTypeRepo()
                             ->getDocumentType("text/html"));
    _document.reset(new document::Document(type, document::DocumentId(
                            document::DocIdString("test", "ntnu"))));
}

void BucketManagerTest::addBucketsToDB(uint32_t count)
{
    _bucketInfo.clear();
    _emptyBuckets = 0;
    lib::RandomGen randomizer(25423);
    while (_bucketInfo.size() < count) {
        document::BucketId id(16, randomizer.nextUint32());
        id = id.stripUnused();
        if (_bucketInfo.size() == 0) {
            id = _node->getBucketIdFactory().getBucketId(
                    _document->getId()).stripUnused();
        }
        TestBucketInfo info;
        info.crc = randomizer.nextUint32();
        info.size = randomizer.nextUint32();
        info.count = randomizer.nextUint32(1, 0xFFFF);

        info.partition = _node->getPartition(id);
        _bucketInfo[id] = info;
    }

    // Make sure we have at least one empty bucket
    TestBucketInfo& info = (++_bucketInfo.begin())->second;
    CPPUNIT_ASSERT(info.size != 0);
    info.size = 0;
    info.count = 0;
    info.crc = 0;
    ++_emptyBuckets;
    for (std::map<document::BucketId, TestBucketInfo>::iterator it
            = _bucketInfo.begin(); it != _bucketInfo.end(); ++it)
    {
        bucketdb::StorageBucketInfo entry;
        entry.disk = it->second.partition;
        entry.setBucketInfo(api::BucketInfo(it->second.crc,
                                           it->second.count,
                                           it->second.size));
        _node->getStorageBucketDatabase().insert(it->first, entry, "foo");
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
        entry.disk = 0;
        _node->getStorageBucketDatabase().insert(id, entry, "foo");
    }

    _top->open();

    _top->sendDown(api::StorageMessage::SP(msg));
    if (_top->getNumReplies() == 1) {
        CPPUNIT_ASSERT_EQUAL(0, (int)_bottom->getNumCommands());
        CPPUNIT_ASSERT(!static_cast<api::StorageReply&>(
                               *_top->getReply(0)).getResult().success());
        return true;
    } else {
        CPPUNIT_ASSERT_EQUAL(0, (int)_top->getNumReplies());

        // Check that bucket database now has the operation's timestamp as last modified.
        {
            StorBucketDatabase::WrappedEntry entry(
                    _node->getStorageBucketDatabase().get(id, "foo"));
            CPPUNIT_ASSERT_EQUAL(lastModified, entry->info.getLastModified());
        }

        return false;
    }
}

void BucketManagerTest::testRemoveLastModifiedOK()
{
    CPPUNIT_ASSERT(!wasBlockedDueToLastModified(
                           new api::RemoveCommand(makeDocumentBucket(document::BucketId(16, 1)),
                                   document::DocumentId("userdoc:m:1:foo"),
                                   api::Timestamp(1235)),
                           1235));
}


void BucketManagerTest::testRemoveLastModifiedFailed()
{
    CPPUNIT_ASSERT(wasBlockedDueToLastModified(
                           new api::RemoveCommand(makeDocumentBucket(document::BucketId(16, 1)),
                                   document::DocumentId("userdoc:m:1:foo"),
                                   api::Timestamp(1233)),
                           1233));
}

void BucketManagerTest::testDistributionBitGenerationEmpty()
{
    TestName("BucketManagerTest::testDistributionBitGenerationEmpty()");
    setupTestEnvironment();
    _manager->doneInit();
    vespalib::Monitor l;
    _manager->updateMetrics(BucketManager::MetricLockGuard(l));
    CPPUNIT_ASSERT_EQUAL(58u, _node->getStateUpdater().getReportedNodeState()->getMinUsedBits());
}

void BucketManagerTest::testDistributionBitChangeOnCreateBucket()
{
    TestName("BucketManagerTest::testDistributionBitChangeOnCreateBucket()");
    setupTestEnvironment();
    addBucketsToDB(30);
    _top->open();
    _node->getDoneInitializeHandler().notifyDoneInitializing();
    _manager->doneInit();
    _manager->updateMinUsedBits();
    CPPUNIT_ASSERT_EQUAL(16u, _node->getStateUpdater().getReportedNodeState()->getMinUsedBits());

    std::shared_ptr<api::CreateBucketCommand> cmd(
            new api::CreateBucketCommand(makeDocumentBucket(document::BucketId(4, 5678))));
    _top->sendDown(cmd);
    CPPUNIT_ASSERT_EQUAL(4u, _node->getStateUpdater().getReportedNodeState()->getMinUsedBits());
}

void BucketManagerTest::testMinUsedBitsFromComponentIsHonored()
{
    TestName("BucketManagerTest::testMinUsedBitsFromComponentIsHonored()");
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
    CPPUNIT_ASSERT_EQUAL(13u, _node->getStateUpdater().getReportedNodeState()->getMinUsedBits());
}

void BucketManagerTest::testRequestBucketInfoWithState()
{
    TestName("BucketManagerTest::testRequestBucketInfoWithState()");
        // Test prior to building bucket cache
    setupTestEnvironment();
    addBucketsToDB(30);
     /* Currently this is just queued up
    {
        std::shared_ptr<api::RequestBucketInfoCommand> cmd(
                new api::RequestBucketInfoCommand(
                    0, lib::ClusterState("distributor:3 .2.s:d storage:1")));
        _top->sendDown(cmd);
        _top->waitForMessages(1, 5);
        CPPUNIT_ASSERT_EQUAL((size_t) 1, _top->getNumReplies());
        std::shared_ptr<api::RequestBucketInfoReply> reply(
                std::dynamic_pointer_cast<api::RequestBucketInfoReply>(
                    _top->getReply(0)));
        _top->reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::NOT_READY),
                             reply->getResult());
    } */
    std::vector<lib::ClusterState> states;
    states.push_back(lib::ClusterState("version:0"));
    states.push_back(lib::ClusterState("version:1 distributor:1 storage:1"));
    states.push_back(lib::ClusterState(
                "version:2 distributor:3 .1.s:i .2.s:d storage:4"));
    states.push_back(lib::ClusterState(
                "version:3 distributor:3 .1.s:i .2.s:d storage:4 .3.s:d"));
    states.push_back(lib::ClusterState(
                "version:4 distributor:3 .1.s:i .2.s:d storage:4"));

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
        CPPUNIT_ASSERT(reply1.get());
        CPPUNIT_ASSERT(reply2.get());
        CPPUNIT_ASSERT(reply3.get());
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::REJECTED,
                "Ignoring bucket info request for cluster state version 1 as "
                "versions from version 2 differs from this state."),
                             reply1->getResult());
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::REJECTED,
                "There is already a newer bucket info request for "
                "this node from distributor 0"),
                             reply2->getResult());
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::OK),
                             reply3->getResult());
        api::RequestBucketInfoReply::Entry entry;

        CPPUNIT_ASSERT_EQUAL((size_t) 18, reply3->getBucketInfo().size());
        entry = api::RequestBucketInfoReply::Entry(
                document::BucketId(16, 0xe8c8), api::BucketInfo(0x79d04f78, 11153, 1851385240u));
        CPPUNIT_ASSERT_EQUAL(entry, reply3->getBucketInfo()[0]);
    }
}

namespace {
    struct PopenWrapper {
        FILE* _file;
        std::vector<char> _buffer;
        uint32_t _index;
        uint32_t _size;
        bool _eof;

        PopenWrapper(const std::string& cmd)
            : _buffer(65536, '\0'), _index(0), _size(0), _eof(false)
        {
            _file = popen(cmd.c_str(), "r");
            if (_file == 0) {
                throw vespalib::Exception("Failed to run '" + cmd
                        + "' in popen: " + strerror(errno), VESPA_STRLOC);
            }
        }

        const char* getNextLine() {
            if (_eof && _size == 0) return 0;
                // Check if we have a newline waiting
            char* newline = strchr(&_buffer[_index], '\n');
                // If not try to get one
            if (_eof) {
                newline = &_buffer[_index + _size];
            } else if (newline == 0) {
                    // If we index is passed half the buffer, reposition
                if (_index > _buffer.size() / 2) {
                    memcpy(&_buffer[0], &_buffer[_index], _size);
                    _index = 0;
                }
                    // Verify we have space to write to
                if (_index + _size >= _buffer.size()) {
                    throw vespalib::Exception("No newline could be find in "
                            "half the buffer size. Wrapper not designed to "
                            "handle that long lines (1)", VESPA_STRLOC);
                }
                    // Fill up buffer
                size_t bytesRead = fread(&_buffer[_index + _size],
                                         1, _buffer.size() - _index - _size - 1,
                                         _file);
                if (bytesRead == 0) {
                    if (!feof(_file)) {
                        throw vespalib::Exception("Failed to run fgets: "
                                + std::string(strerror(errno)), VESPA_STRLOC);
                    } else {
                        _eof = true;
                    }
                } else {
                    _size += bytesRead;
                }
                newline = strchr(&_buffer[_index], '\n');
                if (newline == 0) {
                    if (_eof) {
                        if (_size == 0) return 0;
                    } else {
                        throw vespalib::Exception("No newline could be find in "
                                "half the buffer size. Wrapper not designed to "
                                "handle that long lines (2)", VESPA_STRLOC);
                    }
                }
            }
            *newline = '\0';
            ++newline;
            const char* line = &_buffer[_index];
            uint32_t strlen = (newline - line);
            _index += strlen;
            _size -= strlen;
            return line;
        }
    };
}

void BucketManagerTest::testRequestBucketInfoWithList()
{
    TestName("BucketManagerTest::testRequestBucketInfoWithList()");
    setupTestEnvironment();
    addBucketsToDB(30);
    _top->open();
    _node->getDoneInitializeHandler().notifyDoneInitializing();
    _top->doneInit();
    {
        std::vector<document::BucketId> bids;
        bids.push_back(document::BucketId(16, 0xe8c8));

        std::shared_ptr<api::RequestBucketInfoCommand> cmd(
                new api::RequestBucketInfoCommand(makeBucketSpace(), bids));

        _top->sendDown(cmd);
        _top->waitForMessages(1, 5);
        ASSERT_DUMMYLINK_REPLY_COUNT(_top, 1);
        std::shared_ptr<api::RequestBucketInfoReply> reply(
                std::dynamic_pointer_cast<api::RequestBucketInfoReply>(
                    _top->getReply(0)));
        _top->reset();
        CPPUNIT_ASSERT(reply.get());
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode(api::ReturnCode::OK),
                             reply->getResult());
        if (reply->getBucketInfo().size() > 1) {
            std::cerr << "Too many replies found\n";
            for (uint32_t i=0; i<reply->getBucketInfo().size(); ++i) {
                std::cerr << reply->getBucketInfo()[i] << "\n";
            }
        }
        CPPUNIT_ASSERT_EQUAL((size_t) 1, reply->getBucketInfo().size());
        api::RequestBucketInfoReply::Entry entry(
                document::BucketId(16, 0xe8c8),
                api::BucketInfo(0x79d04f78, 11153, 1851385240u));
        CPPUNIT_ASSERT_EQUAL(entry, reply->getBucketInfo()[0]);
    }
}

void
BucketManagerTest::testSwallowNotifyBucketChangeReply()
{
    TestName("BucketManagerTest::testSwallowNotifyBucketChangeReply()");
    setupTestEnvironment();
    addBucketsToDB(30);
    _top->open();
    _node->getDoneInitializeHandler().notifyDoneInitializing();
    _top->doneInit();

    api::NotifyBucketChangeCommand cmd(makeDocumentBucket(document::BucketId(1, 16)),
                                       api::BucketInfo());
    std::shared_ptr<api::NotifyBucketChangeReply> reply(
            new api::NotifyBucketChangeReply(cmd));

    _top->sendDown(reply);
    // Should not leave the bucket manager.
    CPPUNIT_ASSERT_EQUAL(0, (int)_bottom->getNumCommands());    
}

void
BucketManagerTest::testMetricsGeneration()
{
    setupTestEnvironment();
    _top->open();
    // Add 3 buckets; 2 ready, 1 active. 300 docs total, 600 bytes total.
    for (int i = 0; i < 3; ++i) {
        bucketdb::StorageBucketInfo entry;
        entry.disk = 0;
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
    vespalib::Monitor l;
    _manager->updateMetrics(BucketManager::MetricLockGuard(l));

    CPPUNIT_ASSERT_EQUAL(size_t(2), _manager->_metrics->disks.size());
    const DataStoredMetrics& m(*_manager->_metrics->disks[0]);
    CPPUNIT_ASSERT_EQUAL(int64_t(3), m.buckets.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(300), m.docs.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(600), m.bytes.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(1), m.active.getLast());
    CPPUNIT_ASSERT_EQUAL(int64_t(2), m.ready.getLast());
}

void
BucketManagerTest::insertSingleBucket(const document::BucketId& bucket,
                                      const api::BucketInfo& info)
{
    bucketdb::StorageBucketInfo entry;
    entry.disk = 0;
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
    ConcurrentOperationFixture(BucketManagerTest& self)
        : _self(self),
          _state("distributor:1 storage:1")
    {
        _self.setupTestEnvironment();
        _self._top->open();
        _self._node->getDoneInitializeHandler().notifyDoneInitializing();
        _self._manager->startWorkerThread();
        _self._top->doneInit();

        // Need a cluster state to work with initially, so that processing
        // bucket requests can calculate a target distributor.
        _self._node->setClusterState(_state);
        _self._manager->onDown(
                std::make_shared<api::SetSystemStateCommand>(_state));
    }

    void setUp(const WithBuckets& buckets) {
        for (auto& b : buckets._bucketsAndInfo) {
            _self.insertSingleBucket(b.first, b.second);
        }
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
                *_self._node->getTestDocMan().getTypeRepo()
                    .getDocumentType("testdoctype1"),
                document::DocumentId("id:foo:testdoctype1::bar2"));
        return std::make_shared<api::UpdateCommand>(
                makeDocumentBucket(bucket), update, api::Timestamp(123456));
    }

    auto createFullFetchCommand() const {
        return std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), 0, _state);
    }

    auto createFullFetchCommandWithHash(vespalib::stringref hash) const {
        return std::make_shared<api::RequestBucketInfoCommand>(makeBucketSpace(), 0, _state, hash);
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
        CPPUNIT_ASSERT_EQUAL(nTotal, replies.size());
        for (size_t i = 0; i < nBucketReplies; ++i) {
            CPPUNIT_ASSERT_EQUAL(api::MessageType::REQUESTBUCKETINFO_REPLY,
                                 replies[i]->getType());
        }
        CPPUNIT_ASSERT_EQUAL(msgType, replies[nBucketReplies]->getType());
    }

    void assertReplyOrdering(
            const std::vector<const api::MessageType*>& replyTypes)
    {
        auto replies = awaitAndGetReplies(replyTypes.size());
        CPPUNIT_ASSERT_EQUAL(replyTypes.size(), replies.size());
        for (size_t i = 0; i < replyTypes.size(); ++i) {
            CPPUNIT_ASSERT_EQUAL(*replyTypes[i], replies[i]->getType());
        }
    }

    void clearReceivedReplies() {
        _self._top->getRepliesOnce();
    }

private:
    BucketManagerTest& _self;
    lib::ClusterState _state;
};

void
BucketManagerTest::testSplitReplyOrderedAfterBucketReply()
{
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

void
BucketManagerTest::testJoinReplyOrderedAfterBucketReply()
{
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
void
BucketManagerTest::testDeleteReplyOrderedAfterBucketReply()
{
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

void
BucketManagerTest::testOnlyEnqueueWhenProcessingRequest()
{
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
void
BucketManagerTest::testOrderRepliesAfterBucketSpecificRequest()
{
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

// Test is similar to testOrderRepliesAfterBucketSpecificRequest, but has
// two concurrent bucket info request processing instances going on; one in
// the worker thread and one in the message chain itself. Since we only have
// one queue, we must wait with dispatching replies until _all_ processing
// has ceased.
void
BucketManagerTest::testQueuedRepliesOnlyDispatchedWhenAllProcessingDone()
{
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

TestParams::TestParams() { }
TestParams::TestParams(const TestParams &) = default;
TestParams::~TestParams() {}

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

void
BucketManagerTest::testMutationRepliesForSplitBucketAreEnqueued()
{
    document::BucketId bucket(17, 0);
    doTestConflictingReplyIsEnqueued(
            bucket,
            std::make_shared<api::SplitBucketCommand>(makeDocumentBucket(bucket)),
            api::MessageType::SPLITBUCKET_REPLY);
}

void
BucketManagerTest::testMutationRepliesForDeletedBucketAreEnqueued()
{
    document::BucketId bucket(17, 0);
    doTestConflictingReplyIsEnqueued(
            bucket,
            std::make_shared<api::DeleteBucketCommand>(makeDocumentBucket(bucket)),
            api::MessageType::DELETEBUCKET_REPLY);
}

void
BucketManagerTest::testMutationRepliesForJoinedBucketAreEnqueued()
{
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

void
BucketManagerTest::testConflictingPutRepliesAreEnqueued()
{
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

void
BucketManagerTest::testConflictingUpdateRepliesAreEnqueued()
{
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
void
BucketManagerTest::testRemappedMutationIsCheckedAgainstOriginalBucket()
{
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

void
BucketManagerTest::testBucketConflictSetIsClearedBetweenBlockingRequests()
{
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

void
BucketManagerTest::testConflictSetOnlyClearedAfterAllBucketRequestsDone()
{
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
    CPPUNIT_ASSERT_EQUAL(api::ReturnCode::REJECTED,
                         reply.getResult().getResult());
}

void
BucketManagerTest::testRejectRequestWithMismatchingDistributionHash()
{
    ConcurrentOperationFixture fixture(*this);
    document::BucketId bucket(17, 0);
    fixture.setUp(WithBuckets().add(bucket, api::BucketInfo(50, 100, 200)));
    assertRequestWithBadHashIsRejected(fixture);
}

void
BucketManagerTest::testDbNotIteratedWhenAllRequestsRejected()
{
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

} // storage
