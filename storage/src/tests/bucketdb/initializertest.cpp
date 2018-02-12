// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Tests storage initialization without depending on persistence layer.
 */
#include <vespa/storage/bucketdb/storagebucketdbinitializer.h>

#include <vespa/document/base/testdocman.h>

#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <vespa/vdstestlib/cppunit/dirconfig.h>
#include <vespa/vdstestlib/cppunit/macros.h>
#include <vespa/storage/bucketdb/lockablemap.hpp>
#include <vespa/vdstestlib/cppunit/dirconfig.hpp>
#include <vespa/document/bucket/fixed_bucket_spaces.h>

#include <vespa/log/log.h>
LOG_SETUP(".test.bucketdb.initializing");

using document::FixedBucketSpaces;

namespace storage {

typedef uint16_t PartitionId;

struct InitializerTest : public CppUnit::TestFixture {

    class InitParams {
        vdstestlib::DirConfig config;
        bool configFinalized;

    public:
        uint32_t bucketBitsUsed;
        NodeIndex nodeIndex;
        NodeCount nodeCount;
        Redundancy redundancy;
        uint32_t docsPerDisk;
        DiskCount diskCount;
        std::set<uint32_t> disksDown;
        bool bucketWrongDisk;
        bool bucketMultipleDisks;
        bool failingListRequest;
        bool failingInfoRequest;

        InitParams()
            : config(getStandardConfig(true)),
              configFinalized(false),
              bucketBitsUsed(4),
              nodeIndex(0),
              nodeCount(10),
              redundancy(2),
              docsPerDisk(10),
              diskCount(5),
              bucketWrongDisk(false),
              bucketMultipleDisks(false),
              failingListRequest(false),
              failingInfoRequest(false) {}

        void setAllFailures() {
            bucketWrongDisk = true;
            bucketMultipleDisks = true;
            failingListRequest = true;
            failingInfoRequest = true;
        }

        vdstestlib::DirConfig& getConfig() {
            if (!configFinalized) {
                config.getConfig("stor-server")
                      .setValue("node_index", nodeIndex);
                config.getConfig("stor-distribution")
                      .setValue("redundancy", redundancy);
                configFinalized = true;
            }
            return config;
        }

    };

    document::TestDocMan _docMan;

    void testInitialization(InitParams& params);

    /**
     * Test that the status page can be shown during init without a deadlock
     * or crash or anything. Don't validate much output, it might change.
     */
    void testStatusPage();

    /** Test initializing with an empty node. */
    void testInitEmptyNode() {
        InitParams params;
        params.docsPerDisk = 0;
        testInitialization(params);
    }
    /** Test initializing with some data on single disk. */
    void testInitSingleDisk() {
        InitParams params;
        params.diskCount = DiskCount(1);
        testInitialization(params);
    }
    /** Test initializing with multiple disks. */
    void testInitMultiDisk() {
        InitParams params;
        testInitialization(params);
    }
    /** Test initializing with one of the disks being bad. */
    void testInitFailingMiddleDisk() {
        InitParams params;
        params.disksDown.insert(1);
        testInitialization(params);
    }
    /** Test initializing with last disk being bad. */
    void testInitFailingLastDisk() {
        InitParams params;
        params.disksDown.insert(params.diskCount - 1);
        testInitialization(params);
    }
    /** Test initializing with bucket on wrong disk. */
    void testInitBucketOnWrongDisk() {
        InitParams params;
        params.bucketWrongDisk = true;
        params.bucketBitsUsed = 58;
        testInitialization(params);
    }
    /** Test initializing with bucket on multiple disks. */
    void testInitBucketOnMultipleDisks() {
        InitParams params;
        params.bucketMultipleDisks = true;
        params.bucketBitsUsed = 58;
        testInitialization(params);
    }
    /** Test initializing with failing list request. */
    void testInitFailingListRequest() {
        InitParams params;
        params.failingListRequest = true;
        testInitialization(params);
    }
    void testInitFailingInfoRequest() {
        InitParams params;
        params.failingInfoRequest = true;
        testInitialization(params);
    }
    /** Test initializing with everything being wrong at once. */
    void testAllFailures() {
        InitParams params;
        params.docsPerDisk = 100;
        params.diskCount = DiskCount(10);
        params.disksDown.insert(0);
        params.disksDown.insert(2);
        params.disksDown.insert(3);
        params.disksDown.insert(9);
        params.setAllFailures();
        testInitialization(params);
    }
    void testCommandBlockingDuringInit();

