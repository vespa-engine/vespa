// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-attributes.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/persistence/dummyimpl/dummy_bucket_executor.h>
#include <vespa/searchcore/proton/attribute/attribute_config_inspector.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/attribute/i_attribute_manager.h>
#include <vespa/searchcore/proton/bucketdb/bucket_create_notifier.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/documentmetastore/operation_listener.h>
#include <vespa/searchcore/proton/feedoperation/moveoperation.h>
#include <vespa/searchcore/proton/feedoperation/pruneremoveddocumentsoperation.h>
#include <vespa/searchcore/proton/feedoperation/putoperation.h>
#include <vespa/searchcore/proton/feedoperation/removeoperation.h>
#include <vespa/searchcore/proton/server/blockable_maintenance_job.h>
#include <vespa/searchcore/proton/server/executor_thread_service.h>
#include <vespa/searchcore/proton/server/i_operation_storer.h>
#include <vespa/searchcore/proton/server/ibucketmodifiedhandler.h>
#include <vespa/searchcore/proton/server/idocumentmovehandler.h>
#include <vespa/searchcore/proton/server/iheartbeathandler.h>
#include <vespa/searchcore/proton/server/ipruneremoveddocumentshandler.h>
#include <vespa/searchcore/proton/server/maintenance_controller_explorer.h>
#include <vespa/searchcore/proton/server/maintenance_jobs_injector.h>
#include <vespa/searchcore/proton/server/maintenancecontroller.h>
#include <vespa/searchcore/proton/test/buckethandler.h>
#include <vespa/searchcore/proton/test/clusterstatehandler.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/test/mock_attribute_manager.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchlib/common/idocumentmetastore.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/gate.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/monitored_refcount.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <thread>

using namespace proton;
using namespace vespalib::slime;
using document::BucketId;
using document::Document;
using document::DocumentId;
using document::test::makeBucketSpace;
using vespalib::system_clock;
using proton::bucketdb::BucketCreateNotifier;
using search::AttributeGuard;
using search::DocumentIdT;
using search::DocumentMetaData;
using vespalib::IDestructorCallback;
using search::SerialNum;
using search::CommitParam;
using storage::spi::BucketInfo;
using storage::spi::Timestamp;
using vespalib::MonitoredRefCount;
using vespalib::Slime;
using vespalib::makeLambdaTask;
using vespa::config::search::AttributesConfigBuilder;
using storage::spi::dummy::DummyBucketExecutor;

using BlockedReason = IBlockableMaintenanceJob::BlockedReason;

using BucketIdVector = BucketId::List;

constexpr vespalib::duration TIMEOUT = 60s;
constexpr vespalib::duration DELAY = 10ms;

namespace {

VESPA_THREAD_STACK_TAG(my_executor_init);

void
sampleThreadId(std::thread::id *threadId)
{
    *threadId = std::this_thread::get_id();
}

class MyDocumentSubDB
{
    using DocMap = std::map<DocumentIdT, Document::SP>;
    DocMap _docs;
    uint32_t _subDBId;
    DocumentMetaStore::SP _metaStoreSP;
    DocumentMetaStore & _metaStore;
    std::shared_ptr<const document::DocumentTypeRepo> _repo;
    const DocTypeName &_docTypeName;

public:
    MyDocumentSubDB(uint32_t subDBId, SubDbType subDbType, std::shared_ptr<const document::DocumentTypeRepo> repo,
                    std::shared_ptr<bucketdb::BucketDBOwner> bucketDB, const DocTypeName &docTypeName);
    ~MyDocumentSubDB();

    uint32_t getSubDBId() const { return _subDBId; }

    Document::UP
    getDocument(DocumentIdT lid) const
    {
        auto it(_docs.find(lid));
        if (it != _docs.end()) {
            return Document::UP(it->second->clone());
        } else {
            return Document::UP();
        }
    }

    MaintenanceDocumentSubDB getSubDB();
    void handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &op);
    void handleRemove(RemoveOperationWithDocId &op);
    void prepareMove(MoveOperation &op);
    void handleMove(const MoveOperation &op);
    uint32_t getNumUsedLids() const;
    uint32_t getDocumentCount() const { return _docs.size(); }

