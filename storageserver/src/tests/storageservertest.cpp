// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/storage/storageserver/servicelayernode.h>
#include <vespa/storage/storageserver/distributornode.h>

#include <vespa/document/base/testdocman.h>
#include <vespa/document/test/make_document_bucket.h>
#include <vespa/documentapi/documentapi.h>
#include <vespa/messagebus/rpcmessagebus.h>
#include <vespa/messagebus/network/rpcnetworkparams.h>
#include <vespa/messagebus/staticthrottlepolicy.h>
#include <vespa/messagebus/testlib/slobrok.h>
#include <vespa/storageapi/mbusprot/storagecommand.h>
#include <vespa/storageapi/mbusprot/storagereply.h>
#include <vespa/storageapi/message/bucketsplitting.h>
#include <vespa/storageapi/message/state.h>
#include <vespa/storage/common/statusmetricconsumer.h>
#include <tests/testhelper.h>
#include <tests/dummystoragelink.h>
#include <vespa/slobrok/sbmirror.h>
#include <vespa/storageserver/app/distributorprocess.h>
#include <vespa/storageserver/app/dummyservicelayerprocess.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/fnet/frt/supervisor.h>
#include <sys/time.h>

#include <vespa/log/log.h>

LOG_SETUP(".storageservertest");

using document::test::makeDocumentBucket;

namespace storage {

namespace {
    uint64_t getTimeInMillis() {
        struct timeval t;
        gettimeofday(&t, 0);
        return (t.tv_sec * uint64_t(1000)) + (t.tv_usec / uint64_t(1000));
    }

    class SlobrokMirror {
        config::ConfigUri config;
        FRT_Supervisor visor;
        std::unique_ptr<slobrok::api::MirrorAPI> mirror;

    public:
        SlobrokMirror(const config::ConfigUri & cfg) : config(cfg) {}

        void init(uint32_t timeoutms) {
            uint64_t timeout = getTimeInMillis() + timeoutms;
            visor.Start();
            mirror.reset(new slobrok::api::MirrorAPI(visor, config));
            while (!mirror->ready()) {
                if (getTimeInMillis() > timeout)
                    throw vespalib::IllegalStateException(
                            "Failed to initialize slobrok mirror within "
                            "timeout.", VESPA_STRLOC);
                FastOS_Thread::Sleep(1);
            }
        }

        slobrok::api::MirrorAPI& getMirror() {
            if (mirror.get() == 0) throw vespalib::IllegalStateException(
                    "You need to call init() before you can fetch mirror");
            return *mirror;
        }
        FRT_Supervisor& getSupervisor() {
            if (mirror.get() == 0) throw vespalib::IllegalStateException(
                    "You need to call init() before you can fetch supervisor");
            return visor;
        }

        ~SlobrokMirror() {
            if (mirror) {
                mirror.reset();
                visor.ShutDown(true);
            }
        }
    };
}

struct StorageServerTest : public CppUnit::TestFixture {
    std::unique_ptr<FastOS_ThreadPool> threadPool;
    std::unique_ptr<document::TestDocMan> docMan;
    std::unique_ptr<mbus::Slobrok> slobrok;
    std::unique_ptr<vdstestlib::DirConfig> distConfig;
    std::unique_ptr<vdstestlib::DirConfig> storConfig;
    std::unique_ptr<SlobrokMirror> slobrokMirror;

    StorageServerTest();
    ~StorageServerTest();

    void setUp() override;
    void tearDown() override;

    void testNormalUsage();
    void testPortOverlap_Stress();
    void testShutdownDuringDiskLoad(bool storagenode);
    void testShutdownStorageDuringDiskLoad();
    void testShutdownDistributorDuringDiskLoad();
    void testShutdownAfterDiskFailure_Stress();
    void testPriorityAndQueueSneakingWhileSplitJoinStressTest();
    void testStatusPages();

    CPPUNIT_TEST_SUITE(StorageServerTest);
    CPPUNIT_TEST(testNormalUsage);
    CPPUNIT_TEST_IGNORED(testPortOverlap_Stress);
    CPPUNIT_TEST_IGNORED(testShutdownStorageDuringDiskLoad);
    CPPUNIT_TEST_IGNORED(testShutdownDistributorDuringDiskLoad);
    CPPUNIT_TEST_IGNORED(testShutdownAfterDiskFailure_Stress);

    CPPUNIT_TEST_DISABLED(testPriorityAndQueueSneakingWhileSplitJoinStressTest);

    // Doesn't work in new framework. Will investigate as soon as there's time
    CPPUNIT_TEST_DISABLED(testStatusPages);
    CPPUNIT_TEST_SUITE_END();
};

StorageServerTest::StorageServerTest() = default;
StorageServerTest::~StorageServerTest() = default;

CPPUNIT_TEST_SUITE_REGISTRATION(StorageServerTest);

namespace {

    template<typename T>
    struct ConfigReader : public config::IFetcherCallback<T>,
                          public T
    {
        ConfigReader(const std::string& configId) {
            config::LegacySubscriber subscription;
            subscription.subscribe<T>(configId, this);
        }
        void configure(std::unique_ptr<document::DocumenttypesConfig> c)
        {
            static_cast<T&>(*this) = *c;
        }
    };

    struct Node {
        virtual ~Node() {}
        virtual StorageNode& getNode() = 0;
        virtual StorageNodeContext& getContext() = 0;