    void testBucketProgressCalculator();

    void testBucketsInitializedByLoad();

    CPPUNIT_TEST_SUITE(InitializerTest);
    CPPUNIT_TEST(testInitEmptyNode);
    CPPUNIT_TEST(testInitSingleDisk);
    CPPUNIT_TEST(testInitMultiDisk);
    CPPUNIT_TEST(testInitFailingMiddleDisk);
    CPPUNIT_TEST(testInitFailingLastDisk);
    CPPUNIT_TEST(testInitBucketOnWrongDisk);
    //CPPUNIT_TEST(testInitBucketOnMultipleDisks);
    //CPPUNIT_TEST(testStatusPage);
    //CPPUNIT_TEST(testCommandBlockingDuringInit);
    //CPPUNIT_TEST(testAllFailures);
    CPPUNIT_TEST(testBucketProgressCalculator);
    CPPUNIT_TEST(testBucketsInitializedByLoad);
    CPPUNIT_TEST_SUITE_END();

};

CPPUNIT_TEST_SUITE_REGISTRATION(InitializerTest);

namespace {
// Data kept on buckets we're using in test.
struct BucketData {
    api::BucketInfo info;

    BucketData() : info(0, 0, 0, 0, 0) {
    }

    BucketData operator+(const BucketData& other) const {
        BucketData copy;
        copy.info.setDocumentCount(
                info.getDocumentCount() + other.info.getDocumentCount());
        copy.info.setTotalDocumentSize(
                info.getTotalDocumentSize()
                + other.info.getTotalDocumentSize());
        copy.info.setChecksum(
                info.getChecksum() * other.info.getChecksum());
        return copy;
    }
};
// Data reciding on one disk
typedef std::map<document::BucketId, BucketData> DiskData;
struct BucketInfoLogger {
    std::map<PartitionId, DiskData>& map;

    BucketInfoLogger(std::map<PartitionId, DiskData>& m)
        : map(m) {}