    void setBucketState(const BucketId &bucket, bool active) {
        _metaStore.setBucketState(bucket, active);
    }

    const IDocumentMetaStore &getMetaStore() const { return _metaStore; }
};

MyDocumentSubDB::MyDocumentSubDB(uint32_t subDBId, SubDbType subDbType, std::shared_ptr<const document::DocumentTypeRepo> repo,
                                 std::shared_ptr<bucketdb::BucketDBOwner> bucketDB, const DocTypeName &docTypeName)
    : _docs(),
      _subDBId(subDBId),
      _metaStoreSP(std::make_shared<DocumentMetaStore>(
              std::move(bucketDB), DocumentMetaStore::getFixedName(), search::GrowStrategy(),
              subDbType)),
      _metaStore(*_metaStoreSP),
      _repo(std::move(repo)),
      _docTypeName(docTypeName)
{
    _metaStore.constructFreeList();
}
MyDocumentSubDB::~MyDocumentSubDB() = default;

struct MyDocumentRetriever : public DocumentRetrieverBaseForTest
{
    MyDocumentSubDB &_subDB;

    explicit MyDocumentRetriever(MyDocumentSubDB &subDB) noexcept : _subDB(subDB) { }

    const document::DocumentTypeRepo & getDocumentTypeRepo() const override { abort(); }
    void getBucketMetaData(const storage::spi::Bucket &, DocumentMetaData::Vector &) const override { abort(); }
    DocumentMetaData getDocumentMetaData(const DocumentId &) const override { return {}; }
    Document::UP getFullDocument(DocumentIdT lid) const override { return _subDB.getDocument(lid); }
    CachedSelect::SP parseSelect(const std::string &) const override { return {}; }
};


struct MyBucketModifiedHandler : public IBucketModifiedHandler
{
    BucketIdVector _modified;
    void notifyBucketModified(const BucketId &bucket) override {
        auto itr = std::find(_modified.begin(), _modified.end(), bucket);
        if (itr == _modified.end()) {
            _modified.push_back(bucket);
        }
    }
    void reset() { _modified.clear(); }
};

class MyFeedHandler : public IDocumentMoveHandler,
                      public IPruneRemovedDocumentsHandler,
                      public IHeartBeatHandler,
                      public IOperationStorer
{
    std::thread::id                _executorThreadId;
    std::vector<MyDocumentSubDB *> _subDBs;
    SerialNum                      _serialNum;
    std::atomic<uint32_t>          _heartBeats;
public:
    explicit MyFeedHandler(std::thread::id executorThreadId);

    ~MyFeedHandler() override;

    bool isExecutorThread() const;

    MoveResult handleMove(MoveOperation &op, IDestructorCallback::SP moveDoneCtx) override;
    void performPruneRemovedDocuments(PruneRemovedDocumentsOperation &op) override;
    void heartBeat() override;

    void setSubDBs(const std::vector<MyDocumentSubDB *> &subDBs);

    SerialNum inc_serial_num() {
        return ++_serialNum;
    }

    // Implements IOperationStorer
    void appendOperation(const FeedOperation &op, DoneCallback) override;
    CommitResult startCommit(DoneCallback) override {
        return CommitResult();
    }

    uint32_t getHeartBeats() const {
        return _heartBeats.load(std::memory_order_relaxed);
    }
};

class MyExecutor : public vespalib::ThreadStackExecutorBase
{
public:
    std::thread::id _threadId;

    MyExecutor();
    bool acceptNewTask(unique_lock &, std::condition_variable &) override {
        return isRoomForNewTask();
    }
    void wakeup(unique_lock &, std::condition_variable &) override {}

    ~MyExecutor() override;

    bool isIdle();
    bool waitIdle(vespalib::duration timeout);
};

struct MySimpleJob : public BlockableMaintenanceJob
{
    vespalib::CountDownLatch _latch;
    size_t                   _runCnt;

    MySimpleJob(vespalib::duration delay,
                vespalib::duration interval,
                uint32_t finishCount)
        : BlockableMaintenanceJob("my_job", delay, interval),
          _latch(finishCount),
          _runCnt(0)
    { }
    void block() { setBlocked(BlockedReason::FROZEN_BUCKET); }
    bool run() override {
        _latch.countDown();
        ++_runCnt;
        return true;
    }
};