        bool attemptedStopped()
            { return getNode().attemptedStopped(); }
        void waitUntilInitialized(uint32_t timeout)
            { getNode().waitUntilInitialized(timeout); }
        StorageLink* getChain() { return getNode().getChain(); }
        void requestShutdown(const std::string& reason)
            { getNode().requestShutdown(reason); }
        const framework::StatusReporter* getStatusReporter(const std::string& i)
            { return getContext().getComponentRegister().getStatusReporter(i); }
        NodeStateUpdater& getStateUpdater()
            { return getContext().getComponentRegister().getNodeStateUpdater(); }
    };

    struct Distributor : public Node {
        DistributorProcess _process;

        Distributor(vdstestlib::DirConfig& config);
        ~Distributor();

        virtual StorageNode& getNode() override { return _process.getNode(); }
        virtual StorageNodeContext& getContext() override
            { return _process.getContext(); }
    };

    struct Storage : public Node {
        DummyServiceLayerProcess _process;
        StorageComponent::UP _component;

        Storage(vdstestlib::DirConfig& config);
        ~Storage();

        virtual StorageNode& getNode() override { return _process.getNode(); }
        virtual StorageNodeContext& getContext() override
            { return _process.getContext(); }
        spi::PartitionStateList getPartitions()
            { return _process.getProvider().getPartitionStates().getList(); }
        uint16_t getDiskCount() { return getPartitions().size(); }
        StorageComponent& getComponent() { return *_component; }
    };

Distributor::Distributor(vdstestlib::DirConfig& config)
    : _process(config.getConfigId())
{
    _process.setupConfig(60000);
    _process.createNode();
}
Distributor::~Distributor() {}

Storage::Storage(vdstestlib::DirConfig& config)
    : _process(config.getConfigId())
{
    _process.setupConfig(60000);
    _process.createNode();
    _component.reset(new StorageComponent(
    getContext().getComponentRegister(), "test"));
}
Storage::~Storage() {}

}

void
StorageServerTest::setUp()
{
    threadPool.reset(new FastOS_ThreadPool(128 * 1024));
    docMan.reset(new document::TestDocMan);
    system("chmod -R 755 vdsroot");
    system("rm -rf vdsroot*");
    slobrok.reset(new mbus::Slobrok);
    distConfig.reset(new vdstestlib::DirConfig(getStandardConfig(false)));
    storConfig.reset(new vdstestlib::DirConfig(getStandardConfig(true)));
    addSlobrokConfig(*distConfig, *slobrok);
    addSlobrokConfig(*storConfig, *slobrok);
    storConfig->getConfig("stor-filestor").set("fail_disk_after_error_count", "1");
    system("mkdir -p vdsroot/disks/d0");
    system("mkdir -p vdsroot.distributor");
    slobrokMirror.reset(new SlobrokMirror(slobrok->config()));
}

void
StorageServerTest::tearDown()
{
    slobrokMirror.reset(NULL);
    storConfig.reset(NULL);
    distConfig.reset(NULL);
    slobrok.reset(NULL);
    docMan.reset(NULL);
    threadPool.reset(NULL);
}

void
StorageServerTest::testNormalUsage()
{
    {
        Distributor distServer(*distConfig);
        Storage storServer(*storConfig);
    }
}

namespace {
    struct LoadGiver : public document::Runnable,
                       public mbus::IReplyHandler
    {
        const vdstestlib::DirConfig& _config;
        const std::shared_ptr<const document::DocumentTypeRepo> _repo;
        documentapi::LoadTypeSet _loadTypes;
        std::unique_ptr<mbus::RPCMessageBus> _mbus;
        mbus::SourceSession::UP _sourceSession;
        uint32_t _maxPending;
        uint32_t _currentPending;
        uint32_t _processedOk;
        uint32_t _unexpectedErrors;
        bool _startedShutdown;

        LoadGiver(const vdstestlib::DirConfig& config,
                  const std::shared_ptr<const document::DocumentTypeRepo> repo)
            : _config(config), _repo(repo), _mbus(), _sourceSession(),
              _maxPending(20), _currentPending(0), _processedOk(0),
              _unexpectedErrors(0), _startedShutdown(false) {}
        virtual ~LoadGiver() {
            if (_sourceSession.get() != 0) {
                _sourceSession->close();
            }
        }

        void init() {
            documentapi::DocumentProtocol::SP protocol(
                    new documentapi::DocumentProtocol(_loadTypes, _repo));
            storage::mbusprot::StorageProtocol::SP storageProtocol(
                    new storage::mbusprot::StorageProtocol(_repo, _loadTypes));
            mbus::ProtocolSet protocols;
            protocols.add(protocol);
            protocols.add(storageProtocol);
            mbus::RPCNetworkParams networkParams;
            networkParams.setSlobrokConfig(config::ConfigUri(_config.getConfigId()));
            _mbus.reset(new mbus::RPCMessageBus(protocols, networkParams,
                                                _config.getConfigId()));
            mbus::SourceSessionParams sourceParams;
            sourceParams.setTimeout(5000);
            mbus::StaticThrottlePolicy::SP policy(new mbus::StaticThrottlePolicy());
            policy->setMaxPendingCount(_maxPending);
            sourceParams.setThrottlePolicy(policy);
            _sourceSession = _mbus->getMessageBus().createSourceSession(
                    *this, sourceParams);
        }

        virtual void notifyStartingShutdown() {
            _startedShutdown = true;
        }

        virtual void handleReply(mbus::Reply::UP reply) override {
            using documentapi::DocumentProtocol;
            --_currentPending;
            if (!reply->hasErrors()) {
                ++_processedOk;
            } else if (!_startedShutdown && reply->getNumErrors() > 1) {
                ++_unexpectedErrors;
                std::cerr << reply->getNumErrors() << " errors. First: "
                          << reply->getError(0).getCode() << " - "
                          << reply->getError(0).getMessage() << "\n";
            } else {
                int code = reply->getError(0).getCode();
                std::string errorMsg = reply->getError(0).getMessage();

                if (code == mbus::ErrorCode::UNKNOWN_SESSION
                    || code == mbus::ErrorCode::NO_ADDRESS_FOR_SERVICE
                    || code == mbus::ErrorCode::CONNECTION_ERROR
                    || code == mbus::ErrorCode::HANDSHAKE_FAILED)
                {
                    // Ignore
                } else if ((code >= mbus::ErrorCode::TRANSIENT_ERROR
                            && code < mbus::ErrorCode::FATAL_ERROR)
                    && (errorMsg.find("UNKNOWN_SESSION") != std::string::npos
                        || errorMsg.find("NO_ADDRESS_FOR_SERVICE")
                            != std::string::npos
                        || errorMsg.find("when node is in state Stopping")
                            != std::string::npos
                        || errorMsg.find("HANDSHAKE_FAILED")
                            != std::string::npos
                        || code == mbus::ErrorCode::APP_TRANSIENT_ERROR
                        || code == DocumentProtocol::ERROR_IO_FAILURE
                        || code == DocumentProtocol::ERROR_ABORTED
                        || code == DocumentProtocol::ERROR_BUCKET_NOT_FOUND))
                {
                    // Ignore
                } else {
                    ++_unexpectedErrors;
                    std::cerr << reply->getNumErrors() << " errors. First: "
                              << reply->getError(0).getCode() << " - "
                              << reply->getError(0).getMessage();
                    mbus::Message::UP msg(reply->getMessage());
                    if (msg->getType() == DocumentProtocol::MESSAGE_PUTDOCUMENT)
                    {
                        documentapi::PutDocumentMessage& putMsg(
                                static_cast<documentapi::PutDocumentMessage&>(*msg));
                        std::cerr << " - " << putMsg.getDocument().getId();
                    }
                    std::cerr << "\n";
                }
            }
        }

        void waitUntilDecentLoad(uint32_t maxWait = 60000) {
            uint64_t maxTime = getTimeInMillis() + maxWait;
            while (true) {
                if (_processedOk > 5 && _currentPending > _maxPending / 2) {
                    break;
                }
                uint64_t time = getTimeInMillis();
                if (time > maxTime) {
                    if (_processedOk < 5) {
                        throw vespalib::IllegalStateException(
                            "Failed to process 5 ok operations within timeout.",
                            VESPA_STRLOC);
                    }
                    if (_currentPending < _maxPending / 2) {
                        throw vespalib::IllegalStateException(
                            "Failed to get enough max pending.",
                            VESPA_STRLOC);
                    }
                    break;
                }
                FastOS_Thread::Sleep(1);
            }
            LOG(info, "Currently, we have received %u ok replies and have %u pending ones.",
                _processedOk, _currentPending);
        }
    };