    StorBucketDatabase::Decision operator()(
            uint64_t revBucket, StorBucketDatabase::Entry& entry)
    {
        document::BucketId bucket(
                document::BucketId::keyToBucketId(revBucket));
        CPPUNIT_ASSERT(bucket.getRawId() != 0);
        CPPUNIT_ASSERT_MSG(
                "Found invalid bucket in database: " + bucket.toString()
                        + " " + entry.getBucketInfo().toString(),
                entry.getBucketInfo().valid());
        DiskData& ddata(map[entry.disk]);
        BucketData& bdata(ddata[bucket]);
        bdata.info = entry.getBucketInfo();
        return StorBucketDatabase::CONTINUE;
    }
};
std::map<PartitionId, DiskData>
createMapFromBucketDatabase(StorBucketDatabase& db) {
    std::map<PartitionId, DiskData> result;
    BucketInfoLogger infoLogger(result);
    db.all(infoLogger, "createmap");
    return result;
}
// Create data we want to have in this test
std::map<PartitionId, DiskData>
buildBucketInfo(const document::TestDocMan& docMan,
                InitializerTest::InitParams& params)
{
    std::map<PartitionId, DiskData> result;
    for (uint32_t i=0; i<params.diskCount; ++i) {
        if (params.disksDown.find(i) == params.disksDown.end()) {
            result[i];
        }
    }
    lib::Distribution distribution(
            lib::Distribution::getDefaultDistributionConfig(
                params.redundancy, params.nodeCount));
    document::BucketIdFactory bucketIdFactory;
    lib::NodeState nodeState;
    nodeState.setDiskCount(params.diskCount);

    uint64_t totalDocs = params.docsPerDisk * params.diskCount;
    for (uint32_t i=0, n=totalDocs; i<n; ++i) {
        bool useWrongDisk = false;
        if (i == 1 && params.bucketWrongDisk) {
            useWrongDisk = true;
        }
        document::Document::SP doc(docMan.createRandomDocument(i));
        if (i == 3 && params.bucketMultipleDisks) {
            doc = docMan.createRandomDocument(i - 1);
            useWrongDisk = true;
        }
        document::BucketId bid(bucketIdFactory.getBucketId(doc->getId()));
        bid.setUsedBits(params.bucketBitsUsed);
        bid = bid.stripUnused();
        uint32_t partition(distribution.getIdealDisk(
                    nodeState, params.nodeIndex, bid,
                    lib::Distribution::IDEAL_DISK_EVEN_IF_DOWN));
        if (params.disksDown.find(partition) != params.disksDown.end()) {
            continue;
        }
        if (useWrongDisk) {
            int correctPart = partition;
            partition = (partition + 1) % params.diskCount;;
            while (params.disksDown.find(partition) != params.disksDown.end()) {
                partition = (partition + 1) % params.diskCount;;
            }
            LOG(info, "Putting bucket %s on wrong disk %u instead of %u",
                bid.toString().c_str(), partition, correctPart);
        }
        LOG(info, "Putting bucket %s on disk %u",
            bid.toString().c_str(), partition);
        BucketData& data(result[partition][bid]);
        data.info.setDocumentCount(data.info.getDocumentCount() + 1);
        data.info.setTotalDocumentSize(
                data.info.getTotalDocumentSize() + 100);
        data.info.setChecksum(data.info.getChecksum() * 3);
    }
    return result;
}
void verifyEqual(std::map<PartitionId, DiskData>& org,
                 std::map<PartitionId, DiskData>& existing)
{
    uint32_t equalCount = 0;
    std::map<PartitionId, DiskData>::const_iterator part1(org.begin());
    std::map<PartitionId, DiskData>::const_iterator part2(existing.begin());
    while (part1 != org.end() && part2 != existing.end()) {
        if (part1->first < part2->first) {
            if (!part1->second.empty()) {
                std::ostringstream ost;
                ost << "No data in partition " << part1->first << " found.";
                CPPUNIT_FAIL(ost.str());
            }
            ++part1;
        } else if (part1->first > part2->first) {
            if (!part2->second.empty()) {
                std::ostringstream ost;
                ost << "Found data in partition " << part2->first
                    << " which should not exist.";
                CPPUNIT_FAIL(ost.str());
            }
            ++part2;
        } else {
            DiskData::const_iterator bucket1(part1->second.begin());
            DiskData::const_iterator bucket2(part2->second.begin());
            while (bucket1 != part1->second.end()
                   && bucket2 != part2->second.end())
            {
                if (bucket1->first < bucket2->first) {
                    std::ostringstream ost;
                    ost << "No data in partition " << part1->first
                        << " for bucket " << bucket1->first << " found.";
                    CPPUNIT_FAIL(ost.str());
                } else if (bucket1->first.getId() > bucket2->first.getId())
                {
                    std::ostringstream ost;
                    ost << "Found data in partition " << part2->first
                        << " for bucket " << bucket2->first
                        << " which should not exist.";
                    CPPUNIT_FAIL(ost.str());
                } else if (!(bucket1->second.info == bucket2->second.info)) {
                    std::ostringstream ost;
                    ost << "Bucket " << bucket1->first << " on partition "
                        << part1->first << " has bucket info "
                        << bucket2->second.info << " and not "
                        << bucket1->second.info << " as expected.";
                    CPPUNIT_FAIL(ost.str());
                }
                ++bucket1;
                ++bucket2;
                ++equalCount;
            }
            if (bucket1 != part1->second.end()) {
                std::ostringstream ost;
                ost << "No data in partition " << part1->first
                    << " for bucket " << bucket1->first << " found.";
                CPPUNIT_FAIL(ost.str());
            }
            if (bucket2 != part2->second.end()) {
                std::ostringstream ost;
                ost << "Found data in partition " << part2->first
                    << " for bucket " << bucket2->first
                    << " which should not exist.";
                CPPUNIT_FAIL(ost.str());
            }
            ++part1;
            ++part2;
        }
    }
    if (part1 != org.end() && !part1->second.empty()) {
        std::ostringstream ost;
        ost << "No data in partition " << part1->first << " found.";
        CPPUNIT_FAIL(ost.str());
    }
    if (part2 != existing.end() && !part2->second.empty()) {
        std::ostringstream ost;
        ost << "Found data in partition " << part2->first
            << " which should not exist.";
        CPPUNIT_FAIL(ost.str());
    }
    //std::cerr << "\n  " << equalCount << " buckets were matched.  ";
}

struct MessageCallback
{
public:
    virtual ~MessageCallback() {}
    virtual void onMessage(const api::StorageMessage&) = 0;
};

struct FakePersistenceLayer : public StorageLink {
    StorBucketDatabase& bucketDatabase;
    std::map<PartitionId, DiskData>& data;
    std::string firstFatal;
    std::string fatalError;
    MessageCallback* messageCallback;