struct MySplitJob : public MySimpleJob
{
    MySplitJob(vespalib::duration delay,
               vespalib::duration interval,
               uint32_t finishCount)
        : MySimpleJob(delay, interval, finishCount)
    {
    }
    bool run() override {
        _latch.countDown();
        ++_runCnt;
        return _latch.getCount() == 0;
    }
};

struct MyLongRunningJob : public BlockableMaintenanceJob
{
    vespalib::Gate _firstRun;

    MyLongRunningJob(vespalib::duration delay,
                     vespalib::duration interval)
        : BlockableMaintenanceJob("long_running_job", delay, interval),
          _firstRun()
    {
    }
    ~MyLongRunningJob() override;
    void block() { setBlocked(BlockedReason::FROZEN_BUCKET); }
    bool run() override {
        _firstRun.countDown();
	std::this_thread::sleep_for(1ms);
        return false;
    }
};

MyLongRunningJob::~MyLongRunningJob() = default;

using MyAttributeManager = test::MockAttributeManager;

MaintenanceDocumentSubDB
MyDocumentSubDB::getSubDB()
{
    auto retriever = std::make_shared<MyDocumentRetriever>(*this);

    return MaintenanceDocumentSubDB("my_sub_db", _subDBId,
                                    _metaStoreSP,
                                    retriever,
                                    IFeedView::SP(), nullptr);
}


void
MyDocumentSubDB::handlePruneRemovedDocuments(const PruneRemovedDocumentsOperation &op)
{
    assert(_subDBId == 1u);
    using LidVector = LidVectorContext::LidVector;
    const SerialNum serialNum = op.getSerialNum();
    const LidVectorContext &lidCtx = *op.getLidsToRemove();
    const LidVector &lidsToRemove(lidCtx.getLidVector());
    _metaStore.removeBatch(lidsToRemove, lidCtx.getDocIdLimit());
    _metaStore.removes_complete(lidsToRemove);
    _metaStore.commit(serialNum);
    for (auto lid : lidsToRemove) {
        _docs.erase(lid);
    }
}


void
MyDocumentSubDB::handleRemove(RemoveOperationWithDocId &op)
{
    const SerialNum serialNum = op.getSerialNum();
    const DocumentId &docId = op.getDocumentId();
    const document::GlobalId &gid = op.getGlobalId();
    bool needCommit = false;

    if (op.getValidDbdId(_subDBId)) {
        using PutRes = DocumentMetaStore::Result;

        PutRes putRes(_metaStore.put(gid,
                                     op.getBucketId(),
                                     op.getTimestamp(),
                                     op.getSerializedDocSize(),
                                     op.getLid(), 0u));
        assert(putRes.ok());
        assert(op.getLid() == putRes._lid);
        const document::DocumentType *docType =
            _repo->getDocumentType(_docTypeName.getName());
        auto doc = std::make_unique<Document>(*_repo, *docType, docId);
        _docs[op.getLid()] = std::move(doc);
        needCommit = true;
    }
    if (op.getValidPrevDbdId(_subDBId) && op.changedDbdId()) {
        assert(_metaStore.validLid(op.getPrevLid()));
        const RawDocumentMetaData &meta(_metaStore.getRawMetaData(op.getPrevLid()));
        assert((_subDBId == 1u) == op.getPrevMarkedAsRemoved());
        assert(meta.getGid() == gid);
        (void) meta;

        bool remres = _metaStore.remove(op.getPrevLid(), 0u);
        assert(remres);
        (void) remres;

        _metaStore.removes_complete({ op.getPrevLid() });
        _docs.erase(op.getPrevLid());
        needCommit = true;
    }
    if (needCommit) {
        _metaStore.commit(CommitParam(serialNum));
    }
}


void
MyDocumentSubDB::prepareMove(MoveOperation &op)
{
    const DocumentId &docId = op.getDocument()->getId();
    const document::GlobalId &gid = docId.getGlobalId();
    DocumentMetaStore::Result inspectResult = _metaStore.inspect(gid, 0u);
    assert(!inspectResult._found);
    op.setDbDocumentId(DbDocumentId(_subDBId, inspectResult._lid));
}


