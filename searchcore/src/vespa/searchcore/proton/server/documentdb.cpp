// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "combiningfeedview.h"
#include "commit_and_wait_document_retriever.h"
#include "document_meta_store_read_guards.h"
#include "document_subdb_collection_explorer.h"
#include "documentdb.h"
#include "documentdbconfigscout.h"
#include "idocumentdbowner.h"
#include "lid_space_compaction_handler.h"
#include "maintenance_jobs_injector.h"
#include "reconfig_params.h"
#include "bootstrapconfig.h"
#include "executor_threading_service_stats.h"
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcore/proton/common/statusreport.h>
#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/searchcore/proton/initializer/task_runner.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics_collection.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_registry.h>
#include <vespa/searchcore/proton/reference/document_db_reference_resolver.h>
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.documentdb");

using vespa::config::search::AttributesConfig;
using vespa::config::search::core::ProtonConfig;
using search::index::SchemaBuilder;
using vespalib::JSONStringer;
using vespalib::Executor;
using vespalib::IllegalStateException;
using vespalib::StateExplorer;
using vespalib::make_string;
using namespace proton::matching;
using namespace search;
using namespace search::engine;
using namespace search::fef;
using namespace search::index;
using namespace search::transactionlog;
using searchcorespi::index::IThreadService;
using search::TuneFileDocumentDB;
using storage::spi::Timestamp;
using search::common::FileHeaderContext;
using proton::initializer::InitializerTask;
using proton::initializer::TaskRunner;
using vespalib::makeLambdaTask;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {
constexpr uint32_t indexing_thread_stack_size = 128 * 1024;
}

template <typename FunctionType>
void
DocumentDB::masterExecute(FunctionType &&function) {
    _writeService.master().execute(makeLambdaTask(std::forward<FunctionType>(function)));
}

DocumentDB::DocumentDB(const vespalib::string &baseDir,
                       const DocumentDBConfig::SP &configSnapshot,
                       const vespalib::string &tlsSpec,
                       matching::QueryLimiter &queryLimiter,
                       const vespalib::Clock &clock,
                       const DocTypeName &docTypeName,
                       document::BucketSpace bucketSpace,
                       const ProtonConfig &protonCfg,
                       IDocumentDBOwner &owner,
                       vespalib::ThreadExecutor &warmupExecutor,
                       vespalib::ThreadStackExecutorBase &summaryExecutor,
                       search::transactionlog::Writer &tlsDirectWriter,
                       MetricsWireService &metricsWireService,
                       const FileHeaderContext &fileHeaderContext,
                       ConfigStore::UP config_store,
                       InitializeThreads initializeThreads,
                       const HwInfo &hwInfo)
    : IDocumentDBConfigOwner(),
      IReplayConfig(),
      IFeedHandlerOwner(),
      IDocumentSubDBOwner(),
      IClusterStateChangedHandler(),
      search::transactionlog::SyncProxy(),
      _docTypeName(docTypeName),
      _bucketSpace(bucketSpace),
      _baseDir(baseDir + "/" + _docTypeName.toString()),
      // Only one thread per executor, or performDropFeedView() will fail.
      _writeServiceConfig(ThreadingServiceConfig::make(protonCfg, hwInfo.cpu())),
      _writeService(_writeServiceConfig.indexingThreads(),
                    indexing_thread_stack_size,
                    _writeServiceConfig.defaultTaskLimit()),
      _initializeThreads(initializeThreads),
      _initConfigSnapshot(),
      _initConfigSerialNum(0u),
      _pendingConfigSnapshot(configSnapshot),
      _configMutex(),
      _configCV(),
      _activeConfigSnapshot(),
      _activeConfigSnapshotGeneration(0),
      _activeConfigSnapshotSerialNum(0u),
      _initGate(),
      _clusterStateHandler(_writeService.master()),
      _bucketHandler(_writeService.master()),
      _protonIndexCfg(protonCfg.index),
      _config_store(std::move(config_store)),
      _sessionManager(new matching::SessionManager(protonCfg.grouping.sessionmanager.maxentries)),
      _metricsWireService(metricsWireService),
      _metricsHook(*this, _docTypeName.getName(), protonCfg.numthreadspersearch),
      _feedView(),
      _refCount(),
      _syncFeedViewEnabled(false),
      _owner(owner),
      _state(),
      _dmUsageForwarder(_writeService.master()),
      _writeFilter(),
      _feedHandler(_writeService, tlsSpec, docTypeName, _state, *this, _writeFilter, *this, tlsDirectWriter),
      _subDBs(*this, *this, _feedHandler, _docTypeName, _writeService, warmupExecutor,
              summaryExecutor, fileHeaderContext, metricsWireService, getMetricsCollection(),
              queryLimiter, clock, _configMutex, _baseDir, protonCfg, hwInfo),
      _maintenanceController(_writeService.master(), summaryExecutor, _docTypeName),
      _visibility(_feedHandler, _writeService, _feedView),
      _lidSpaceCompactionHandlers(),
      _jobTrackers(),
      _lastDocStoreCacheStats(),
      _calc()
{
    assert(configSnapshot);

    LOG(debug, "DocumentDB(%s): Creating database in directory '%s'",
        _docTypeName.toString().c_str(), _baseDir.c_str());

    _feedHandler.init(_config_store->getOldestSerialNum());
    _feedHandler.setBucketDBHandler(&_subDBs.getBucketDBHandler());
    saveInitialConfig(*configSnapshot);
    resumeSaveConfig();
    SerialNum configSerial = _config_store->getPrevValidSerial(_feedHandler.getPrunedSerialNum() + 1);
    assert(configSerial > 0);
    DocumentDBConfig::SP loaded_config;
    _config_store->loadConfig(*configSnapshot, configSerial, loaded_config);
    // Grab relevant parts from pending config
    loaded_config = DocumentDBConfigScout::scout(loaded_config, *_pendingConfigSnapshot.get());
    // Ignore configs that are not relevant during replay of transaction log
    loaded_config = DocumentDBConfig::makeReplayConfig(loaded_config);

    _initConfigSnapshot = loaded_config;
    _initConfigSerialNum = configSerial;
    // Forward changes of cluster state to feed view via us
    _clusterStateHandler.addClusterStateChangedHandler(this);
    // Forward changes of cluster state to bucket handler
    _clusterStateHandler.addClusterStateChangedHandler(&_bucketHandler);
    for (auto subDb : _subDBs) {
        _lidSpaceCompactionHandlers.push_back(ILidSpaceCompactionHandler::UP
                (new LidSpaceCompactionHandler(*subDb, _docTypeName.getName())));
    }
    _writeFilter.setConfig(loaded_config->getMaintenanceConfigSP()->getAttributeUsageFilterConfig());
    fastos::TimeStamp visibilityDelay = loaded_config->getMaintenanceConfigSP()->getVisibilityDelay();
    _visibility.setVisibilityDelay(visibilityDelay);
    if (_visibility.getVisibilityDelay() > 0) {
        _writeService.setTaskLimit(_writeServiceConfig.semiUnboundTaskLimit(), _writeServiceConfig.defaultTaskLimit());
    }
}