    struct SimpleLoadGiver : public LoadGiver {
        const document::TestDocMan& _testDocMan;
        vespalib::Monitor _threadMonitor;

        SimpleLoadGiver(const vdstestlib::DirConfig& config,
                        const document::TestDocMan& tdm)
            : LoadGiver(config, tdm.getTypeRepoSP()), _testDocMan(tdm),
                        _threadMonitor() {}
        ~SimpleLoadGiver() {
            stop();
            join();
        }
        virtual bool onStop() override {
            vespalib::MonitorGuard monitor(_threadMonitor);
            monitor.signal();
            return true;
        }
        void run() override {
            uint32_t seed = 0;
            uint32_t maxDocSize = 65536;
            init();
            vespalib::MonitorGuard monitor(_threadMonitor);
            while (running()) {
                uint32_t attemptCount = 0;
                while (_currentPending < _maxPending
                       && ++attemptCount < _maxPending)
                {
                    document::Document::SP doc(
                            _testDocMan.createRandomDocument(
                                ++seed, maxDocSize));
                    mbus::Message::UP msg(
                            new documentapi::PutDocumentMessage(doc));
                    msg->setRetryEnabled(false);
                    mbus::Result r = _sourceSession->send(std::move(msg),
                            "storage/cluster.storage/distributor/0/default",
                            true);
                    if (r.isAccepted()){
                        ++_currentPending;
                    } else {
                        if (!_startedShutdown) {
                            std::cerr << "Source session did not accept "
                                         "message.\n";
                        }
                        break;
                    }
                }
                monitor.wait(1);
            }
        }
    };