void
MyDocumentSubDB::handleMove(const MoveOperation &op)
{
    const SerialNum serialNum = op.getSerialNum();
    const Document::SP &doc = op.getDocument();
    const DocumentId &docId = doc->getId();
    const document::GlobalId &gid = docId.getGlobalId();
    bool needCommit = false;

    if (op.getValidDbdId(_subDBId)) {
        using PutRes = DocumentMetaStore::Result;

        PutRes putRes(_metaStore.put(gid,
                                     op.getBucketId(),
                                     op.getTimestamp(),
                                     op.getSerializedDocSize(),
                                     op.getLid(), 0u));
        assert(putRes.ok());
        assert(op.getLid() == putRes._lid);
        _docs[op.getLid()] = doc;
        needCommit = true;
    }
    if (op.getValidPrevDbdId(_subDBId)) {
        assert(_metaStore.validLid(op.getPrevLid()));
        const RawDocumentMetaData &meta(_metaStore.getRawMetaData(op.getPrevLid()));
        assert((_subDBId == 1u) == op.getPrevMarkedAsRemoved());
        assert(meta.getGid() == gid);
        (void) meta;

        bool remres = _metaStore.remove(op.getPrevLid(), 0u);
        assert(remres);
        (void) remres;

        _metaStore.removes_complete({ op.getPrevLid() });
        _docs.erase(op.getPrevLid());
        needCommit = true;
    }
    if (needCommit) {
        _metaStore.commit(CommitParam(serialNum));
    }
}


uint32_t
MyDocumentSubDB::getNumUsedLids() const
{
    return _metaStore.getNumUsedLids();
}


MyFeedHandler::MyFeedHandler(std::thread::id executorThreadId)
    : IDocumentMoveHandler(),
      IPruneRemovedDocumentsHandler(),
      IHeartBeatHandler(),
      _executorThreadId(executorThreadId),
      _subDBs(),
      _serialNum(0u),
      _heartBeats(0u)
{
}


MyFeedHandler::~MyFeedHandler() = default;


bool
MyFeedHandler::isExecutorThread() const
{
    return (_executorThreadId == std::this_thread::get_id());
}


IDocumentMoveHandler::MoveResult
MyFeedHandler::handleMove(MoveOperation &op, IDestructorCallback::SP moveDoneCtx)
{
    assert(isExecutorThread());
    assert(op.getValidPrevDbdId());
    _subDBs[op.getSubDbId()]->prepareMove(op);
    assert(op.getValidDbdId());
    assert(op.getSubDbId() != op.getPrevSubDbId());
    // Check for wrong magic numbers
    assert(op.getSubDbId() != 1u);
    assert(op.getPrevSubDbId() != 1u);
    assert(op.getSubDbId() < _subDBs.size());
    assert(op.getPrevSubDbId() < _subDBs.size());
    appendOperation(op, std::move(moveDoneCtx));
    _subDBs[op.getSubDbId()]->handleMove(op);
    _subDBs[op.getPrevSubDbId()]->handleMove(op);
    return MoveResult::SUCCESS;
}


void
MyFeedHandler::performPruneRemovedDocuments(PruneRemovedDocumentsOperation &op)
{
    assert(isExecutorThread());
    if (op.getLidsToRemove()->getNumLids() != 0u) {
        appendOperation(op, std::make_shared<vespalib::IgnoreCallback>());
        // magic number.
        _subDBs[1u]->handlePruneRemovedDocuments(op);
    }
}


void
MyFeedHandler::heartBeat()
{
    assert(isExecutorThread());
    _heartBeats.store(_heartBeats.load(std::memory_order_relaxed) + 1, std::memory_order_relaxed);
}


void
MyFeedHandler::setSubDBs(const std::vector<MyDocumentSubDB *> &subDBs)
{
    _subDBs = subDBs;
}


void
MyFeedHandler::appendOperation(const FeedOperation &op, DoneCallback)
{
    const_cast<FeedOperation &>(op).setSerialNum(inc_serial_num());
}

MyExecutor::MyExecutor()
  : vespalib::ThreadStackExecutorBase(-1, my_executor_init),
    _threadId()
{
    start(1);
    execute(makeLambdaTask([this]() {
                sampleThreadId(&_threadId);
            }));
    sync();
}