void DocumentDB::registerReference()
{
    if (_state.getAllowReconfig()) {
        auto registry = _owner.getDocumentDBReferenceRegistry();
        if (registry) {
            auto reference = _subDBs.getReadySubDB()->getDocumentDBReference();
            if (reference) {
                registry->add(_docTypeName.getName(), reference);
            }
        }
    }
}

void DocumentDB::setActiveConfig(const DocumentDBConfig::SP &config,
                                 SerialNum serialNum, int64_t generation) {
    lock_guard guard(_configMutex);
    registerReference();
    _activeConfigSnapshot = config;
    assert(generation >= config->getGeneration());
    if (_activeConfigSnapshotGeneration < generation) {
        _activeConfigSnapshotGeneration = generation;
    }
    _activeConfigSnapshotSerialNum = serialNum;
    _configCV.notify_all();
}

DocumentDBConfig::SP DocumentDB::getActiveConfig() const {
    lock_guard guard(_configMutex);
    return _activeConfigSnapshot;
}

void
DocumentDB::internalInit()
{
    (void) _state.enterLoadState();
    masterExecute([this]() { initManagers(); });
}

class InitDoneTask : public vespalib::Executor::Task {
    DocumentDB::InitializeThreads _initializeThreads;
    std::shared_ptr<TaskRunner>   _taskRunner;
    DocumentDBConfig::SP          _configSnapshot;
    DocumentDB&                   _self;
public:
    InitDoneTask(DocumentDB::InitializeThreads initializeThreads,
                 std::shared_ptr<TaskRunner> taskRunner,
                 DocumentDBConfig::SP configSnapshot,
                 DocumentDB& self)
        : _initializeThreads(std::move(initializeThreads)),
          _taskRunner(std::move(taskRunner)),
          _configSnapshot(std::move(configSnapshot)),
          _self(self)
    {
    }

    ~InitDoneTask();

    void run() override {
        _self.initFinish(std::move(_configSnapshot));
    }
};

InitDoneTask::~InitDoneTask() = default;

void
DocumentDB::initManagers()
{
    // Called by executor thread
    DocumentDBConfig::SP configSnapshot(_initConfigSnapshot);
    _initConfigSnapshot.reset();
    InitializerTask::SP rootTask =
        _subDBs.createInitializer(*configSnapshot, _initConfigSerialNum, _protonIndexCfg);
    InitializeThreads initializeThreads = _initializeThreads;
    _initializeThreads.reset();
    std::shared_ptr<TaskRunner> taskRunner(std::make_shared<TaskRunner>(*initializeThreads));
    auto doneTask = std::make_unique<InitDoneTask>(std::move(initializeThreads), taskRunner,
                                                   std::move(configSnapshot), *this);
    taskRunner->runTask(rootTask, _writeService.master(), std::move(doneTask));
}

void
DocumentDB::initFinish(DocumentDBConfig::SP configSnapshot)
{
    // Called by executor thread
    _bucketHandler.setReadyBucketHandler(_subDBs.getReadySubDB()->getDocumentMetaStoreContext().get());
    _subDBs.initViews(*configSnapshot, _sessionManager);
    _syncFeedViewEnabled = true;
    syncFeedView();
    // Check that feed view has been activated.
    assert(_feedView.get().get() != NULL);
    setActiveConfig(configSnapshot, _initConfigSerialNum, configSnapshot->getGeneration());
    startTransactionLogReplay();
}


void
DocumentDB::newConfigSnapshot(DocumentDBConfig::SP snapshot)
{
    // Called by executor thread
    _pendingConfigSnapshot.set(snapshot);
    {
        lock_guard guard(_configMutex);
        if ( ! _activeConfigSnapshot) {
            LOG(debug,
                "DocumentDB(%s): Ignoring new available config snapshot. "
                "The document database does not have"
                " an active config snapshot yet", _docTypeName.toString().c_str());
            return;
        }
        if (!_state.getAllowReconfig()) {
            LOG(warning,
                "DocumentDB(%s): Ignoring new available config snapshot. "
                "The document database is not allowed to"
                " reconfigure yet. Wait until replay is done before"
                " you try to reconfigure again", _docTypeName.toString().c_str());
            return;
        }
    }
    masterExecute([this] () { performReconfig(_pendingConfigSnapshot.get()); } );
}


void
DocumentDB::enterReprocessState()
{
    // Called by executor thread
    assert(_writeService.master().isCurrentThread());
    if (!_state.enterReprocessState()) {
        return;
    }
    ReprocessingRunner &runner = _subDBs.getReprocessingRunner();
    if (!runner.empty()) {
        runner.run();
        NoopOperation op;
        _feedHandler.storeOperationSync(op);
        sync(op.getSerialNum());
        _subDBs.pruneRemovedFields(op.getSerialNum());
    }
    _subDBs.onReprocessDone(_feedHandler.getSerialNum());
    enterOnlineState();
}


void
DocumentDB::enterOnlineState()
{
    // Called by executor thread
    // Ensure that all replayed operations are committed to memory structures
    _feedView.get()->forceCommit(_feedHandler.getSerialNum());
    _writeService.sync();

    (void) _state.enterOnlineState();
    // Consider delayed pruning of transaction log and config history
    _feedHandler.considerDelayedPrune();
    performStartMaintenance();
}

void
DocumentDB::performReconfig(DocumentDBConfig::SP configSnapshot)
{
    // Called by executor thread
    applyConfig(std::move(configSnapshot), getCurrentSerialNumber());
    if (_state.getState() == DDBState::State::APPLY_LIVE_CONFIG) {
        enterReprocessState();
    }
}