    void setSystemState(SlobrokMirror& mirror,
                        const lib::ClusterState& state,
                        const std::vector<std::string>& address)
    {
        std::string systemState = state.toString();
        auto deleter = [](auto * ptr) { ptr->SubRef(); };
        for (uint32_t i=0; i<address.size(); ++i) {
            slobrok::api::MirrorAPI::SpecList list(
                    mirror.getMirror().lookup(address[i]));
            for (uint32_t j=0; j<list.size(); ++j) {
                auto target = std::unique_ptr<FRT_Target, decltype(deleter)>(mirror.getSupervisor().GetTarget(
                                                                             list[j].second.c_str()), deleter);
                auto req = std::unique_ptr<FRT_RPCRequest, decltype(deleter)>(mirror.getSupervisor().AllocRPCRequest(),
                                                                              deleter);
                req->SetMethodName("setsystemstate2");
                req->GetParams()->AddString(systemState.c_str());
                target->InvokeSync(req.get(), 5.0);
                if (req->GetErrorCode() != FRTE_NO_ERROR) {
                    throw vespalib::IllegalStateException(
                            "Failed sending setsystemstate request: "
                          + std::string(req->GetErrorMessage()), VESPA_STRLOC);
                }
            }
        }
    }
}

void
StorageServerTest::testShutdownDuringDiskLoad(bool storagenode)
{
    slobrokMirror->init(5000);
        // Verify that, then shutdown, we stop accepting new messages, fail
        // all messages enqueued and finish current operations before shutting
        // down without any errors.
    std::unique_ptr<Distributor> distServer(new Distributor(*distConfig));
    std::unique_ptr<Storage> storServer(new Storage(*storConfig));
    storServer->waitUntilInitialized(30);
    LOG(info, "\n\nStorage server stable\n\n");
    lib::ClusterState state("version:1 bits:1 distributor:1 storage:1");
    std::vector<std::string> addresses;
    addresses.push_back("storage/cluster.storage/storage/0");
    addresses.push_back("storage/cluster.storage/distributor/0");
    LOG(info, "\n\nSetting system states\n\n");
    setSystemState(*slobrokMirror, state, addresses);
    LOG(info, "\n\nWaiting for stable distributor server\n\n");
    distServer->waitUntilInitialized(30);

    LOG(info, "\n\nSTARTING LOADGIVER\n\n");

    SimpleLoadGiver loadGiver(*distConfig, *docMan);
    loadGiver.start(*threadPool);
    loadGiver.waitUntilDecentLoad();

    loadGiver.notifyStartingShutdown();

    if (storagenode) {
        LOG(info, "\n\nKILLING STORAGE NODE\n\n");
        storServer->requestShutdown(
                "Stopping storage server during load for testing");
        storServer.reset(0);
    } else {
        LOG(info, "\n\nKILLING DISTRIBUTOR\n\n");
        distServer->requestShutdown(
                "Stopping distributor during load for testing");
        distServer.reset(0);
    }
    LOG(info, "\n\nDONE KILLING NODE. Cleaning up other stuff.\n\n");

    CPPUNIT_ASSERT_EQUAL(0u, loadGiver._unexpectedErrors);
}

void
StorageServerTest::testShutdownStorageDuringDiskLoad()
{
    testShutdownDuringDiskLoad(true);
}

void
StorageServerTest::testShutdownDistributorDuringDiskLoad()
{
    testShutdownDuringDiskLoad(false);
}

void
StorageServerTest::testShutdownAfterDiskFailure_Stress()
{
    slobrokMirror->init(5000);

    // Verify that, then shutdown, we stop accepting new messages, fail
    // all messages enqueued and finish current operations before shutting
    // down without any errors.
    std::unique_ptr<Distributor> distServer(new Distributor(*distConfig));
    std::unique_ptr<Storage> storServer(new Storage(*storConfig));
    //storServer->getSlotFileCache().disable();
    storServer->waitUntilInitialized(30);
    LOG(info, "\n\nStorage server stable\n\n");
    lib::ClusterState state("version:1 bits:1 distributor:1 storage:1");
    std::vector<std::string> addresses;
    addresses.push_back("storage/cluster.storage/storage/0");
    addresses.push_back("storage/cluster.storage/distributor/0");
    LOG(info, "\n\nSetting system states\n\n");
    setSystemState(*slobrokMirror, state, addresses);
    LOG(info, "\n\nWaiting for stable distributor server\n\n");
    distServer->waitUntilInitialized(30);

    LOG(info, "\n\nSTARTING LOADGIVER\n\n");

    SimpleLoadGiver loadGiver(*distConfig, *docMan);
    loadGiver.start(*threadPool);
    loadGiver.waitUntilDecentLoad();

    // Test that getting io errors flags storage for shutdown
    // (The shutdown is the responsibility of the application in
    // storageserver)
    CPPUNIT_ASSERT(!storServer->attemptedStopped());
    loadGiver.notifyStartingShutdown();
    LOG(info, "\n\nREMOVING PERMISSIONS\n\n");
    system("chmod 000 vdsroot/disks/d0/*.0");
    system("ls -ld vdsroot/disks/d0/* > permissions");

    for (uint32_t i=0; i<6000; ++i) {
        //storServer->getMemFileCache().clear();
        if (storServer->attemptedStopped()) break;
        FastOS_Thread::Sleep(10);
    }
    if (!storServer->attemptedStopped()) {
        CPPUNIT_FAIL("Removing permissions from disk failed to stop storage "
                     "within timeout of 60 seconds");
    }

    CPPUNIT_ASSERT_EQUAL(0u, loadGiver._unexpectedErrors);
    unlink("permissions");
}

namespace {