MyExecutor::~MyExecutor()
{
    cleanup();
}

bool
MyExecutor::isIdle()
{
    (void) getStats();
    sync();
    auto stats = getStats();
    return stats.acceptedTasks == 0u;
}

bool
MyExecutor::waitIdle(vespalib::duration timeout)
{
    vespalib::Timer timer;
    while (!isIdle()) {
        if (timer.elapsed() >= timeout)
            return false;
    }
    return true;
}


}  // namespace

class MaintenanceControllerTest : public ::testing::Test
{
public:
    MyExecutor                         _executor;
    SyncableExecutorThreadService      _threadService;
    DummyBucketExecutor                _bucketExecutor;
    DocTypeName                        _docTypeName;
    test::UserDocumentsBuilder         _builder;
    std::shared_ptr<bucketdb::BucketDBOwner>     _bucketDB;
    test::BucketStateCalculator::SP    _calc;
    test::ClusterStateHandler          _clusterStateHandler;
    test::BucketHandler                _bucketHandler;
    MyBucketModifiedHandler            _bmc;
    MyDocumentSubDB                    _ready;
    MyDocumentSubDB                    _removed;
    MyDocumentSubDB                    _notReady;
    MyFeedHandler                      _fh;
    DocumentDBMaintenanceConfig::SP    _mcCfg;
    bool                               _injectDefaultJobs;
    DocumentDBJobTrackers              _jobTrackers;
    std::shared_ptr<proton::IAttributeManager> _readyAttributeManager;
    std::shared_ptr<proton::IAttributeManager> _notReadyAttributeManager;
    AttributeUsageFilter               _attributeUsageFilter;
    test::DiskMemUsageNotifier         _diskMemUsageNotifier;
    BucketCreateNotifier               _bucketCreateNotifier;
    MonitoredRefCount                  _refCount;
    Transport                          _transport;
    MaintenanceController              _mc;

    MaintenanceControllerTest();
    ~MaintenanceControllerTest() override;

    void syncSubDBs();
    void performSyncSubDBs();
    void notifyClusterStateChanged();
    void performNotifyClusterStateChanged();
    void startMaintenance();
    void injectMaintenanceJobs();
    void performStartMaintenance();
    void stopMaintenance();
    void forwardMaintenanceConfig();
    void performForwardMaintenanceConfig();

    void removeDocs(const test::UserDocuments &docs, Timestamp timestamp);

    void
    setPruneConfig(const DocumentDBPruneConfig &pruneConfig)
    {
        auto newCfg = std::make_shared<DocumentDBMaintenanceConfig>(
                           pruneConfig,
                           _mcCfg->getHeartBeatConfig(),
                           _mcCfg->getVisibilityDelay(),
                           _mcCfg->getLidSpaceCompactionConfig(),
                           _mcCfg->getAttributeUsageFilterConfig(),
                           _mcCfg->getAttributeUsageSampleInterval(),
                           _mcCfg->getBlockableJobConfig(),
                           _mcCfg->getFlushConfig(),
                           _mcCfg->getBucketMoveConfig());
        _mcCfg = newCfg;
        forwardMaintenanceConfig();
    }

    void
    setHeartBeatConfig(const DocumentDBHeartBeatConfig &heartBeatConfig)
    {
        auto newCfg = std::make_shared<DocumentDBMaintenanceConfig>(
                           _mcCfg->getPruneRemovedDocumentsConfig(),
                           heartBeatConfig,
                           _mcCfg->getVisibilityDelay(),
                           _mcCfg->getLidSpaceCompactionConfig(),
                           _mcCfg->getAttributeUsageFilterConfig(),
                           _mcCfg->getAttributeUsageSampleInterval(),
                           _mcCfg->getBlockableJobConfig(),
                           _mcCfg->getFlushConfig(),
                           _mcCfg->getBucketMoveConfig());
        _mcCfg = newCfg;
        forwardMaintenanceConfig();
    }