void
DocumentDB::applySubDBConfig(const DocumentDBConfig &newConfigSnapshot,
                             SerialNum serialNum, const ReconfigParams &params)
{
    auto registry = _owner.getDocumentDBReferenceRegistry();
    auto oldRepo = _activeConfigSnapshot->getDocumentTypeRepoSP();
    auto oldDocType = oldRepo->getDocumentType(_docTypeName.getName());
    assert(oldDocType != nullptr);
    auto newRepo = newConfigSnapshot.getDocumentTypeRepoSP();
    auto newDocType = newRepo->getDocumentType(_docTypeName.getName());
    assert(newDocType != nullptr);
    DocumentDBReferenceResolver resolver(*registry, *newDocType, newConfigSnapshot.getImportedFieldsConfig(),
                                         *oldDocType, _refCount, _writeService.attributeFieldWriter(), _state.getAllowReconfig());
    _subDBs.applyConfig(newConfigSnapshot, *_activeConfigSnapshot, serialNum, params, resolver);
}

void
DocumentDB::applyConfig(DocumentDBConfig::SP configSnapshot, SerialNum serialNum)
{
    // Always called by executor thread:
    // Called by performReconfig() by executor thread during normal
    // feed mode and when switching to normal feed mode after replay.
    // Called by replayConfig() in visitor callback by executor thread
    // when using config from transaction log.
    if (_state.getClosed()) {
        LOG(error, "Applying config to closed document db");
        return;
    }
    ConfigComparisonResult cmpres;
    Schema::SP oldSchema;
    int64_t generation = configSnapshot->getGeneration();
    {
        lock_guard guard(_configMutex);
        assert(_activeConfigSnapshot.get());
        if (_state.getState() >= DDBState::State::ONLINE) {
            configSnapshot = DocumentDBConfig::makeDelayedAttributeAspectConfig(configSnapshot, *_activeConfigSnapshot);
        }
        if (configSnapshot->getDelayedAttributeAspects()) {
            _state.setConfigState(DDBState::ConfigState::NEED_RESTART);
            LOG(info, "DocumentDB(%s): Delaying attribute aspect changes: need restart",
                _docTypeName.toString().c_str());
        }
        cmpres = _activeConfigSnapshot->compare(*configSnapshot);
    }
    if (_state.getState() == DDBState::State::APPLY_LIVE_CONFIG) {
        cmpres.importedFieldsChanged = true;
    }
    const ReconfigParams params(cmpres);
    // Save config via config manager if replay is done.
    bool equalReplayConfig =
        *DocumentDBConfig::makeReplayConfig(configSnapshot) ==
        *DocumentDBConfig::makeReplayConfig(_activeConfigSnapshot);
    bool tlsReplayDone = _feedHandler.getTransactionLogReplayDone();
    if (!equalReplayConfig && tlsReplayDone) {
        sync(_feedHandler.getSerialNum());
        serialNum = _feedHandler.incSerialNum();
        _config_store->saveConfig(*configSnapshot, serialNum);
        // save entry in transaction log
        NewConfigOperation op(serialNum, *_config_store);
        _feedHandler.storeOperationSync(op);
        sync(op.getSerialNum());
    }
    bool hasVisibilityDelayChanged = false;
    {
        bool elidedConfigSave = equalReplayConfig && tlsReplayDone;
        // Flush changes to attributes and memory index, cf. visibilityDelay
        _feedView.get()->forceCommit(elidedConfigSave ? serialNum : serialNum - 1);
        _writeService.sync();
        fastos::TimeStamp visibilityDelay = configSnapshot->getMaintenanceConfigSP()->getVisibilityDelay();
        hasVisibilityDelayChanged = (visibilityDelay != _visibility.getVisibilityDelay());
        _visibility.setVisibilityDelay(visibilityDelay);
    }
    if (_visibility.getVisibilityDelay() > 0) {
        _writeService.setTaskLimit(_writeServiceConfig.semiUnboundTaskLimit(), _writeServiceConfig.defaultTaskLimit());
    } else {
        _writeService.setTaskLimit(_writeServiceConfig.defaultTaskLimit(), _writeServiceConfig.defaultTaskLimit());
    }
    if (params.shouldSubDbsChange() || hasVisibilityDelayChanged) {
        applySubDBConfig(*configSnapshot, serialNum, params);
        if (serialNum < _feedHandler.getSerialNum()) {
            // Not last entry in tls.  Reprocessing should already be done.
            _subDBs.getReprocessingRunner().reset();
        }
        if (_state.getState() == DDBState::State::ONLINE) {
            // Changes applied while online should not trigger reprocessing
            assert(_subDBs.getReprocessingRunner().empty());
        }
    }
    if (params.shouldIndexManagerChange()) {
        setIndexSchema(*configSnapshot, serialNum);
    }
    if (!configSnapshot->getDelayedAttributeAspects()) {
        if (_state.getDelayedConfig()) {
            LOG(info, "DocumentDB(%s): Stopped delaying attribute aspect changes",
                _docTypeName.toString().c_str());
        }
        _state.clearDelayedConfig();
    }
    setActiveConfig(configSnapshot, serialNum, generation);
    if (params.shouldMaintenanceControllerChange()) {
        forwardMaintenanceConfig();
    }
    _writeFilter.setConfig(configSnapshot->getMaintenanceConfigSP()->getAttributeUsageFilterConfig());
    if (_subDBs.getReprocessingRunner().empty()) {
        _subDBs.pruneRemovedFields(serialNum);
    }
}


namespace {
void
doNothing(IFeedView::SP)
{
    // Called by index executor, delays when feed view is dropped.
}
}  // namespace

void
DocumentDB::performDropFeedView(IFeedView::SP feedView)
{
    // Called by executor task, delays when feed view is dropped.
    // Also called by DocumentDB::receive() method to keep feed view alive

    _writeService.attributeFieldWriter().sync();
    _writeService.summary().sync();

    // Feed view is kept alive in the closure's shared ptr.
    _writeService.index().execute(makeLambdaTask([this, feedView] () { performDropFeedView2(feedView); }));
}


void
DocumentDB::performDropFeedView2(IFeedView::SP feedView)
{
    // Called by executor task, delays when feed view is dropped.
    // Also called by DocumentDB::receive() method to keep feed view alive
    _writeService.indexFieldInverter().sync();
    _writeService.indexFieldWriter().sync();

    // Feed view is kept alive in the closure's shared ptr.
    masterExecute([feedView] () { doNothing(feedView); });
}