    FakePersistenceLayer(std::map<PartitionId, DiskData>& d,
                         StorBucketDatabase& db)
        : StorageLink("fakepersistencelayer"),
          bucketDatabase(db),
          data(d),
          messageCallback(0)
    {
    }

    void fatal(vespalib::stringref error) {
        fatalError = error;
        if (firstFatal.empty()) firstFatal = fatalError;
    }
    const BucketData* getBucketData(PartitionId partition,
                                    const document::BucketId& bucket,
                                    vespalib::stringref opname)
    {
        std::map<PartitionId, DiskData>::const_iterator it(
                data.find(partition));
        if (it == data.end()) {
            std::ostringstream ost;
            ost << bucket << " is stated to be on partition " << partition
                << " in operation " << opname << ", but we have no data for "
                << "it there.";
            fatal(ost.str());
        } else {
            DiskData::const_iterator it2(it->second.find(bucket));
            if (it2 == it->second.end()) {
                std::ostringstream ost;
                ost << "Have no data for " << bucket << " on disk " << partition
                    << " in operation " << opname;
                fatal(ost.str());
            } else {
                const BucketData& bucketData(it2->second);
                return &bucketData;
            }
        }
        return 0;
    }

    bool onDown(const api::StorageMessage::SP& msg) override {
        fatalError = "";
        if (messageCallback) {
            messageCallback->onMessage(*msg);
        }
        if (msg->getType() == api::MessageType::INTERNAL) {
            api::InternalCommand& cmd(
                    dynamic_cast<api::InternalCommand&>(*msg));
            if (cmd.getType() == ReadBucketList::ID) {
                ReadBucketList& rbl(dynamic_cast<ReadBucketList&>(cmd));
                ReadBucketListReply::SP reply(new ReadBucketListReply(rbl));
                std::map<PartitionId, DiskData>::const_iterator it(
                        data.find(rbl.getPartition()));
                if (it == data.end()) {
                    std::ostringstream ost;
                    ost << "Got list request to partition "
                        << rbl.getPartition()
                        << " for which we should not get a request";
                    fatal(ost.str());
                } else {
                    if (cmd.getBucket().getBucketSpace() == FixedBucketSpaces::default_space()) {
                        for (DiskData::const_iterator it2 = it->second.begin();
                             it2 != it->second.end(); ++it2)
                        {
                            reply->getBuckets().push_back(it2->first);
                        }
                    }
                }
                if (!fatalError.empty()) {
                    reply->setResult(api::ReturnCode(
                            api::ReturnCode::INTERNAL_FAILURE, fatalError));
                }
                sendUp(reply);
            } else if (cmd.getType() == ReadBucketInfo::ID) {
                ReadBucketInfo& rbi(dynamic_cast<ReadBucketInfo&>(cmd));
                ReadBucketInfoReply::SP reply(new ReadBucketInfoReply(rbi));
                StorBucketDatabase::WrappedEntry entry(
                        bucketDatabase.get(rbi.getBucketId(), "fakelayer"));
                if (!entry.exist()) {
                    fatal("Bucket " + rbi.getBucketId().toString()
                          + " did not exist in bucket database but we got "
                          + "read bucket info request for it.");
                } else {
                    const BucketData* bucketData(getBucketData(
                            entry->disk, rbi.getBucketId(), "readbucketinfo"));
                    if (bucketData != 0) {
                        entry->setBucketInfo(bucketData->info);
                        entry.write();
                    }
                }
                if (!fatalError.empty()) {
                    reply->setResult(api::ReturnCode(
                            api::ReturnCode::INTERNAL_FAILURE, fatalError));
                }
                sendUp(reply);
            } else if (cmd.getType() == InternalBucketJoinCommand::ID) {
                InternalBucketJoinCommand& ibj(
                        dynamic_cast<InternalBucketJoinCommand&>(cmd));
                InternalBucketJoinReply::SP reply(
                        new InternalBucketJoinReply(ibj));
                StorBucketDatabase::WrappedEntry entry(
                        bucketDatabase.get(ibj.getBucketId(), "fakelayer"));
                if (!entry.exist()) {
                    fatal("Bucket " + ibj.getBucketId().toString()
                          + " did not exist in bucket database but we got "
                          + "read bucket info request for it.");
                } else {
                    const BucketData* source(getBucketData(
                            ibj.getDiskOfInstanceToJoin(), ibj.getBucketId(),
                            "internaljoinsource"));
                    const BucketData* target(getBucketData(
                            ibj.getDiskOfInstanceToKeep(), ibj.getBucketId(),
                            "internaljointarget"));
                    if (source != 0 && target != 0) {
                        entry->setBucketInfo((*source + *target).info);
                        entry.write();
                    }
                }
                if (!fatalError.empty()) {
                    reply->setResult(api::ReturnCode(
                            api::ReturnCode::INTERNAL_FAILURE, fatalError));
                }
                sendUp(reply);
            } else {
                return false;
            }
            return true;
        }
        return false;
    }
};

} // end of anonymous namespace

#define CPPUNIT_ASSERT_METRIC_SET(x) \
    CPPUNIT_ASSERT(initializer->getMetrics().x.getValue() > 0);

void
InitializerTest::testInitialization(InitParams& params)
{
    std::map<PartitionId, DiskData> data(buildBucketInfo(_docMan, params));

    spi::PartitionStateList partitions(params.diskCount);
    for (std::set<uint32_t>::const_iterator it = params.disksDown.begin();
         it != params.disksDown.end(); ++it)
    {
        partitions[*it] = spi::PartitionState(
                spi::PartitionState::DOWN, "Set down in test");
    }
    TestServiceLayerApp node(params.diskCount, params.nodeIndex,
                             params.getConfig().getConfigId());
    DummyStorageLink top;
    StorageBucketDBInitializer* initializer;
    FakePersistenceLayer* bottom;
    top.push_back(StorageLink::UP(initializer = new StorageBucketDBInitializer(
            params.getConfig().getConfigId(),
            partitions,
            node.getDoneInitializeHandler(),
            node.getComponentRegister())));
    top.push_back(StorageLink::UP(bottom = new FakePersistenceLayer(
            data, node.getStorageBucketDatabase())));

    LOG(info, "STARTING INITIALIZATION");
    top.open();

    /*
    FileChanger updater(config, nodeIndex, params, orgBucketDatabase);
    if (params.bucketWrongDisk) updater.moveBucketWrongDisk();
    if (params.bucketMultipleDisks) updater.copyBucketWrongDisk();
    if (params.failingListRequest) {
        updater.removeDirPermission(6, 'r');
        updater.removeBucketsFromDBAtPath(6);
    }
    if (params.failingInfoRequest) {
        updater.removeFilePermission();
        orgBucketDatabase.erase(updater.getBucket(8));
    }
    */

    node.waitUntilInitialized(initializer);

    std::map<PartitionId, DiskData> initedBucketDatabase(
            createMapFromBucketDatabase(node.getStorageBucketDatabase()));
    verifyEqual(data, initedBucketDatabase);
    /*
    if (params.bucketWrongDisk) {
        CPPUNIT_ASSERT_METRIC_SET(_wrongDisk);
    }
    if (params.bucketMultipleDisks) {
        CPPUNIT_ASSERT_METRIC_SET(_joinedCount);
    }
    */
}

/*
namespace {
    enum State { LISTING, INFO, DONE };
    void verifyStatusContent(StorageBucketDBInitializer& initializer,
                             State state)
    {
        std::ostringstream ost;
        initializer.reportStatus(ost, framework::HttpUrlPath(""));
        std::string status = ost.str();

        if (state == LISTING) {
            CPPUNIT_ASSERT_CONTAIN("List phase completed: false", status);
            CPPUNIT_ASSERT_CONTAIN("Initialization completed: false", status);
        } else if (state == INFO) {
            CPPUNIT_ASSERT_CONTAIN("List phase completed: true", status);
            CPPUNIT_ASSERT_CONTAIN("Initialization completed: false", status);
        } else if (state == DONE) {
            CPPUNIT_ASSERT_CONTAIN("List phase completed: true", status);
            CPPUNIT_ASSERT_CONTAIN("Initialization completed: true", status);
        }
    }
}

void
InitializerTest::testStatusPage()
{
        // Set up surrounding system to create a single bucket for us to
        // do init on.
    vdstestlib::DirConfig config(getStandardConfig(true));
    uint16_t nodeIndex(
            config.getConfig("stor-server").getValue("node_index", 0));
    InitParams params;
    params.docsPerDisk = 1;
    params.diskCount = 1;
    std::map<document::BucketId, api::BucketInfo> orgBucketDatabase(
            buildBucketInfo(_docMan, config, nodeIndex, 1, 1, params.disksDown));
    FileChanger updater(config, nodeIndex, params, orgBucketDatabase);

        // Set up the initializer.
    DummyStorageServer server(config.getConfigId());
    DummyStorageLink top;
    DummyStorageLink *bottom;
    StorageBucketDBInitializer* initializer;
    top.push_back(StorageLink::UP(initializer = new StorageBucketDBInitializer(
            config.getConfigId(), server)));
    top.push_back(StorageLink::UP(bottom = new DummyStorageLink));

        // Grab bucket database lock for bucket to init to lock the initializer
        // in the init stage
    StorBucketDatabase::WrappedEntry entry(
            server.getStorageBucketDatabase().get(
                    updater.getBucket(0), "testCommandBlocking",
                    StorBucketDatabase::LOCK_IF_NONEXISTING_AND_NOT_CREATING));
        // Start the initializer
    top.open();
    bottom->waitForMessages(1, 30);
    verifyStatusContent(*initializer, LISTING);
        // Attempt to send put. Should be blocked
        // Attempt to send request bucket info. Should be blocked.
        // Attempt to send getNodeState. Should not be blocked.

        // Unlock bucket in bucket database so listing step can complete.
        // Await read info request being sent down.
    entry.unlock();
    bottom->waitForMessages(1, 30);
    verifyStatusContent(*initializer, INFO);

    ReadBucketInfo& cmd(dynamic_cast<ReadBucketInfo&>(*bottom->getCommand(0)));
    ReadBucketInfoReply::SP reply(new ReadBucketInfoReply(cmd));
    bottom->sendUp(reply);

    node.waitUntilInitialized(initializer);
    verifyStatusContent(*initializer, DONE);

}

#define ASSERT_BLOCKED(top, bottom, blocks) \
    if (blocks) { \
        top.waitForMessages(1, 30); \
        CPPUNIT_ASSERT_EQUAL(size_t(1), top.getReplies().size()); \
        CPPUNIT_ASSERT_EQUAL(size_t(0), bottom.getCommands().size()); \
        api::StorageReply& reply(dynamic_cast<api::StorageReply&>( \
                    *top.getReply(0))); \
        CPPUNIT_ASSERT_EQUAL(api::ReturnCode::ABORTED, \
                             reply.getResult().getResult()); \
        top.reset(); \
    } else { \
        bottom.waitForMessages(1, 30); \
        CPPUNIT_ASSERT_EQUAL(size_t(0), top.getReplies().size()); \
        CPPUNIT_ASSERT_EQUAL(size_t(1), bottom.getCommands().size()); \
        api::StorageCommand& command(dynamic_cast<api::StorageCommand&>( \
                    *bottom.getCommand(0))); \
        (void) command; \
        bottom.reset(); \
    }

namespace {
    void verifyBlockingOn(DummyStorageLink& top,
                          DummyStorageLink& bottom,
                          bool blockEnabled)
    {
        // Attempt to send get. Should be blocked if block enabled
        {
            api::GetCommand::SP cmd(new api::GetCommand(
                        document::BucketId(16, 4),
                        document::DocumentId("userdoc:ns:4:test"), true));
            top.sendDown(cmd);
            ASSERT_BLOCKED(top, bottom, blockEnabled);
        }
        // Attempt to send request bucket info. Should be blocked if enabled.
        {
            api::RequestBucketInfoCommand::SP cmd(
                    new api::RequestBucketInfoCommand(
                        0, lib::ClusterState("")));
            top.sendDown(cmd);
            ASSERT_BLOCKED(top, bottom, blockEnabled);
        }
        // Attempt to send getNodeState. Should not be blocked.
        {
            api::GetNodeStateCommand::SP cmd(new api::GetNodeStateCommand(
                    lib::NodeState::UP(0)));
            top.sendDown(cmd);
            ASSERT_BLOCKED(top, bottom, false);
        }
    }
}

void
InitializerTest::testCommandBlockingDuringInit()
{
        // Set up surrounding system to create a single bucket for us to
        // do init on.
    vdstestlib::DirConfig config(getStandardConfig(true));
    uint16_t nodeIndex(
            config.getConfig("stor-server").getValue("node_index", 0));
    InitParams params;
    params.docsPerDisk = 1;
    params.diskCount = 1;
    std::map<document::BucketId, api::BucketInfo> orgBucketDatabase(
            buildBucketInfo(_docMan, config, nodeIndex, 1, 1, params.disksDown));
    FileChanger updater(config, nodeIndex, params, orgBucketDatabase);

        // Set up the initializer.
    DummyStorageServer server(config.getConfigId());
    DummyStorageLink top;
    DummyStorageLink *bottom;
    StorageBucketDBInitializer* initializer;
    top.push_back(StorageLink::UP(initializer = new StorageBucketDBInitializer(
            config.getConfigId(), server)));
    top.push_back(StorageLink::UP(bottom = new DummyStorageLink));

        // Grab bucket database lock for bucket to init to lock the initializer
        // in the init stage
    StorBucketDatabase::WrappedEntry entry(
            server.getStorageBucketDatabase().get(
                    updater.getBucket(0), "testCommandBlocking",
                    StorBucketDatabase::LOCK_IF_NONEXISTING_AND_NOT_CREATING));
        // Start the initializer
    top.open();
    verifyBlockingOn(top, *bottom, true);
        // Attempt to send put. Should be blocked
        // Attempt to send request bucket info. Should be blocked.
        // Attempt to send getNodeState. Should not be blocked.

        // Unlock bucket in bucket database so listing step can complete.
        // Await read info request being sent down.
    entry.unlock();
    bottom->waitForMessages(1, 30);
    dynamic_cast<ReadBucketInfo&>(*bottom->getCommand(0));
    CPPUNIT_ASSERT(!server.isInitialized());
    bottom->reset();

        // Retry - Should now not block
    verifyBlockingOn(top, *bottom, false);
}
*/

void
InitializerTest::testBucketProgressCalculator()
{
    using document::BucketId;
    StorageBucketDBInitializer::BucketProgressCalculator calc;
    // We consider the given bucket as not being completed, so progress
    // will be _up to_, not _including_ the bucket. This means we can never
    // reach 1.0, so progress completion must be handled by other logic!
    CPPUNIT_ASSERT_EQUAL(0.0, calc.calculateProgress(BucketId(1, 0)));
    CPPUNIT_ASSERT_EQUAL(0.0, calc.calculateProgress(BucketId(32, 0)));

    CPPUNIT_ASSERT_EQUAL(0.5, calc.calculateProgress(BucketId(1, 1)));

    CPPUNIT_ASSERT_EQUAL(0.25, calc.calculateProgress(BucketId(2, 2)));
    CPPUNIT_ASSERT_EQUAL(0.5, calc.calculateProgress(BucketId(2, 1)));
    CPPUNIT_ASSERT_EQUAL(0.75, calc.calculateProgress(BucketId(2, 3)));

    CPPUNIT_ASSERT_EQUAL(0.875, calc.calculateProgress(BucketId(3, 7)));
}

struct DatabaseInsertCallback : MessageCallback
{
    DiskData& _data;
    StorBucketDatabase& _database;
    TestServiceLayerApp& _app;
    const InitializerTest::InitParams& _params;
    bool _invoked;
    double _lastSeenProgress;
    uint8_t _expectedReadBucketPriority;
    std::ostringstream _errors;
    DatabaseInsertCallback(DiskData& data,
                           StorBucketDatabase& db,
                           TestServiceLayerApp& app,
                           const InitializerTest::InitParams& params)
        : _data(data),
          _database(db),
          _app(app),
          _params(params),
          _invoked(false),
          _lastSeenProgress(0),
          _expectedReadBucketPriority(255)
    {}