    void setLidSpaceCompactionConfig(const DocumentDBLidSpaceCompactionConfig &cfg) {
        auto newCfg = std::make_shared<DocumentDBMaintenanceConfig>(
                           _mcCfg->getPruneRemovedDocumentsConfig(),
                           _mcCfg->getHeartBeatConfig(),
                           _mcCfg->getVisibilityDelay(),
                           cfg,
                           _mcCfg->getAttributeUsageFilterConfig(),
                           _mcCfg->getAttributeUsageSampleInterval(),
                           _mcCfg->getBlockableJobConfig(),
                           _mcCfg->getFlushConfig(),
                           _mcCfg->getBucketMoveConfig());
        _mcCfg = newCfg;
        forwardMaintenanceConfig();
    }

    void
    performNotifyBucketStateChanged(document::BucketId bucketId, BucketInfo::ActiveState newState)
    {
        _bucketHandler.notifyBucketStateChanged(bucketId, newState);
    }

    void
    notifyBucketStateChanged(const document::BucketId &bucketId, BucketInfo::ActiveState newState)
    {
        _executor.execute(makeLambdaTask([&]() {
            performNotifyBucketStateChanged(bucketId, newState);
        }));
        _executor.sync();
    }
};

MaintenanceControllerTest::MaintenanceControllerTest()
    : _executor(),
      _threadService(_executor),
      _bucketExecutor(2),
      _docTypeName("searchdocument"), // must match document builder
      _builder(),
      _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
      _calc(new test::BucketStateCalculator()),
      _clusterStateHandler(),
      _bucketHandler(),
      _bmc(),
      _ready(0u, SubDbType::READY, _builder.getRepo(), _bucketDB, _docTypeName),
      _removed(1u, SubDbType::REMOVED, _builder.getRepo(), _bucketDB, _docTypeName),
      _notReady(2u, SubDbType::NOTREADY, _builder.getRepo(), _bucketDB, _docTypeName),
      _fh(_executor._threadId),
      _mcCfg(new DocumentDBMaintenanceConfig),
      _injectDefaultJobs(true),
      _jobTrackers(),
      _readyAttributeManager(std::make_shared<MyAttributeManager>()),
      _notReadyAttributeManager(std::make_shared<MyAttributeManager>()),
      _attributeUsageFilter(),
      _bucketCreateNotifier(),
      _refCount(),
      _transport(),
      _mc(_transport.transport(), _threadService, _refCount, _docTypeName)
{
    std::vector<MyDocumentSubDB *> subDBs;
    subDBs.push_back(&_ready);
    subDBs.push_back(&_removed);
    subDBs.push_back(&_notReady);
    _fh.setSubDBs(subDBs);
    syncSubDBs();
}

MaintenanceControllerTest::~MaintenanceControllerTest()
{
    stopMaintenance();
}

void
MaintenanceControllerTest::syncSubDBs()
{
    _executor.execute(makeLambdaTask([this]() { performSyncSubDBs(); }));
    _executor.sync();
}

void
MaintenanceControllerTest::performSyncSubDBs()
{
    _mc.syncSubDBs(_ready.getSubDB(), _removed.getSubDB(), _notReady.getSubDB());
}

void
MaintenanceControllerTest::notifyClusterStateChanged()
{
    _executor.execute(makeLambdaTask([this]() { performNotifyClusterStateChanged(); }));
    _executor.sync();
}

void
MaintenanceControllerTest::performNotifyClusterStateChanged()
{
    _clusterStateHandler.notifyClusterStateChanged(_calc);
}

void
MaintenanceControllerTest::startMaintenance()
{
    _executor.execute(makeLambdaTask([this]() { performStartMaintenance(); }));
    _executor.sync();
}

void
MaintenanceControllerTest::injectMaintenanceJobs()
{
    if (_injectDefaultJobs) {
        MaintenanceJobsInjector::injectJobs(_mc, *_mcCfg, _bucketExecutor, _fh, _fh,
                                            _bucketCreateNotifier, makeBucketSpace(), _fh, _fh,
                                            _bmc, _clusterStateHandler, _bucketHandler, _calc, _diskMemUsageNotifier,
                                            _jobTrackers, _readyAttributeManager, _notReadyAttributeManager,
                                            _attributeUsageFilter);
    }
}

void
MaintenanceControllerTest::performStartMaintenance()
{
    injectMaintenanceJobs();
    _mc.start();
}


void
MaintenanceControllerTest::stopMaintenance()
{
    _mc.stop();
    _executor.sync();
}