    struct PriorityStorageLoadGiver : public LoadGiver {
        const document::TestDocMan& _testDocMan;
        vespalib::Monitor _threadMonitor;
        StorBucketDatabase _bucketDB;
        document::BucketIdFactory _idFactory;
        uint32_t _putCount;
        uint32_t _getCount;
        uint32_t _removeCount;
        uint32_t _joinCount;
        uint32_t _splitCount;
        uint32_t _createBucket;
        uint32_t _deleteBucket;
        uint32_t _remappedOperations;
        uint32_t _notFoundOps;
        uint32_t _existOps;
        uint32_t _bucketDeletedOps;
        uint32_t _bucketNotFoundOps;
        uint32_t _rejectedOps;

        PriorityStorageLoadGiver(const vdstestlib::DirConfig& config,
                                 const document::TestDocMan& tdm)
            : LoadGiver(config, tdm.getTypeRepoSP()), _testDocMan(tdm),
              _threadMonitor(),
              _bucketDB(), _idFactory(),
              _putCount(0), _getCount(0), _removeCount(0), _joinCount(0),
              _splitCount(0), _createBucket(0), _deleteBucket(0),
              _remappedOperations(0), _notFoundOps(0), _existOps(0),
              _bucketDeletedOps(0), _bucketNotFoundOps(0), _rejectedOps(0) {}
        ~PriorityStorageLoadGiver() {
            close();
        }
        virtual void close() {
            if (running()) {
                stop();
                join();
            }
        }
        virtual bool onStop() override {
            vespalib::MonitorGuard monitor(_threadMonitor);
            monitor.signal();
            return true;
        }

        void run() override {
            uint32_t seed = 0;
            uint32_t maxDocSize = 65536;
            init();
            vespalib::MonitorGuard monitor(_threadMonitor);
            std::list<mbusprot::StorageCommand*> sendList;
            while (running()) {
                while (sendList.size() < (_maxPending - _currentPending)) {
                    document::Document::SP doc(
                            _testDocMan.createRandomDocument(
                                ++seed, maxDocSize));
                    api::StorageCommand::SP cmd;
                    document::BucketId bucket(
                            _idFactory.getBucketId(doc->getId()));
                    std::map<document::BucketId,
                             StorBucketDatabase::WrappedEntry> entries(
                                _bucketDB.getContained(bucket, ""));
                    if (entries.size() == 0
                        || (entries.size() == 1
                            && (entries.begin()->second->getBucketInfo().getChecksum() & 2) != 0))
                    {
                        if (entries.size() == 0) {
                            bucket.setUsedBits(4);
                            bucket = bucket.stripUnused();
                            entries[bucket] = _bucketDB.get(bucket, "",
                                    StorBucketDatabase::CREATE_IF_NONEXISTING);
                            entries[bucket]->setChecksum(0);
                        } else {
                            bucket = entries.begin()->first;
                            entries[bucket]->setChecksum(
                                    entries[bucket]->getBucketInfo().getChecksum() & ~2);
                        }
                        entries[bucket]->disk = 0;
                        entries[bucket].write();
                        entries[bucket] = _bucketDB.get(bucket, "foo");
                        CPPUNIT_ASSERT(entries[bucket].exist());
                        cmd.reset(new api::CreateBucketCommand(makeDocumentBucket(bucket)));
                        sendList.push_back(new mbusprot::StorageCommand(cmd));
                    }
                    CPPUNIT_ASSERT_EQUAL(size_t(1), entries.size());
                    bucket = entries.begin()->first;
                    auto *entry_wrapper = &(entries.begin()->second);
                    auto *entry = entry_wrapper->get();
                    if (seed % 95 == 93) { // Delete bucket
                        if ((entry->getBucketInfo().getChecksum() & 2) == 0) {
                            cmd.reset(new api::DeleteBucketCommand(makeDocumentBucket(bucket)));
                            entry->setChecksum(
                                    entry->getBucketInfo().getChecksum() | 2);
                            entry_wrapper->write();
                            sendList.push_back(
                                    new mbusprot::StorageCommand(cmd));
                        }
                    } else if (seed % 13 == 8) { // Join
                        if (entry->getBucketInfo().getChecksum() == 0 && bucket.getUsedBits() > 3) {
                                // Remove existing locks we have to not cause
                                // deadlock
                            entry = nullptr;
                            entry_wrapper = nullptr;
                            entries.clear();
                                // Then continue
                            document::BucketId super(bucket.getUsedBits() - 1,
                                                     bucket.getRawId());
                            super = super.stripUnused();
                            api::JoinBucketsCommand::SP jcmd(
                                    new api::JoinBucketsCommand(makeDocumentBucket(super)));
                            entries = _bucketDB.getAll(super, "foo");
                            bool foundAnyLocked = false;
                            for (std::map<document::BucketId,
                                          StorBucketDatabase::WrappedEntry>
                                            ::iterator it = entries.begin();
                                 it != entries.end(); ++it)
                            {
                                if (!super.contains(it->first) || super == it->first) continue;
                                jcmd->getSourceBuckets().push_back(
                                        it->first.stripUnused());
                                foundAnyLocked |= (it->second->getBucketInfo().getChecksum() != 0);
                            }
                            if (!foundAnyLocked && jcmd->getSourceBuckets().size() == 2) {
                                for (std::map<document::BucketId,
                                              StorBucketDatabase::WrappedEntry>
                                            ::iterator it = entries.begin();
                                     it != entries.end(); ++it)
                                {
                                    if (!super.contains(it->first)) continue;
                                    it->second->setChecksum(
                                            it->second->getBucketInfo().getChecksum() | 1);
                                    it->second.write();
                                }
                                cmd = jcmd;
                                sendList.push_back(
                                        new mbusprot::StorageCommand(cmd));
                            }
                        }
                    } else if (seed % 13 == 1) { // Split
                            // Use _checksum == 1 to mean that we have a pending
                            // maintenance operation to this bucket.
                        if (entry->getBucketInfo().getChecksum() == 0) {
                            cmd.reset(new api::SplitBucketCommand(makeDocumentBucket(bucket)));
                            entry->setChecksum(1);
                            entry_wrapper->write();
                            sendList.push_back(
                                    new mbusprot::StorageCommand(cmd));
                        }
                    } else if (seed % 7 == 5) { // Remove
                        if ((entry->getBucketInfo().getChecksum() & 2) == 0) {
                            cmd.reset(new api::RemoveCommand(makeDocumentBucket(bucket),
                                        doc->getId(), 1000ull * seed + 2));
                            sendList.push_back(
                                    new mbusprot::StorageCommand(cmd));
                        }
                    } else if (seed % 5 == 3) { // Get
                        if ((entry->getBucketInfo().getChecksum() & 2) == 0) {
                            cmd.reset(new api::GetCommand(
                                        makeDocumentBucket(bucket), doc->getId(), "[all]"));
                            sendList.push_back(
                                    new mbusprot::StorageCommand(cmd));
                        }
                    } else { // Put
                        if ((entry->getBucketInfo().getChecksum() & 2) == 0) {
                            cmd.reset(new api::PutCommand(
                                        makeDocumentBucket(bucket), doc, 1000ull * seed + 1));
                            sendList.push_back(
                                    new mbusprot::StorageCommand(cmd));
                        }
                    }
                    if (!sendList.empty()) {
                        uint8_t priorities[] = {
                            api::StorageMessage::LOW,
                            api::StorageMessage::NORMAL,
                            api::StorageMessage::HIGH,
                            api::StorageMessage::VERYHIGH
                        };
                        sendList.back()->getCommand()->setPriority(priorities[seed % 4]);
                    }
                }
                if (sendList.size() > 0) {
                    uint32_t sent = 0;
                    for (uint32_t i=0; i<sendList.size(); ++i) {
                        mbus::Message::UP msg(*sendList.begin());
                        msg->setRetryEnabled(false);
                        mbus::Result r = _sourceSession->send(std::move(msg),
                                "storage/cluster.storage/storage/0/default",
                                true);
                        if (r.isAccepted()){
                            sendList.pop_front();
                            ++_currentPending;
                            ++sent;
                        } else {
                            r.getMessage().release();
                            break;
                        }
                    }
                }
                monitor.wait(1);
            }
        }