    void onMessage(const api::StorageMessage& msg) override
    {
        // Always make sure we're not set as initialized while we're still
        // processing messages! Also ensure progress never goes down.
        lib::NodeState::CSP reportedState(
                _app.getStateUpdater().getReportedNodeState());
        double progress(reportedState->getInitProgress().getValue());
        LOG(debug, "reported progress is now %g", progress);
        // CppUnit exceptions are swallowed...
        if (progress >= 1.0) {
            _errors << "progress exceeded 1.0: " << progress << "\n";
        }
        if (progress < _lastSeenProgress) {
            _errors << "progress went down! "
                    << _lastSeenProgress << " -> " << progress
                    << "\n";
        }
        // 16 bits is allowed before we have listed any buckets at all
        // since we at that point have no idea and have not reported anything
        // back to the fleetcontroller.
        if (_params.bucketBitsUsed != reportedState->getMinUsedBits()
            && !(reportedState->getMinUsedBits() == 16 && !_invoked))
        {
            _errors << "reported state contains wrong min used bits. "
                    << "expected " << _params.bucketBitsUsed
                    << ", but got " << reportedState->getMinUsedBits()
                    << "\n";
        }
        _lastSeenProgress = progress;
        if (_invoked) {
            return;
        }

        if (msg.getType() == api::MessageType::INTERNAL) {
            const api::InternalCommand& cmd(
                    dynamic_cast<const api::InternalCommand&>(msg));
            if (cmd.getType() == ReadBucketInfo::ID) {
                if (cmd.getPriority() != _expectedReadBucketPriority) {
                    _errors << "expected ReadBucketInfo priority of "
                            << static_cast<int>(_expectedReadBucketPriority)
                            << ", was " << static_cast<int>(cmd.getPriority());
                }
                // As soon as we get the first ReadBucketInfo, we insert new buckets
                // into the the bucket database in order to simulate external
                // load init. Kinda hacky, but should work as long as initializer
                // always does at least 1 extra iteration pass (which we use
                // config overrides to ensure happens).
                _invoked = true;
                for (int i = 0; i < 4; ++i) {
                    document::BucketId bid(16 + i, 8); // not the first, nor the last bucket
                    BucketData d;
                    StorBucketDatabase::WrappedEntry entry(
                            _database.get(bid, "DatabaseInsertCallback::onMessage",
                                    StorBucketDatabase::LOCK_IF_NONEXISTING_AND_NOT_CREATING));
                    if (entry.exist()) {
                        _errors << "db entry for " << bid << " already existed";
                    }
                    if (i < 5) {
                        d.info = api::BucketInfo(3+i, 4+i, 5+i, 6+i, 7+i);
                    }
                    _data[bid] = d;
                    entry->disk = 0;
                    entry->setBucketInfo(d.info);
                    entry.write();
                }
            }
        }        
    }
};

void
InitializerTest::testBucketsInitializedByLoad()
{
    InitParams params;
    params.docsPerDisk = 100;
    params.diskCount = DiskCount(1);
    params.getConfig().getConfig("stor-bucket-init").setValue("max_pending_info_reads_per_disk", 1);
    params.getConfig().getConfig("stor-bucket-init").setValue("min_pending_info_reads_per_disk", 1);
    params.getConfig().getConfig("stor-bucket-init")
            .setValue("info_read_priority", 231);

    std::map<PartitionId, DiskData> data(buildBucketInfo(_docMan, params));

    spi::PartitionStateList partitions(params.diskCount);
    TestServiceLayerApp node(params.diskCount, params.nodeIndex,
                             params.getConfig().getConfigId());
    DummyStorageLink top;
    StorageBucketDBInitializer* initializer;
    FakePersistenceLayer* bottom;
    top.push_back(StorageLink::UP(initializer = new StorageBucketDBInitializer(
            params.getConfig().getConfigId(),
            partitions,
            node.getDoneInitializeHandler(),
            node.getComponentRegister())));
    top.push_back(StorageLink::UP(bottom = new FakePersistenceLayer(
            data, node.getStorageBucketDatabase())));

    DatabaseInsertCallback callback(data[0], node.getStorageBucketDatabase(),
                                    node, params);
    callback._expectedReadBucketPriority = 231;

    bottom->messageCallback = &callback;

    top.open();

    node.waitUntilInitialized(initializer);
    // Must explicitly wait until initializer has closed to ensure node state
    // has been set.
    top.close();

    CPPUNIT_ASSERT(callback._invoked);
    CPPUNIT_ASSERT_EQUAL(std::string(), callback._errors.str());

    std::map<PartitionId, DiskData> initedBucketDatabase(
            createMapFromBucketDatabase(node.getStorageBucketDatabase()));
    verifyEqual(data, initedBucketDatabase);

    lib::NodeState::CSP reportedState(
            node.getStateUpdater().getReportedNodeState());

    double progress(reportedState->getInitProgress().getValue());
    CPPUNIT_ASSERT(progress >= 1.0);
    CPPUNIT_ASSERT(progress < 1.0001);

    CPPUNIT_ASSERT_EQUAL(params.bucketBitsUsed,
                         reportedState->getMinUsedBits());
}

} // storage