void
DocumentDB::tearDownReferences()
{
    // Called by master executor thread
    auto registry = _owner.getDocumentDBReferenceRegistry();
    auto activeConfig = getActiveConfig();
    auto repo = activeConfig->getDocumentTypeRepoSP();
    auto docType = repo->getDocumentType(_docTypeName.getName());
    assert(docType != nullptr);
    DocumentDBReferenceResolver resolver(*registry,
                                         *docType,
                                         activeConfig->getImportedFieldsConfig(),
                                         *docType,
                                         _refCount,
                                         _writeService.attributeFieldWriter(),
                                         false);
    _subDBs.tearDownReferences(resolver);
    registry->remove(_docTypeName.getName());
}

void
DocumentDB::close()
{
    {
        lock_guard guard(_configMutex);
        _state.enterShutdownState();
        _configCV.notify_all();
    }
    _writeService.master().sync(); // Complete all tasks that didn't observe shutdown
    masterExecute([this]() { tearDownReferences(); });
    _writeService.master().sync();
    // Wait until inflight feed operations to this document db has left.
    // Caller should have removed document DB from feed router.
    _refCount.waitForZeroRefCount();
    // Abort any ongoing maintenance
    stopMaintenance();

    // The attributes in the ready sub db is also the total set of attributes.
    DocumentDBTaggedMetrics &metrics = getMetricsCollection().getTaggedMetrics();
    LegacyDocumentDBMetrics &legacyMetrics = getMetricsCollection().getLegacyMetrics();
    AttributeMetricsCollection ready(metrics.ready.attributes, legacyMetrics.ready.attributes);
    AttributeMetricsCollection notReady(metrics.notReady.attributes, legacyMetrics.notReady.attributes);
    _metricsWireService.cleanAttributes(ready, &legacyMetrics.attributes);
    _metricsWireService.cleanAttributes(notReady, NULL);
    _writeService.sync();
    masterExecute([this] () { closeSubDBs(); } );
    _writeService.sync();
    // What about queued tasks ?
    _writeService.shutdown();
    _maintenanceController.kill();
    _feedHandler.close();
    // Assumes that feed engine has been closed.  If only this document DB
    // is going away while system is still up and running then caller must
    // ensure that routing has been torn down and pending messages have been
    // drained.  This goes for all facets: feeding, tls replay,
    // matching, summary fetch, flushing and reconfig.
    _feedView.clear();
    _subDBs.clearViews();
    _sessionManager->close();
    _state.enterDeadState();
}

DocumentDB::~DocumentDB()
{
    close();
    // Remove forwarding of cluster state change
    _clusterStateHandler.removeClusterStateChangedHandler(&_bucketHandler);
    _clusterStateHandler.removeClusterStateChangedHandler(this);
    
}

void
DocumentDB::closeSubDBs()
{
    _subDBs.close();
}

size_t
DocumentDB::getNumDocs() const
{
    return _subDBs.getReadySubDB()->getNumDocs();
}

size_t
DocumentDB::getNumActiveDocs() const
{
    return _subDBs.getReadySubDB()->getNumActiveDocs();
}

void
DocumentDB::saveInitialConfig(const DocumentDBConfig &configSnapshot)
{
    // Only called from ctor

    lock_guard guard(_configMutex);
    if (_config_store->getBestSerialNum() != 0)
        return;             // Initial config already present

    SerialNum confSerial = _feedHandler.incSerialNum();
    // Elide save of new config entry in transaction log, it would be
    // pruned at once anyway.
    // save noop entry in transaction log
    NoopOperation op;
    _feedHandler.storeOperationSync(op);
    sync(op.getSerialNum());
    // Wipe everything in transaction log before initial config.
    try {
        _feedHandler.tlsPrune(confSerial);  // throws on error
    } catch (const vespalib::IllegalStateException & e) {
        LOG(warning, "DocumentDB(%s): saveInitialConfig() failed pruning due to '%s'",
            _docTypeName.toString().c_str(), e.what());
    }
    _config_store->saveConfig(configSnapshot, confSerial);
}

void
DocumentDB::resumeSaveConfig()
{
    SerialNum bestSerial = _config_store->getBestSerialNum();
    if (bestSerial == 0)
        return;
    if (bestSerial != _feedHandler.getSerialNum() + 1)
        return;
    // proton was interrupted when saving later config.
    SerialNum confSerial = _feedHandler.incSerialNum();
    // resume operation, i.e. save config entry in transaction log
    NewConfigOperation op(confSerial, *_config_store);
    _feedHandler.storeOperationSync(op);
    sync(op.getSerialNum());
}


void
DocumentDB::onTransactionLogReplayDone()
{
    // Called by executor thread
    _subDBs.onReplayDone();
    if (!_owner.isInitializing()) {
        // This document db is added when system is up,
        // must signal that all existing buckets must be checked.
        notifyAllBucketsChanged();
    }
}


void
DocumentDB::onPerformPrune(SerialNum flushedSerial)
{
    if (!getAllowPrune()) {
        assert(_state.getClosed());
        return;
    }
    _config_store->prune(flushedSerial);
}

bool
DocumentDB::getAllowPrune() const
{
    return _state.getAllowPrune();
}

void
DocumentDB::start()
{
    LOG(debug,
        "DocumentDB(%s): Database starting.",
        _docTypeName.toString().c_str());

    internalInit();
}

void
DocumentDB::waitForInitDone()
{
    _initGate.await();
}

void
DocumentDB::startTransactionLogReplay()
{
    // This configSnapshot is only used to reuse DocumentTypeRepo
    // and TuneFile when loading configs during replay.
    DocumentDBConfig::SP configSnapshot = getActiveConfig();
    IDocumentSubDB *readySubDB = _subDBs.getReadySubDB();
    SerialNum oldestFlushedSerial = getOldestFlushedSerial();
    SerialNum newestFlushedSerial = getNewestFlushedSerial();
    (void) _state.enterReplayTransactionLogState();
    _feedHandler.replayTransactionLog(readySubDB->getIndexManager()->
                                      getFlushedSerialNum(),
                                      readySubDB->getSummaryManager()->
                                      getBackingStore().lastSyncToken(),
                                      oldestFlushedSerial,
                                      newestFlushedSerial,
                                      *_config_store);
    _initGate.countDown();

    LOG(debug, "DocumentDB(%s): Database started.", _docTypeName.toString().c_str());
}