void
MaintenanceControllerTest::forwardMaintenanceConfig()
{
    _executor.execute(makeLambdaTask([this]() { performForwardMaintenanceConfig(); }));
    _executor.sync();
}


void
MaintenanceControllerTest::performForwardMaintenanceConfig()
{
    _mc.killJobs();
    injectMaintenanceJobs();
    _mc.newConfig();
}


void
MaintenanceControllerTest::removeDocs(const test::UserDocuments &docs, Timestamp timestamp)
{

    for (const auto & entry : docs) {
        const test::BucketDocuments &bucketDocs = entry.second;
        for (const test::Document &testDoc : bucketDocs.getDocs()) {
            RemoveOperationWithDocId op(testDoc.getBucket(), timestamp, testDoc.getDoc()->getId());
            op.setDbDocumentId(DbDocumentId(_removed.getSubDBId(), testDoc.getLid()));
            _fh.appendOperation(op, std::make_shared<vespalib::IgnoreCallback>());
            _removed.handleRemove(op);
        }
    }
}

TEST_F(MaintenanceControllerTest, require_that_document_pruner_is_active)
{
    uint64_t tshz = 1000000;
    uint64_t now = static_cast<uint64_t>(time(nullptr)) * tshz;
    Timestamp remTime(static_cast<Timestamp::Type>(now - 3600 * tshz));
    Timestamp keepTime(static_cast<Timestamp::Type>(now + 3600 * tshz));
    _builder.createDocs(1, 1, 4); // 3 docs
    _builder.createDocs(2, 4, 6); // 2 docs
    test::UserDocuments keepDocs(_builder.getDocs());
    removeDocs(keepDocs, keepTime);
    _builder.clearDocs();
    _builder.createDocs(3, 6, 8); // 2 docs
    _builder.createDocs(4, 8, 11); // 3 docs
    test::UserDocuments removeDocs(_builder.getDocs());
    this->removeDocs(removeDocs, remTime);
    notifyClusterStateChanged();
    EXPECT_TRUE(_executor.isIdle());
    EXPECT_EQ(10u, _removed.getNumUsedLids());
    EXPECT_EQ(10u, _removed.getDocumentCount());
    startMaintenance();
    ASSERT_TRUE(_executor.waitIdle(TIMEOUT));
    EXPECT_EQ(10u, _removed.getNumUsedLids());
    EXPECT_EQ(10u, _removed.getDocumentCount());
    setPruneConfig(DocumentDBPruneConfig(DELAY, 900s));
    for (uint32_t i = 0; i < 60000; ++i) {
        std::this_thread::sleep_for(1ms);
        ASSERT_TRUE(_executor.waitIdle(TIMEOUT));
        if (_removed.getNumUsedLids() != 10u)
            break;
    }
    _bucketExecutor.sync();
    _executor.sync();
    EXPECT_EQ(5u, _removed.getNumUsedLids());
    EXPECT_EQ(5u, _removed.getDocumentCount());
}

TEST_F(MaintenanceControllerTest, require_that_heartbeats_are_scheduled)
{
    notifyClusterStateChanged();
    startMaintenance();
    setHeartBeatConfig(DocumentDBHeartBeatConfig(DELAY));
    for (uint32_t i = 0; i < 60000; ++i) {
        std::this_thread::sleep_for(1ms);
        if (_fh.getHeartBeats() != 0u)
            break;
    }
    EXPECT_GT(_fh.getHeartBeats(), 0u);
}

TEST_F(MaintenanceControllerTest, require_that_a_simple_maintenance_job_is_executed)
{
    auto job = std::make_unique<MySimpleJob>(DELAY, DELAY, 3);
    MySimpleJob &myJob = *job;
    _mc.registerJob(std::move(job));
    _injectDefaultJobs = false;
    startMaintenance();
    bool done = myJob._latch.await(TIMEOUT);
    EXPECT_TRUE(done);
    EXPECT_EQ(0u, myJob._latch.getCount());
}

TEST_F(MaintenanceControllerTest, require_that_a_split_maintenance_job_is_executed)
{
    auto job = std::make_unique<MySplitJob>(DELAY, TIMEOUT * 2, 3);
    MySplitJob &myJob = *job;
    _mc.registerJob(std::move(job));
    _injectDefaultJobs = false;
    startMaintenance();
    bool done = myJob._latch.await(TIMEOUT);
    EXPECT_TRUE(done);
    EXPECT_EQ(0u, myJob._latch.getCount());
}

