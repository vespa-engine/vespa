// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
/**
 * Tests storage initialization without depending on persistence layer.
 */
#include <vespa/document/base/testdocman.h>
#include <vespa/document/bucket/fixed_bucket_spaces.h>
#include <vespa/storage/bucketdb/lockablemap.hpp>
#include <vespa/storage/bucketdb/storagebucketdbinitializer.h>
#include <vespa/storage/persistence/filestorage/filestormanager.h>
#include <vespa/storageapi/message/bucket.h>
#include <vespa/storageapi/message/persistence.h>
#include <vespa/storageapi/message/state.h>
#include <tests/common/teststorageapp.h>
#include <tests/common/dummystoragelink.h>
#include <tests/common/testhelper.h>
#include <vespa/vdstestlib/config/dirconfig.hpp>
#include <vespa/vespalib/gtest/gtest.h>

#include <vespa/log/log.h>
LOG_SETUP(".test.bucketdb.initializing");

using document::FixedBucketSpaces;
using namespace ::testing;

namespace storage {

typedef uint16_t PartitionId;

struct InitializerTest : public Test {

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
              diskCount(1),
              bucketWrongDisk(false),
              bucketMultipleDisks(false),
              failingListRequest(false),
              failingInfoRequest(false)
        {}

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

    void do_test_initialization(InitParams& params);
};

TEST_F(InitializerTest, init_with_empty_node) {
    InitParams params;
    params.docsPerDisk = 0;
    do_test_initialization(params);
}

TEST_F(InitializerTest, init_with_data_on_single_disk) {
    InitParams params;
    params.diskCount = DiskCount(1);
    do_test_initialization(params);
}

TEST_F(InitializerTest, init_with_multiple_disks) {
    InitParams params;
    do_test_initialization(params);
}

TEST_F(InitializerTest, init_with_bucket_on_wrong_disk) {
    InitParams params;
    params.bucketWrongDisk = true;
    params.bucketBitsUsed = 58;
    do_test_initialization(params);
}

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
// Data residing on one disk
typedef std::map<document::BucketId, BucketData> DiskData;
struct BucketInfoLogger {
    std::map<PartitionId, DiskData>& map;

    explicit BucketInfoLogger(std::map<PartitionId, DiskData>& m)
        : map(m) {}

    StorBucketDatabase::Decision operator()(
            uint64_t revBucket, const StorBucketDatabase::Entry& entry)
    {
        document::BucketId bucket(document::BucketId::keyToBucketId(revBucket));
        assert(bucket.getRawId() != 0);
        assert(entry.getBucketInfo().valid());
        DiskData& ddata(map[0]);
        BucketData& bdata(ddata[bucket]);
        bdata.info = entry.getBucketInfo();
        return StorBucketDatabase::Decision::CONTINUE;
    }
};
std::map<PartitionId, DiskData>
createMapFromBucketDatabase(StorBucketDatabase& db) {
    std::map<PartitionId, DiskData> result;
    BucketInfoLogger infoLogger(result);
    db.for_each(std::ref(infoLogger), "createmap");
    return result;
}
// Create data we want to have in this test
std::map<PartitionId, DiskData>
buildBucketInfo(const document::TestDocMan& docMan,
                InitializerTest::InitParams& params)
{
    std::map<PartitionId, DiskData> result;
    for (uint32_t i=0; i<params.diskCount; ++i) {
        result[i];
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
        if (useWrongDisk) {
            int correctPart = partition;
            partition = (partition + 1) % params.diskCount;;
            LOG(debug, "Putting bucket %s on wrong disk %u instead of %u",
                bid.toString().c_str(), partition, correctPart);
        }
        LOG(debug, "Putting bucket %s on disk %u",
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
                FAIL() << "No data in partition " << part1->first << " found.";
            }
            ++part1;
        } else if (part1->first > part2->first) {
            if (!part2->second.empty()) {
                FAIL() << "Found data in partition " << part2->first
                       << " which should not exist.";
            }
            ++part2;
        } else {
            auto bucket1 = part1->second.begin();
            auto bucket2 = part2->second.begin();
            while (bucket1 != part1->second.end()
                   && bucket2 != part2->second.end())
            {
                if (bucket1->first < bucket2->first) {
                    FAIL() << "No data in partition " << part1->first
                           << " for bucket " << bucket1->first << " found.";
                } else if (bucket1->first.getId() > bucket2->first.getId())
                {
                    FAIL() << "Found data in partition " << part2->first
                           << " for bucket " << bucket2->first
                           << " which should not exist.";
                } else if (!(bucket1->second.info == bucket2->second.info)) {
                    FAIL() << "Bucket " << bucket1->first << " on partition "
                           << part1->first << " has bucket info "
                           << bucket2->second.info << " and not "
                           << bucket1->second.info << " as expected.";
                }
                ++bucket1;
                ++bucket2;
                ++equalCount;
            }
            if (bucket1 != part1->second.end()) {
                FAIL() << "No data in partition " << part1->first
                       << " for bucket " << bucket1->first << " found.";
            }
            if (bucket2 != part2->second.end()) {
                FAIL() << "Found data in partition " << part2->first
                       << " for bucket " << bucket2->first
                       << " which should not exist.";
            }
            ++part1;
            ++part2;
        }
    }
    if (part1 != org.end() && !part1->second.empty()) {
        FAIL() << "No data in partition " << part1->first << " found.";
    }
    if (part2 != existing.end() && !part2->second.empty()) {
        FAIL() << "Found data in partition " << part2->first
               << " which should not exist.";
    }
}