BucketGuard::UP DocumentDB::lockBucket(const document::BucketId &bucket)
{
    BucketGuard::UP guard(std::make_unique<BucketGuard>(bucket, _maintenanceController));
    return std::move(guard);
}

std::shared_ptr<std::vector<IDocumentRetriever::SP> >
DocumentDB::getDocumentRetrievers(IDocumentRetriever::ReadConsistency consistency)
{
    std::shared_ptr<std::vector<IDocumentRetriever::SP> > list = _subDBs.getRetrievers();

    if (consistency == IDocumentRetriever::ReadConsistency::STRONG) {
        std::shared_ptr<std::vector<IDocumentRetriever::SP> > wrappedList = std::make_shared<std::vector<IDocumentRetriever::SP>>();
        wrappedList->reserve(list->size());
        for (const IDocumentRetriever::SP & retriever : *list) {
            wrappedList->emplace_back(new CommitAndWaitDocumentRetriever(retriever, _visibility));
        }
        return wrappedList;
    } else {
        return list;
    }
}

SerialNum
DocumentDB::getOldestFlushedSerial()
{
    return _subDBs.getOldestFlushedSerial();
}

SerialNum
DocumentDB::getNewestFlushedSerial()
{
    return _subDBs.getNewestFlushedSerial();
}

std::unique_ptr<SearchReply>
DocumentDB::match(const ISearchHandler::SP &, const SearchRequest &req, vespalib::ThreadBundle &threadBundle) const
{
    // Ignore input searchhandler. Use readysubdb's searchhandler instead.
    ISearchHandler::SP view(_subDBs.getReadySubDB()->getSearchView());
    return view->match(view, req, threadBundle);
}

std::unique_ptr<DocsumReply>
DocumentDB::getDocsums(const DocsumRequest & request)
{
    ISearchHandler::SP view(_subDBs.getReadySubDB()->getSearchView());
    return view->getDocsums(request);
}

IFlushTarget::List
DocumentDB::getFlushTargets()
{
    IFlushTarget::List flushTargets = _subDBs.getFlushTargets();
    return _jobTrackers.trackFlushTargets(flushTargets);
}

void
DocumentDB::flushDone(SerialNum flushedSerial)
{
    _feedHandler.flushDone(flushedSerial);
}

void
DocumentDB::setIndexSchema(const DocumentDBConfig &configSnapshot, SerialNum serialNum)
{
    // Called by executor thread
    _subDBs.getReadySubDB()->setIndexSchema(configSnapshot.getSchemaSP(), serialNum);

    // TODO: Adjust tune.
}

void
DocumentDB::reconfigure(const DocumentDBConfig::SP & snapshot)
{
    masterExecute([this, snapshot]() { newConfigSnapshot(snapshot); });
    // Wait for config to be applied, or for document db close
    std::unique_lock<std::mutex> guard(_configMutex);
    while ((_activeConfigSnapshotGeneration < snapshot->getGeneration()) && !_state.getClosed()) {
        _configCV.wait(guard);
    }
}

void
DocumentDB::enterRedoReprocessState()
{
    assert(_writeService.master().isCurrentThread());
    ReprocessingRunner &runner = _subDBs.getReprocessingRunner();
    if (!runner.empty()) {
        if (!_state.enterRedoReprocessState()) {
            return;
        }
        runner.run();
        _subDBs.onReprocessDone(_feedHandler.getSerialNum());
        NoopOperation op;
        _feedHandler.storeOperationSync(op);
        sync(op.getSerialNum());
        _subDBs.pruneRemovedFields(op.getSerialNum());
    }
    enterApplyLiveConfigState();
}


void
DocumentDB::enterApplyLiveConfigState()
{
    assert(_writeService.master().isCurrentThread());
    // Enable reconfig and queue currently pending config as executor task.
    {
        lock_guard guard(_configMutex);
        (void) _state.enterApplyLiveConfigState();
    }
    masterExecute([this]() { performReconfig(_pendingConfigSnapshot.get()); });
}


StatusReport::UP
DocumentDB::reportStatus() const
{
    StatusReport::Params params("documentdb:" + _docTypeName.toString());
    const DDBState::State rawState = _state.getState();
    {
        const vespalib::string state(DDBState::getStateString(rawState));
        const vespalib::string configState(DDBState::getConfigStateString(_state.getConfigState()));
        params.internalState(state).internalConfigState(configState);
    }

    if (_initGate.getCount() != 0) {
        return StatusReport::create(params.state(StatusReport::PARTIAL).
                message("DocumentDB initializing components"));
    } else if (_feedHandler.isDoingReplay()) {
        float progress = _feedHandler.getReplayProgress() * 100.0f;
        vespalib::string msg = vespalib::make_string("DocumentDB replay transaction log on startup (%u%% done)",
                static_cast<uint32_t>(progress));
        return StatusReport::create(params.state(StatusReport::PARTIAL).progress(progress).message(msg));
    } else if (rawState == DDBState::State::APPLY_LIVE_CONFIG) {
        return StatusReport::create(params.state(StatusReport::PARTIAL)
                                          .message("DocumentDB apply live config on startup"));
    } else if (rawState == DDBState::State::REPROCESS ||
               rawState == DDBState::State::REDO_REPROCESS)
    {
        float progress = _subDBs.getReprocessingProgress() * 100.0f;
        vespalib::string msg = make_string("DocumentDB reprocess on startup (%u%% done)",
                                           static_cast<uint32_t>(progress));
        return StatusReport::create(params.state(StatusReport::PARTIAL).progress(progress).message(msg));
    } else if (_state.getDelayedConfig()) {
        return StatusReport::create(params.state(StatusReport::PARTIAL).
                message("DocumentDB delaying attribute aspects changes in config"));
    } else {
        return StatusReport::create(params.state(StatusReport::UPOK));
    }
}