TEST_F(MaintenanceControllerTest, require_that_blocked_jobs_are_not_executed)
{
    auto job = std::make_unique<MySimpleJob>(DELAY, DELAY, 0);
    MySimpleJob &myJob = *job;
    myJob.block();
    _mc.registerJob(std::move(job));
    _injectDefaultJobs = false;
    startMaintenance();
    for (uint32_t napCount = 0; (myJob._runCnt != 0) && (napCount < 200); napCount++) {
        std::this_thread::sleep_for(10ms);
    }
    EXPECT_EQ(0u, myJob._runCnt);
}

TEST_F(MaintenanceControllerTest, require_that_maintenance_controller_state_list_jobs)
{
    {
        auto job1 = std::make_unique<MySimpleJob>(TIMEOUT * 2, TIMEOUT * 2, 0);
        auto job2 = std::make_unique<MyLongRunningJob>(DELAY, DELAY);
        auto &longRunningJob = dynamic_cast<MyLongRunningJob &>(*job2);
        _mc.registerJob(std::move(job1));
        _mc.registerJob(std::move(job2));
        _injectDefaultJobs = false;
        startMaintenance();
        longRunningJob._firstRun.await(TIMEOUT);
    }

    MaintenanceControllerExplorer explorer(_mc.getJobList());
    Slime state;
    SlimeInserter inserter(state);
    explorer.get_state(inserter, true);

    Inspector &runningJobs = state.get()["runningJobs"];
    EXPECT_EQ(1u, runningJobs.children());
    EXPECT_EQ("long_running_job", runningJobs[0]["name"].asString().make_string());

    Inspector &allJobs = state.get()["allJobs"];
    EXPECT_EQ(2u, allJobs.children());
    EXPECT_EQ("my_job", allJobs[0]["name"].asString().make_string());
    EXPECT_EQ("long_running_job", allJobs[1]["name"].asString().make_string());
}

namespace {

const MaintenanceJobRunner *
findJob(const MaintenanceController::JobList &jobs, const std::string &jobName)
{
    auto itr = std::find_if(jobs.begin(), jobs.end(),
                            [&](const auto &job){ return job->getJob().getName() == jobName; });
    if (itr != jobs.end()) {
        return itr->get();
    }
    return nullptr;
}

bool
containsJob(const MaintenanceController::JobList &jobs, const std::string &jobName)
{
    return findJob(jobs, jobName) != nullptr;
}

}

TEST_F(MaintenanceControllerTest, require_that_lid_space_compaction_jobs_can_be_disabled)
{
    forwardMaintenanceConfig();
    {
        auto jobs = _mc.getJobList();
        EXPECT_EQ(7u, jobs.size());
        EXPECT_TRUE(containsJob(jobs, "lid_space_compaction.searchdocument.my_sub_db"));
    }
    setLidSpaceCompactionConfig(DocumentDBLidSpaceCompactionConfig::createDisabled());
    {
        auto jobs = _mc.getJobList();
        EXPECT_EQ(4u, jobs.size());
        EXPECT_FALSE(containsJob(jobs, "lid_space_compaction.searchdocument.my_sub_db"));
    }
}

void
assertPruneRemovedDocumentsConfig(vespalib::duration expDelay, vespalib::duration expInterval, vespalib::duration interval, MaintenanceControllerTest &f)
{
    SCOPED_TRACE(std::to_string(vespalib::to_s(interval)));
    f.setPruneConfig(DocumentDBPruneConfig(interval, 1000s));
    const auto *job = findJob(f._mc.getJobList(), "prune_removed_documents.searchdocument");
    EXPECT_EQ(expDelay, job->getJob().getDelay());
    EXPECT_EQ(expInterval, job->getJob().getInterval());
}

TEST_F(MaintenanceControllerTest, require_that_delay_for_prune_removed_documents_is_set_based_on_interval_and_is_max_300_secs)
{
    assertPruneRemovedDocumentsConfig(300s, 301s, 301s, *this);
    assertPruneRemovedDocumentsConfig(299s, 299s, 299s, *this);
}