        std::string report() {
            std::ostringstream ost;
            ost << "Performed ("
                << _putCount << ", " << _getCount << ", "
                << _removeCount << ", " << _splitCount << ", "
                << _joinCount << ", " << _createBucket << ", "
                << _deleteBucket
                << ") put/get/remove/split/join/create/delete operations.\n"
                << "Result: " << _remappedOperations << " remapped operations\n"
                << "  " << _processedOk << " ok responses.\n"
                << "  " << _notFoundOps << " NOT_FOUND responses.\n"
                << "  " << _existOps << " EXISTS responses\n"
                << "  " << _bucketDeletedOps << " BUCKET_DELETED responses\n"
                << "  " << _bucketNotFoundOps << " BUCKET_NOT_FOUND responses\n"
                << "  " << _rejectedOps << " REJECTED responses (duplicate splits)\n"
                << "  " << _unexpectedErrors << " unexpected errors\n";
            return ost.str();
        }

        virtual void handleReply(mbus::Reply::UP reply) override {
            if (_startedShutdown) return;
            --_currentPending;
            std::ostringstream err;
            mbusprot::StorageReply* mreply(
                    dynamic_cast<mbusprot::StorageReply*>(reply.get()));
            if (mreply == 0) {
                ++_unexpectedErrors;
                err << "Got unexpected reply which is not a storage reply, "
                    << "likely emptyreply.";
                if (reply->hasErrors()) {
                    int code = reply->getError(0).getCode();
                    std::string errorMsg = reply->getError(0).getMessage();
                    err << "\n mbus(" << code << "): '" << errorMsg << "'";
                }
                err << "\n";
                std::cerr << err.str();
                return;
            }
            api::StorageReply& sreply(*mreply->getReply());
            api::BucketReply& breply(static_cast<api::BucketReply&>(sreply));

            if ((!reply->hasErrors()
                  && sreply.getResult().success())
                || sreply.getResult().getResult() == api::ReturnCode::EXISTS
                || sreply.getResult().getMessage().find("Bucket does not exist; assuming already split") != std::string::npos
                || sreply.getResult().getResult()
                            == api::ReturnCode::BUCKET_DELETED
                || sreply.getResult().getResult()
                            == api::ReturnCode::BUCKET_NOT_FOUND)
            {
                std::ostringstream out;
                if (breply.hasBeenRemapped()) {
                    ++_remappedOperations;
                }
                if (sreply.getType() == api::MessageType::JOINBUCKETS_REPLY) {
                    vespalib::MonitorGuard monitor(_threadMonitor);
                    api::JoinBucketsReply& joinReply(
                            static_cast<api::JoinBucketsReply&>(sreply));
                    StorBucketDatabase::WrappedEntry entry(
                            _bucketDB.get(joinReply.getBucketId(), "",
                                StorBucketDatabase::CREATE_IF_NONEXISTING));
                    entry->setChecksum(0);
                    entry->disk = 0;
                    entry.write();
                    for(std::vector<document::BucketId>::const_iterator it
                            = joinReply.getSourceBuckets().begin();
                        it != joinReply.getSourceBuckets().end(); ++it)
                    {
                        _bucketDB.erase(*it, "foo");
                    }
                    ++_joinCount;
                    out << "OK " << joinReply.getBucketId() << " Join\n";
                } else if (sreply.getType()
                                == api::MessageType::SPLITBUCKET_REPLY
                           && sreply.getResult().getResult() != api::ReturnCode::REJECTED)
                {
                    vespalib::MonitorGuard monitor(_threadMonitor);
                    api::SplitBucketReply& splitReply(
                            static_cast<api::SplitBucketReply&>(sreply));
                    StorBucketDatabase::WrappedEntry entry(
                            _bucketDB.get(splitReply.getBucketId(), "foo"));
                    if (entry.exist()) {
                        //CPPUNIT_ASSERT((entry->getBucketInfo().getChecksum() & 1) != 0);
                        entry.remove();
                    }
                    for(std::vector<api::SplitBucketReply::Entry>::iterator it
                            = splitReply.getSplitInfo().begin();
                        it != splitReply.getSplitInfo().end(); ++it)
                    {
                        entry = _bucketDB.get(it->first, "foo",
                                StorBucketDatabase::CREATE_IF_NONEXISTING);
                        entry->setChecksum(0);
                        entry->disk = 0;
                        entry.write();
                    }
                    ++_splitCount;
                    out << "OK " << splitReply.getBucketId() << " Split\n";
                } else if (sreply.getType() == api::MessageType::PUT_REPLY) {
                    ++_putCount;
                    if (!static_cast<api::PutReply&>(sreply).wasFound()) {
                        ++_notFoundOps;
                    }
                    out << "OK " << breply.getBucketId() << " Put\n";
                } else if (sreply.getType() == api::MessageType::GET_REPLY) {
                    ++_getCount;
                    if (!static_cast<api::GetReply&>(sreply).wasFound()) {
                        ++_notFoundOps;
                    }
                    out << "OK " << breply.getBucketId() << " Get\n";
                } else if (sreply.getType() == api::MessageType::REMOVE_REPLY) {
                    ++_removeCount;
                    if (!static_cast<api::RemoveReply&>(sreply).wasFound()) {
                        ++_notFoundOps;
                    }
                    out << "OK " << breply.getBucketId() << " Remove\n";
                } else if (sreply.getType()
                                == api::MessageType::CREATEBUCKET_REPLY)
                {
                    ++_createBucket;
                    out << "OK " << breply.getBucketId() << " Create\n";
                } else if (sreply.getType()
                                == api::MessageType::DELETEBUCKET_REPLY)
                {
                    ++_deleteBucket;
                    out << "OK " << breply.getBucketId() << " Delete\n";
                }
                switch (sreply.getResult().getResult()) {
                    case api::ReturnCode::EXISTS: ++_existOps; break;
                    case api::ReturnCode::BUCKET_NOT_FOUND:
                            ++_bucketNotFoundOps; break;
                    case api::ReturnCode::BUCKET_DELETED:
                            ++_bucketDeletedOps; break;
                    case api::ReturnCode::REJECTED:
                            ++_rejectedOps; break;
                    case api::ReturnCode::OK: ++_processedOk; break;
                    default:
                        assert(false);
                }
                //std::cerr << "OK - " << sreply.getType() << "\n";
                if (_processedOk % 5000 == 0) {
                    out << report();
                }
                //err << out.str();
            } else {
                ++_unexpectedErrors;
                api::BucketReply& brep(static_cast<api::BucketReply&>(sreply));
                err << "Failed " << brep.getBucketId() << " "
                    << sreply.getType().getName() << ":";
                if (reply->hasErrors()) {
                    int code = reply->getError(0).getCode();
                    std::string errorMsg = reply->getError(0).getMessage();
                    err << " mbus(" << code << "): '" << errorMsg << "'";
                }
                if (sreply.getResult().failed()) {
                    err << " sapi: " << sreply.getResult() << "\n";
                }
            }
            std::cerr << err.str();
        }
    };