struct MessageCallback
{
public:
    virtual ~MessageCallback() = default;
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
            auto it2 = it->second.find(bucket);
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
            auto& cmd = dynamic_cast<api::InternalCommand&>(*msg);
            if (cmd.getType() == ReadBucketList::ID) {
                auto& rbl = dynamic_cast<ReadBucketList&>(cmd);
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
                        for (const auto& bd : it->second) {
                            reply->getBuckets().push_back(bd.first);
                        }
                    }
                }
                if (!fatalError.empty()) {
                    reply->setResult(api::ReturnCode(
                            api::ReturnCode::INTERNAL_FAILURE, fatalError));
                }
                sendUp(reply);
            } else if (cmd.getType() == ReadBucketInfo::ID) {
                auto& rbi = dynamic_cast<ReadBucketInfo&>(cmd);
                ReadBucketInfoReply::SP reply(new ReadBucketInfoReply(rbi));
                StorBucketDatabase::WrappedEntry entry(
                        bucketDatabase.get(rbi.getBucketId(), "fakelayer"));
                if (!entry.exist()) {
                    fatal("Bucket " + rbi.getBucketId().toString()
                          + " did not exist in bucket database but we got "
                          + "read bucket info request for it.");
                } else {
                    const BucketData* bucketData(getBucketData(0, rbi.getBucketId(), "readbucketinfo"));
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
                auto& ibj = dynamic_cast<InternalBucketJoinCommand&>(cmd);
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

void
InitializerTest::do_test_initialization(InitParams& params)
{
    std::map<PartitionId, DiskData> data(buildBucketInfo(_docMan, params));

    assert(params.diskCount == 1u);
    TestServiceLayerApp node(params.nodeIndex, params.getConfig().getConfigId());
    DummyStorageLink top;
    StorageBucketDBInitializer* initializer;
    FakePersistenceLayer* bottom;
    top.push_back(StorageLink::UP(initializer = new StorageBucketDBInitializer(
            params.getConfig().getConfigId(),
            node.getDoneInitializeHandler(),
            node.getComponentRegister())));
    top.push_back(StorageLink::UP(bottom = new FakePersistenceLayer(
            data, node.getStorageBucketDatabase())));

    LOG(debug, "STARTING INITIALIZATION");
    top.open();

    node.waitUntilInitialized(initializer);

    std::map<PartitionId, DiskData> initedBucketDatabase(
            createMapFromBucketDatabase(node.getStorageBucketDatabase()));
    verifyEqual(data, initedBucketDatabase);
}

TEST_F(InitializerTest, bucket_progress_calculator) {
    using document::BucketId;
    StorageBucketDBInitializer::BucketProgressCalculator calc;
    // We consider the given bucket as not being completed, so progress
    // will be _up to_, not _including_ the bucket. This means we can never
    // reach 1.0, so progress completion must be handled by other logic!
    EXPECT_DOUBLE_EQ(0.0, calc.calculateProgress(BucketId(1, 0)));
    EXPECT_DOUBLE_EQ(0.0, calc.calculateProgress(BucketId(32, 0)));

    EXPECT_DOUBLE_EQ(0.5, calc.calculateProgress(BucketId(1, 1)));

    EXPECT_DOUBLE_EQ(0.25, calc.calculateProgress(BucketId(2, 2)));
    EXPECT_DOUBLE_EQ(0.5, calc.calculateProgress(BucketId(2, 1)));
    EXPECT_DOUBLE_EQ(0.75, calc.calculateProgress(BucketId(2, 3)));

    EXPECT_DOUBLE_EQ(0.875, calc.calculateProgress(BucketId(3, 7)));
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
            auto& cmd = dynamic_cast<const api::InternalCommand&>(msg);
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
                                    StorBucketDatabase::CREATE_IF_NONEXISTING));
                    if (entry.preExisted()) {
                        _errors << "db entry for " << bid << " already existed";
                    }
                    if (i < 5) {
                        d.info = api::BucketInfo(3+i, 4+i, 5+i, 6+i, 7+i);
                    }
                    _data[bid] = d;
                    entry->setBucketInfo(d.info);
                    entry.write();
                }
            }
        }        
    }
};

TEST_F(InitializerTest, buckets_initialized_by_load) {
    InitParams params;
    params.docsPerDisk = 100;
    params.diskCount = DiskCount(1);
    params.getConfig().getConfig("stor-bucket-init").setValue("max_pending_info_reads_per_disk", 1);
    params.getConfig().getConfig("stor-bucket-init").setValue("min_pending_info_reads_per_disk", 1);
    params.getConfig().getConfig("stor-bucket-init")
            .setValue("info_read_priority", 231);

    std::map<PartitionId, DiskData> data(buildBucketInfo(_docMan, params));

    assert(params.diskCount == 1u);
    TestServiceLayerApp node(params.nodeIndex,
                             params.getConfig().getConfigId());
    DummyStorageLink top;
    StorageBucketDBInitializer* initializer;
    FakePersistenceLayer* bottom;
    top.push_back(StorageLink::UP(initializer = new StorageBucketDBInitializer(
            params.getConfig().getConfigId(),
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

    ASSERT_TRUE(callback._invoked);
    EXPECT_EQ(std::string(), callback._errors.str());

    std::map<PartitionId, DiskData> initedBucketDatabase(
            createMapFromBucketDatabase(node.getStorageBucketDatabase()));
    verifyEqual(data, initedBucketDatabase);

    lib::NodeState::CSP reportedState(
            node.getStateUpdater().getReportedNodeState());

    double progress(reportedState->getInitProgress().getValue());
    EXPECT_GE(progress, 1.0);
    EXPECT_LT(progress, 1.0001);

    EXPECT_EQ(params.bucketBitsUsed, reportedState->getMinUsedBits());
}

} // storage