void
DocumentDB::replayConfig(search::SerialNum serialNum)
{
    // Called by executor thread during transaction log replay.
    DocumentDBConfig::SP configSnapshot = getActiveConfig();
    if (configSnapshot.get() == NULL) {
        LOG(warning,
            "DocumentDB(%s): Missing old config when replaying config, serialNum=%" PRIu64,
            _docTypeName.toString().c_str(), serialNum);
        return;
    }
    // Load config to replay
    _config_store->loadConfig(*configSnapshot, serialNum, configSnapshot);
    // Grab relevant parts from pending config
    configSnapshot = DocumentDBConfigScout::scout(configSnapshot, *_pendingConfigSnapshot.get());
    // Ignore configs that are not relevant during replay of transaction log
    configSnapshot = DocumentDBConfig::makeReplayConfig(configSnapshot);
    applyConfig(configSnapshot, serialNum);
    LOG(info,
        "DocumentDB(%s): Replayed config with serialNum=%" PRIu64,
        _docTypeName.toString().c_str(), serialNum);
}

int64_t DocumentDB::getActiveGeneration() const {
    lock_guard guard(_configMutex);
    return _activeConfigSnapshotGeneration;
}

void
DocumentDB::syncFeedView()
{
    // Called by executor or while in rendezvous with executor

    if (!_syncFeedViewEnabled)
        return;
    IFeedView::SP oldFeedView(_feedView.get());
    IFeedView::SP newFeedView(_subDBs.getFeedView());
    _feedView.set(newFeedView);
    _feedHandler.setActiveFeedView(newFeedView.get());
    _subDBs.createRetrievers();
    _subDBs.maintenanceSync(_maintenanceController, _visibility);

    // Ensure that old feed view is referenced until all index executor tasks
    // depending on it has completed.
    performDropFeedView(oldFeedView);
}

bool
DocumentDB::hasDocument(const document::DocumentId &id)
{
    return _subDBs.getReadySubDB()->hasDocument(id);
}

void
DocumentDB::injectMaintenanceJobs(const DocumentDBMaintenanceConfig &config)
{
    // Called by executor thread
    _maintenanceController.killJobs();
    MaintenanceJobsInjector::injectJobs(_maintenanceController,
            config,
            _feedHandler, // IHeartBeatHandler
            *_sessionManager, // ISessionCachePruner
            _lidSpaceCompactionHandlers,
            _feedHandler, // IOperationStorer
            _maintenanceController, // IFrozenBucketHandler
            _subDBs.getBucketCreateNotifier(),
            _docTypeName.getName(),
            _bucketSpace,
            _feedHandler, // IPruneRemovedDocumentsHandler
            _feedHandler, // IDocumentMoveHandler
            _clusterStateHandler, // IBucketModifiedHandler
            _clusterStateHandler, // IClusterStateChangedNotifier
            _bucketHandler, // IBucketStateChangedNotifier
            _calc, // IBucketStateCalculator::SP
            _dmUsageForwarder,
            _jobTrackers,
            _visibility,  // ICommitable
            _subDBs.getReadySubDB()->getAttributeManager(),
            _subDBs.getNotReadySubDB()->getAttributeManager(),
            _writeFilter);
}

void
DocumentDB::performStartMaintenance()
{
    // Called by executor thread
    // Only start once, after replay done

    DocumentDBMaintenanceConfig::SP maintenanceConfig;
    {
        lock_guard guard(_configMutex);
        if (_state.getClosed())
            return;
        assert(_activeConfigSnapshot.get() != NULL);
        maintenanceConfig = _activeConfigSnapshot->getMaintenanceConfigSP();
    }
    if (_maintenanceController.getStopping()) {
        return;
    }
    injectMaintenanceJobs(*maintenanceConfig);
    _maintenanceController.start(maintenanceConfig);
}

void
DocumentDB::stopMaintenance()
{
    _maintenanceController.stop();
}

void
DocumentDB::forwardMaintenanceConfig()
{
    // Called by executor thread
    DocumentDBConfig::SP activeConfig = getActiveConfig();
    assert(activeConfig.get() != NULL);
    DocumentDBMaintenanceConfig::SP
        maintenanceConfig(activeConfig->getMaintenanceConfigSP());
    if (!_state.getClosed()) {
        if (_maintenanceController.getStarted() &&
            !_maintenanceController.getStopping()) {
            injectMaintenanceJobs(*maintenanceConfig);
        }
        _maintenanceController.newConfig(maintenanceConfig);
    }
}

void
DocumentDB::notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc)
{
    // Called by executor thread
    _calc = newCalc; // Save for maintenance job injection
    // Forward changes of cluster state to feed view
    IFeedView::SP feedView(_feedView.get());
    if (feedView.get() != NULL) {
        // Try downcast to avoid polluting API
        CombiningFeedView *cfv = dynamic_cast<CombiningFeedView *>(feedView.get());
        if (cfv != NULL)
            cfv->setCalculator(newCalc);
    }
    _subDBs.setBucketStateCalculator(newCalc);
}


namespace {

void notifyBucketsChanged(const documentmetastore::IBucketHandler &metaStore,
                          IBucketModifiedHandler &handler,
                          const vespalib::string &name)
{
    BucketDBOwner::Guard buckets = metaStore.getBucketDB().takeGuard();
    for (const auto &kv : *buckets) {
        handler.notifyBucketModified(kv.first);
    }
    LOG(debug, "notifyBucketsChanged(%s, %zu)", name.c_str(), buckets->size());
}

}


void
DocumentDB::notifyAllBucketsChanged()
{
    // Called by executor thread
    notifyBucketsChanged(_subDBs.getReadySubDB()->getDocumentMetaStoreContext().get(),
                         _clusterStateHandler, "ready");
    notifyBucketsChanged(_subDBs.getRemSubDB()->getDocumentMetaStoreContext().get(),
                         _clusterStateHandler, "removed");
    notifyBucketsChanged(_subDBs.getNotReadySubDB()->getDocumentMetaStoreContext().get(),
                         _clusterStateHandler, "notready");
}

namespace {

void
updateIndexMetrics(DocumentDBMetricsCollection &metrics, const search::SearchableStats &stats)
{
    DocumentDBTaggedMetrics::IndexMetrics &indexMetrics = metrics.getTaggedMetrics().index;
    indexMetrics.diskUsage.set(stats.sizeOnDisk());
    indexMetrics.memoryUsage.update(stats.memoryUsage());

    LegacyDocumentDBMetrics::IndexMetrics &legacyIndexMetrics = metrics.getLegacyMetrics().index;
    legacyIndexMetrics.memoryUsage.set(stats.memoryUsage().allocatedBytes());
    legacyIndexMetrics.docsInMemory.set(stats.docsInMemory());
    legacyIndexMetrics.diskUsage.set(stats.sizeOnDisk());
}

struct TempAttributeMetric
{
    MemoryUsage _memoryUsage;
    uint64_t    _bitVectors;