    enum StateType { REPORTED, CURRENT };
    void waitForStorageUp(StorageComponent& storageNode,
                          StateType type, time_t timeoutMS = 60000)
    {
        framework::defaultimplementation::RealClock clock;
        framework::MilliSecTime timeout = clock.getTimeInMillis()
                                        + framework::MilliSecTime(timeoutMS);
        while (true) {
            lib::NodeState::CSP ns(type == REPORTED
                    ? storageNode.getStateUpdater().getReportedNodeState()
                    : storageNode.getStateUpdater().getCurrentNodeState());
            if (ns->getState() == lib::State::UP) return;
            if (clock.getTimeInMillis() > timeout) {
                std::ostringstream ost;
                ost << "Storage node failed to get up within timeout of "
                    << timeoutMS << " ms. Current state is: " << ns;
                CPPUNIT_FAIL(ost.str());
            }
            FastOS_Thread::Sleep(10);
        }
    }

}

void
StorageServerTest::testPriorityAndQueueSneakingWhileSplitJoinStressTest()
{
    PriorityStorageLoadGiver loadGiver(*storConfig, *docMan);
    Storage storServer(*storConfig);
    waitForStorageUp(storServer.getComponent(), REPORTED);
    api::SetSystemStateCommand::SP cmd(new api::SetSystemStateCommand(
                lib::ClusterState("storage:1")));
    storServer.getChain()->sendDown(cmd);
    waitForStorageUp(storServer.getComponent(), CURRENT);
    loadGiver.start(*threadPool);
    while (loadGiver._processedOk + loadGiver._unexpectedErrors < 1000) {
        FastOS_Thread::Sleep(100);
        std::cerr << "OK " << loadGiver._processedOk << " Errors: "
                  << loadGiver._unexpectedErrors << "\n";
    }
    loadGiver.notifyStartingShutdown();
    loadGiver.stop();
    loadGiver.close();
    std::cerr << loadGiver.report();
    CPPUNIT_ASSERT(loadGiver._bucketNotFoundOps < 300);
    CPPUNIT_ASSERT(loadGiver._unexpectedErrors == 0);
}

// This test is not a stress test, but adding stress to its name makes it not
// run during regular make test runs
void
StorageServerTest::testPortOverlap_Stress()
{
    for (uint32_t i=0; i<3; ++i) {
        std::cerr << "Run " << i << "\n";
        tearDown();
        setUp();
        const char* config = "stor-communicationmanager";
        std::string type = "none";
        if (i == 0) {
            distConfig->getConfig(config).set("mbusport", "12301");
            storConfig->getConfig(config).set("mbusport", "12311");
            type = "mbusport";
        } else if (i == 1) {
            distConfig->getConfig(config).set("rpcport", "12302");
            storConfig->getConfig(config).set("rpcport", "12312");
            type = "rpcport";
        } else if (i == 2) {
            distConfig->getConfig("stor-status").set("httpport", "12303");
            storConfig->getConfig("stor-status").set("httpport", "12313");
            type = "httpport";
        }
        LOG(info, "TEST: (0) STARTING PORT TEST: %s", type.c_str());
        slobrokMirror->init(5000);

        std::unique_ptr<Distributor> distServerOld(new Distributor(*distConfig));
        std::unique_ptr<Storage> storServerOld(new Storage(*storConfig));

        LOG(info, "TEST: (1) WAITING FOR STABLE STORAGE SERVER");
        storServerOld->waitUntilInitialized(30);
        LOG(info, "TEST: (2) STORAGE SERVER STABLE");
        lib::ClusterState state("version:1 distributor:1 storage:1");
        std::vector<std::string> addresses;
        addresses.push_back("storage/cluster.storage/storage/0");
        addresses.push_back("storage/cluster.storage/distributor/0");
        LOG(info, "TEST: (3) SETTING SYSTEM STATES");
        setSystemState(*slobrokMirror, state, addresses);
        LOG(info, "TEST: (4) WAITING FOR STABLE DISTRIBUTOR SERVER");
        distServerOld->waitUntilInitialized(30);

        {
            LOG(info, "TEST: (5) ADDING SOME LOAD TO CHECK PORTS");
            SimpleLoadGiver loadGiver(*distConfig, *docMan);
            loadGiver.start(*threadPool);
            loadGiver.waitUntilDecentLoad();
        }

        LOG(info, "TEST: (6) CREATING NEW SET OF SERVERS");
        try{
            Distributor distServer(*distConfig);
            CPPUNIT_FAIL("Distributor server failed to fail on busy " + type);
        } catch (vespalib::Exception& e) {
            std::string msg = e.getMessage();
            std::string::size_type pos = msg.rfind(':');
            if (pos != std::string::npos) msg = msg.substr(pos + 2);
            if (msg == "Failed to listen to RPC port 12302." ||
                msg == "Failed to start network." ||
                msg == "Failed to start status HTTP server using port 12303.")
            {
            } else {
                CPPUNIT_FAIL("Unexpected exception: " + msg);
            }
        }
        try{
            Storage storServer(*storConfig);
            CPPUNIT_FAIL("Storage server failed to fail on busy " + type);
        } catch (vespalib::Exception& e) {
            std::string msg = e.getMessage();
            std::string::size_type pos = msg.rfind(':');
            if (pos != std::string::npos) msg = msg.substr(pos + 2);
            if (msg == "Failed to listen to RPC port 12312." ||
                msg == "Failed to start network." ||
                msg == "Failed to start status HTTP server using port 12313.")
            {
            } else {
                CPPUNIT_FAIL("Unexpected exception: " + msg);
            }
        }
    }
}

void
StorageServerTest::testStatusPages()
{
    Storage storServer(*storConfig);
        // Bucket manager doesn't set up metrics before after talking to
        // persistence layer
    storServer.getNode().waitUntilInitialized();
    {
            // Get HTML status pages
        framework::HttpUrlPath path("?interval=-2&format=html");
        std::ostringstream ost;
        try{
            const framework::StatusReporter* reporter(
                storServer.getStatusReporter("statusmetricsconsumer"));
            CPPUNIT_ASSERT(reporter != 0);
            reporter->reportStatus(ost, path);
        } catch (std::exception& e) {
            CPPUNIT_FAIL("Failed to get status metric page: "
                    + std::string(e.what()) + "\nGot so far: " + ost.str());
        }
        std::string output = ost.str();
        CPPUNIT_ASSERT_MSG(output,
                           output.find("Exception") == std::string::npos);
        CPPUNIT_ASSERT_MSG(output, output.find("Error") == std::string::npos);
    }
}

} // storage