    TempAttributeMetric()
        : _memoryUsage(),
          _bitVectors(0)
    {}
};

struct TempAttributeMetrics
{
    typedef std::map<vespalib::string, TempAttributeMetric> AttrMap;
    TempAttributeMetric _total;
    AttrMap _attrs;
};

bool
isReadySubDB(const IDocumentSubDB *subDb, const DocumentSubDBCollection &subDbs)
{
    return subDb == subDbs.getReadySubDB();
}

bool
isNotReadySubDB(const IDocumentSubDB *subDb, const DocumentSubDBCollection &subDbs)
{
    return subDb == subDbs.getNotReadySubDB();
}

void
fillTempAttributeMetrics(TempAttributeMetrics &metrics, const vespalib::string &attrName,
                         const MemoryUsage &memoryUsage, uint32_t bitVectors)
{
    metrics._total._memoryUsage.merge(memoryUsage);
    metrics._total._bitVectors += bitVectors;
    TempAttributeMetric &m = metrics._attrs[attrName];
    m._memoryUsage.merge(memoryUsage);
    m._bitVectors += bitVectors;
}

void
fillTempAttributeMetrics(TempAttributeMetrics &totalMetrics,
                         TempAttributeMetrics &readyMetrics,
                         TempAttributeMetrics &notReadyMetrics,
                         const DocumentSubDBCollection &subDbs)
{
    for (const auto subDb : subDbs) {
        proton::IAttributeManager::SP attrMgr(subDb->getAttributeManager());
        if (attrMgr) {
            TempAttributeMetrics *subMetrics =
                    (isReadySubDB(subDb, subDbs) ? &readyMetrics :
                     (isNotReadySubDB(subDb, subDbs) ? &notReadyMetrics : nullptr));
            std::vector<search::AttributeGuard> list;
            attrMgr->getAttributeListAll(list);
            for (const auto &attr : list) {
                const search::attribute::Status &status = attr->getStatus();
                MemoryUsage memoryUsage(status.getAllocated(), status.getUsed(), status.getDead(), status.getOnHold());
                uint32_t bitVectors = status.getBitVectors();
                fillTempAttributeMetrics(totalMetrics, attr->getName(), memoryUsage, bitVectors);
                if (subMetrics != nullptr) {
                    fillTempAttributeMetrics(*subMetrics, attr->getName(), memoryUsage, bitVectors);
                }
            }
        }
    }
}

void
updateLegacyAttributeMetrics(LegacyAttributeMetrics &metrics, const TempAttributeMetrics &tmpMetrics)
{
    for (const auto &attr : tmpMetrics._attrs) {
        LegacyAttributeMetrics::List::Entry *entry = metrics.list.get(attr.first);
        if (entry) {
            entry->memoryUsage.set(attr.second._memoryUsage.allocatedBytes());
            entry->bitVectors.set(attr.second._bitVectors);
        } else {
            LOG(debug, "Could not update metrics for attribute: '%s'", attr.first.c_str());
        }
    }
    metrics.memoryUsage.set(tmpMetrics._total._memoryUsage.allocatedBytes());
    metrics.bitVectors.set(tmpMetrics._total._bitVectors);
}

void
updateAttributeMetrics(AttributeMetrics &metrics, const TempAttributeMetrics &tmpMetrics)
{
    for (const auto &attr : tmpMetrics._attrs) {
        auto entry = metrics.get(attr.first);
        if (entry) {
            entry->memoryUsage.update(attr.second._memoryUsage);
        }
    }
}

void
updateAttributeMetrics(DocumentDBMetricsCollection &metrics, const DocumentSubDBCollection &subDbs)
{
    TempAttributeMetrics totalMetrics;
    TempAttributeMetrics readyMetrics;
    TempAttributeMetrics notReadyMetrics;
    fillTempAttributeMetrics(totalMetrics, readyMetrics, notReadyMetrics, subDbs);

    updateLegacyAttributeMetrics(metrics.getLegacyMetrics().attributes, totalMetrics);
    updateLegacyAttributeMetrics(metrics.getLegacyMetrics().ready.attributes, readyMetrics);
    updateLegacyAttributeMetrics(metrics.getLegacyMetrics().notReady.attributes, notReadyMetrics);

    updateAttributeMetrics(metrics.getTaggedMetrics().ready.attributes, readyMetrics);
    updateAttributeMetrics(metrics.getTaggedMetrics().notReady.attributes, notReadyMetrics);
}

void
updateMatchingMetrics(LegacyDocumentDBMetrics::MatchingMetrics &metrics, const IDocumentSubDB &ready)
{
    MatchingStats stats;
    for (const auto &kv : metrics.rank_profiles) {
        MatchingStats rp_stats = ready.getMatcherStats(kv.first);
        kv.second->update(rp_stats);
        stats.add(rp_stats);
    }
    metrics.update(stats);
}

void
updateDocstoreMetrics(LegacyDocumentDBMetrics::DocstoreMetrics &metrics,
                      const DocumentSubDBCollection &sub_dbs,
                      CacheStats &lastCacheStats)
{
    size_t memoryUsage = 0;
    CacheStats cache_stats;
    for (const auto subDb : sub_dbs) {
        const ISummaryManager::SP &summaryMgr = subDb->getSummaryManager();
        if (summaryMgr) {
            cache_stats += summaryMgr->getBackingStore().getCacheStats();
            memoryUsage += summaryMgr->getBackingStore().memoryUsed();
        }
    }
    metrics.memoryUsage.set(memoryUsage);
    size_t lookups = cache_stats.hits + cache_stats.misses;
    metrics.cacheLookups.set(lookups);
    size_t last_count = lastCacheStats.hits + lastCacheStats.misses;
        // For the above code to add sane values to the metric, the following
        // must be true
    if (lookups < last_count || cache_stats.hits < lastCacheStats.hits) {
        LOG(warning, "Not adding document db metrics as values calculated "
                     "are corrupt. %" PRIu64 ", %" PRIu64 ", %" PRIu64 ", %" PRIu64 ".",
            lookups, last_count, cache_stats.hits, lastCacheStats.hits);
    } else {
        if (lookups - last_count > 0xffffffffull
            || cache_stats.hits - lastCacheStats.hits > 0xffffffffull)
        {
            LOG(warning, "Document db metrics to add are suspiciously high."
                         " %" PRIu64 ", %" PRIu64 ".",
                lookups - last_count, cache_stats.hits - lastCacheStats.hits);
        }
        metrics.cacheHitRate.addTotalValueWithCount(
                cache_stats.hits - lastCacheStats.hits, lookups - last_count);
    }
    metrics.hits = cache_stats.hits;
    metrics.cacheElements.set(cache_stats.elements);
    metrics.cacheMemoryUsed.set(cache_stats.memory_used);
    lastCacheStats = cache_stats;
}

void
updateDocumentStoreMetrics(DocumentDBTaggedMetrics::SubDBMetrics::
                           DocumentStoreMetrics &metrics,
                           IDocumentSubDB *subDb)
{
    const ISummaryManager::SP &summaryMgr = subDb->getSummaryManager();
    search::IDocumentStore &backingStore = summaryMgr->getBackingStore();
    search::DataStoreStorageStats storageStats(backingStore.getStorageStats());
    metrics.diskUsage.set(storageStats.diskUsage());
    metrics.diskBloat.set(storageStats.diskBloat());
    metrics.maxBucketSpread.set(storageStats.maxBucketSpread());
    metrics.memoryUsage.update(backingStore.getMemoryUsage());
}

template <typename MetricSetType>
void
updateLidSpaceMetrics(MetricSetType &metrics, const search::IDocumentMetaStore &metaStore)
{
    LidUsageStats stats = metaStore.getLidUsageStats();
    metrics.lidLimit.set(stats.getLidLimit());
    metrics.usedLids.set(stats.getUsedLids());
    metrics.lowestFreeLid.set(stats.getLowestFreeLid());
    metrics.highestUsedLid.set(stats.getHighestUsedLid());
    metrics.lidBloatFactor.set(stats.getLidBloatFactor());
    metrics.lidFragmentationFactor.set(stats.getLidFragmentationFactor());
}

}  // namespace

void
DocumentDB::updateMetrics(DocumentDBMetricsCollection &metrics)
{
    if (_state.getState() < DDBState::State::REPLAY_TRANSACTION_LOG) {
        return;
    }
    
    ExecutorThreadingServiceStats threadingServiceStats = _writeService.getStats();
    updateLegacyMetrics(metrics.getLegacyMetrics(), threadingServiceStats);
    updateIndexMetrics(metrics, _subDBs.getReadySubDB()->getSearchableStats());
    updateAttributeMetrics(metrics, _subDBs);
    updateMetrics(metrics.getTaggedMetrics(), threadingServiceStats);
}

void
DocumentDB::updateLegacyMetrics(LegacyDocumentDBMetrics &metrics, const ExecutorThreadingServiceStats &threadingServiceStats)
{
    updateMatchingMetrics(metrics.matching, *_subDBs.getReadySubDB());
    metrics.executor.update(threadingServiceStats.getMasterExecutorStats());
    metrics.summaryExecutor.update(threadingServiceStats.getSummaryExecutorStats());
    metrics.indexExecutor.update(threadingServiceStats.getIndexExecutorStats());
    metrics.sessionManager.update(_sessionManager->getGroupingStats());
    updateDocstoreMetrics(metrics.docstore, _subDBs, _lastDocStoreCacheStats);
    metrics.numDocs.set(getNumDocs());

    DocumentMetaStoreReadGuards dmss(_subDBs);
    
    metrics.numActiveDocs.set(dmss.numActiveDocs());
    metrics.numIndexedDocs.set(dmss.numIndexedDocs());
    metrics.numStoredDocs.set(dmss.numStoredDocs());
    metrics.numRemovedDocs.set(dmss.numRemovedDocs());

    updateLidSpaceMetrics(metrics.ready.docMetaStore, dmss.readydms->get());
    updateLidSpaceMetrics(metrics.notReady.docMetaStore, dmss.notreadydms->get());
    updateLidSpaceMetrics(metrics.removed.docMetaStore, dmss.remdms->get());

    metrics.numBadConfigs.set(_state.getDelayedConfig() ? 1u : 0u);
}

void
DocumentDB::
updateMetrics(DocumentDBTaggedMetrics::AttributeMetrics &metrics)
{
    AttributeUsageFilter &writeFilter(_writeFilter);
    AttributeUsageStats attributeUsageStats = writeFilter.getAttributeUsageStats();
    bool feedBlocked = !writeFilter.acceptWriteOperation();
    double enumStoreUsed = attributeUsageStats.enumStoreUsage().getUsage().usage();
    double multiValueUsed = attributeUsageStats.multiValueUsage().getUsage().usage();
    metrics.resourceUsage.enumStore.set(enumStoreUsed);
    metrics.resourceUsage.multiValue.set(multiValueUsed);
    metrics.resourceUsage.feedingBlocked.set(feedBlocked ? 1 : 0);
}

void
DocumentDB::updateMetrics(DocumentDBTaggedMetrics &metrics, const ExecutorThreadingServiceStats &)
{
    _jobTrackers.updateMetrics(metrics.job);

    updateMetrics(metrics.attribute);
    updateDocumentStoreMetrics(metrics.ready.documentStore, _subDBs.getReadySubDB());
    updateDocumentStoreMetrics(metrics.removed.documentStore, _subDBs.getRemSubDB());
    updateDocumentStoreMetrics(metrics.notReady.documentStore, _subDBs.getNotReadySubDB());
    DocumentMetaStoreReadGuards dmss(_subDBs);
    updateLidSpaceMetrics(metrics.ready.lidSpace, dmss.readydms->get());
    updateLidSpaceMetrics(metrics.notReady.lidSpace, dmss.notreadydms->get());
    updateLidSpaceMetrics(metrics.removed.lidSpace, dmss.remdms->get());
}

void
DocumentDB::sync(SerialNum syncTo)
{
    LOG(spam, "DocumentDB(%s): sync(): serialNum=%zu", _docTypeName.toString().c_str(), syncTo);
    _feedHandler.syncTls(syncTo);
}

void
DocumentDB::waitForOnlineState()
{
    _state.waitForOnlineState();
}

vespalib::string
DocumentDB::getName() const
{
    return _docTypeName.getName();
}

document::BucketSpace
DocumentDB::getBucketSpace() const
{
    return _bucketSpace;
}

uint32_t
DocumentDB::getDistributionKey() const
{
    return _owner.getDistributionKey();
}

} // namespace proton
