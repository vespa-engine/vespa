// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "documentdb.h"
#include "bootstrapconfig.h"
#include "combiningfeedview.h"
#include "document_meta_store_read_guards.h"
#include "document_subdb_collection_explorer.h"
#include "documentdbconfigscout.h"
#include "feedhandler.h"
#include "i_shared_threading_service.h"
#include "idocumentdbowner.h"
#include "idocumentsubdb.h"
#include "maintenance_jobs_injector.h"
#include "reconfig_params.h"
#include "replay_throttling_policy.h"
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/metrics/updatehook.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcore/proton/common/i_transient_resource_usage_provider.h>
#include <vespa/searchcore/proton/common/statusreport.h>
#include <vespa/searchcore/proton/docsummary/isummarymanager.h>
#include <vespa/searchcore/proton/feedoperation/noopoperation.h>
#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/searchcore/proton/initializer/task_runner.h>
#include <vespa/searchcore/proton/metrics/executor_threading_service_stats.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include <vespa/searchcore/proton/persistenceengine/commit_and_wait_document_retriever.h>
#include <vespa/searchcore/proton/reference/document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_registry.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/exceptions.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/log/log.h>
#include <vespa/searchcorespi/index/warmupconfig.h>

LOG_SETUP(".proton.server.documentdb");

using vespa::config::search::AttributesConfig;
using vespa::config::search::core::ProtonConfig;
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
using searchcorespi::index::WarmupConfig;
using search::TuneFileDocumentDB;
using storage::spi::Timestamp;
using search::common::FileHeaderContext;
using proton::initializer::InitializerTask;
using proton::initializer::TaskRunner;
using vespalib::GateCallback;
using vespalib::IDestructorCallback;
using vespalib::makeLambdaTask;
using searchcorespi::IFlushTarget;

namespace proton {

namespace {
constexpr uint32_t indexing_thread_stack_size = 128_Ki;

index::IndexConfig
makeIndexConfig(const ProtonConfig::Index & cfg) {
    return {WarmupConfig(vespalib::from_s(cfg.warmup.time), cfg.warmup.unpack), size_t(cfg.maxflushed), size_t(cfg.cache.size)};
}

ReplayThrottlingPolicy
make_replay_throttling_policy(const ProtonConfig::ReplayThrottlingPolicy& cfg) {
    if (cfg.type == ProtonConfig::ReplayThrottlingPolicy::Type::UNLIMITED) {
        return ReplayThrottlingPolicy({});
    }
    vespalib::SharedOperationThrottler::DynamicThrottleParams params;
    params.min_window_size = cfg.minWindowSize;
    params.max_window_size = cfg.maxWindowSize;
    params.window_size_increment = cfg.windowSizeIncrement;
    return ReplayThrottlingPolicy(params);
}

class MetricsUpdateHook : public metrics::UpdateHook {
    DocumentDB &_db;
public:
    explicit MetricsUpdateHook(DocumentDB &s)
        : metrics::UpdateHook("documentdb-hook"),
          _db(s)
    {}
    void updateMetrics(const MetricLockGuard & guard) override {
        _db.updateMetrics(guard);
    }
};

class DocumentDBResourceUsageProvider : public ITransientResourceUsageProvider {
private:
    const DocumentDB& _doc_db;

public:
    explicit DocumentDBResourceUsageProvider(const DocumentDB& doc_db) noexcept
        : _doc_db(doc_db)
    {}
    TransientResourceUsage get_transient_resource_usage() const override {
        // Transient disk usage is measured as the total disk usage of all current fusion indexes.
        // Transient memory usage is measured as the total memory usage of all memory indexes.
        auto stats = _doc_db.getReadySubDB()->getSearchableStats();
        return {stats.fusion_size_on_disk(), stats.memoryUsage().allocatedBytes()};
    }
};

template<typename T>
void
forceCommitAndWait(IFeedView & feedView, SerialNum serialNum, T keepAlive) {
    vespalib::Gate gate;
    using Keep = vespalib::KeepAlive<std::pair<T, std::shared_ptr<IDestructorCallback>>>;
    feedView.forceCommit(CommitParam(serialNum),
                          std::make_shared<Keep>(std::make_pair(std::move(keepAlive), std::make_shared<GateCallback>(gate))));
    gate.await();
}

}

template <typename FunctionType>
void
DocumentDB::masterExecute(FunctionType &&function) {
    _writeService.master().execute(makeLambdaTask(std::forward<FunctionType>(function)));
}

DocumentDB::SP
DocumentDB::create(const vespalib::string &baseDir,
                   DocumentDBConfig::SP currentSnapshot,
                   const vespalib::string &tlsSpec,
                   matching::QueryLimiter &queryLimiter,
                   const DocTypeName &docTypeName,
                   document::BucketSpace bucketSpace,
                   const ProtonConfig &protonCfg,
                   IDocumentDBOwner &owner,
                   ISharedThreadingService& shared_service,
                   const search::transactionlog::WriterFactory &tlsWriterFactory,
                   MetricsWireService &metricsWireService,
                   const search::common::FileHeaderContext &fileHeaderContext,
                   std::shared_ptr<search::attribute::Interlock> attribute_interlock,
                   ConfigStore::UP config_store,
                   InitializeThreads initializeThreads,
                   const HwInfo &hwInfo)
{
    return DocumentDB::SP(
            new DocumentDB(baseDir, std::move(currentSnapshot), tlsSpec, queryLimiter, docTypeName, bucketSpace,
                           protonCfg, owner, shared_service, tlsWriterFactory,
                           metricsWireService, fileHeaderContext, std::move(attribute_interlock),
                           std::move(config_store), std::move(initializeThreads), hwInfo));
}
DocumentDB::DocumentDB(const vespalib::string &baseDir,
                       DocumentDBConfig::SP configSnapshot,
                       const vespalib::string &tlsSpec,
                       matching::QueryLimiter &queryLimiter,
                       const DocTypeName &docTypeName,
                       document::BucketSpace bucketSpace,
                       const ProtonConfig &protonCfg,
                       IDocumentDBOwner &owner,
                       ISharedThreadingService& shared_service,
                       const search::transactionlog::WriterFactory &tlsWriterFactory,
                       MetricsWireService &metricsWireService,
                       const FileHeaderContext &fileHeaderContext,
                       std::shared_ptr<search::attribute::Interlock> attribute_interlock,
                       ConfigStore::UP config_store,
                       InitializeThreads initializeThreads,
                       const HwInfo &hwInfo)
    : DocumentDBConfigOwner(),
      IReplayConfig(),
      IFeedHandlerOwner(),
      IDocumentSubDBOwner(),
      IClusterStateChangedHandler(),
      search::transactionlog::SyncProxy(),
      std::enable_shared_from_this<DocumentDB>(),
      _docTypeName(docTypeName),
      _bucketSpace(bucketSpace),
      _baseDir(baseDir + "/" + _docTypeName.toString()),
      // Only one thread per executor, or performDropFeedView() will fail.
      _writeServiceConfig(configSnapshot->get_threading_service_config()),
      _writeService(shared_service.shared(), shared_service.transport(), shared_service.clock(), shared_service.field_writer(),
                    &shared_service.invokeService(), _writeServiceConfig, indexing_thread_stack_size),
      _initializeThreads(std::move(initializeThreads)),
      _initConfigSnapshot(),
      _initConfigSerialNum(0u),
      _pendingConfigSnapshot(configSnapshot),
      _configMutex(),
      _configCV(),
      _activeConfigSnapshot(),
      _activeConfigSnapshotGeneration(0),
      _validateAndSanitizeDocStore(protonCfg.validateAndSanitizeDocstore == vespa::config::search::core::ProtonConfig::ValidateAndSanitizeDocstore::YES),
      _initGate(),
      _clusterStateHandler(_writeService.master()),
      _bucketHandler(_writeService.master()),
      _indexCfg(makeIndexConfig(protonCfg.index)),
      _replay_throttling_policy(std::make_unique<ReplayThrottlingPolicy>(make_replay_throttling_policy(protonCfg.replayThrottlingPolicy))),
      _config_store(std::move(config_store)),
      _sessionManager(std::make_shared<matching::SessionManager>(protonCfg.grouping.sessionmanager.maxentries)),
      _metricsWireService(metricsWireService),
      _metrics(_docTypeName.getName(), protonCfg.numthreadspersearch),
      _metricsHook(std::make_unique<MetricsUpdateHook>(*this)),
      _feedView(),
      _refCount(),
      _owner(owner),
      _bucketExecutor(shared_service.bucket_executor()),
      _state(),
      _dmUsageForwarder(_writeService.master()),
      _writeFilter(),
      _transient_usage_provider(std::make_shared<DocumentDBResourceUsageProvider>(*this)),
      _feedHandler(std::make_unique<FeedHandler>(_writeService, tlsSpec, docTypeName, *this, _writeFilter, *this, tlsWriterFactory)),
      _subDBs(*this, *this, *_feedHandler, _docTypeName,
              _writeService, shared_service.warmup(), fileHeaderContext, std::move(attribute_interlock),
              metricsWireService, getMetrics(), queryLimiter, shared_service.clock(),
              _configMutex, _baseDir, hwInfo),
      _maintenanceController(shared_service.transport(), _writeService.master(), shared_service.shared(), _refCount, _docTypeName),
      _jobTrackers(),
      _calc(),
      _metricsUpdater(_subDBs, _writeService, _jobTrackers, *_sessionManager, _writeFilter, *_feedHandler)
{
    assert(configSnapshot);

    LOG(debug, "DocumentDB(%s): Creating database in directory '%s'", _docTypeName.toString().c_str(), _baseDir.c_str());

    _feedHandler->init(_config_store->getOldestSerialNum());
    _feedHandler->setBucketDBHandler(&_subDBs.getBucketDBHandler());
    saveInitialConfig(configSnapshot);
    resumeSaveConfig();
    SerialNum configSerial = _config_store->getPrevValidSerial(_feedHandler->getPrunedSerialNum() + 1);
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

    _writeFilter.setConfig(loaded_config->getMaintenanceConfigSP()->getAttributeUsageFilterConfig());
}

void
DocumentDB::registerReference()
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

void
DocumentDB::setActiveConfig(DocumentDBConfig::SP config, int64_t generation) {
    lock_guard guard(_configMutex);
    registerReference();
    assert(generation >= config->getGeneration());
    _activeConfigSnapshot = std::move(config);
    if (_activeConfigSnapshotGeneration < generation) {
        _activeConfigSnapshotGeneration = generation;
    }
    _configCV.notify_all();
}

DocumentDBConfig::SP
DocumentDB::getActiveConfig() const {
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
    {}

    ~InitDoneTask() override;

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
    InitializerTask::SP rootTask = _subDBs.createInitializer(*configSnapshot, _initConfigSerialNum, _indexCfg);
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
    assert(_writeService.master().isCurrentThread());
    _bucketHandler.setReadyBucketHandler(_subDBs.getReadySubDB()->getDocumentMetaStoreContext().get());
    _subDBs.initViews(*configSnapshot, _sessionManager);
    syncFeedView();
    // Check that feed view has been activated.
    assert(_feedView.get());
    int64_t generation = configSnapshot->getGeneration();
    setActiveConfig(std::move(configSnapshot), generation);
    startTransactionLogReplay();
}


void
DocumentDB::newConfigSnapshot(DocumentDBConfig::SP snapshot)
{
    // Called by executor thread
    _pendingConfigSnapshot.set(std::move(snapshot));
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
        (void) _feedHandler->storeOperationSync(op);
        sync(op.getSerialNum());
        _subDBs.pruneRemovedFields(op.getSerialNum());
    }
    _subDBs.onReprocessDone(_feedHandler->getSerialNum());
    enterOnlineState();
}


void
DocumentDB::enterOnlineState()
{
    // Called by executor thread
    assert(_writeService.master().isCurrentThread());
    // Ensure that all replayed operations are committed to memory structures
    _feedView.get()->forceCommitAndWait(CommitParam(_feedHandler->getSerialNum()));

    (void) _state.enterOnlineState();
    // Consider delayed pruning of transaction log and config history
    _feedHandler->considerDelayedPrune();
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
    const auto & newRepo = newConfigSnapshot.getDocumentTypeRepoSP();
    auto newDocType = newRepo->getDocumentType(_docTypeName.getName());
    assert(newDocType != nullptr);
    DocumentDBReferenceResolver resolver(*registry, *newDocType, newConfigSnapshot.getImportedFieldsConfig(), *oldDocType,
                                         _refCount, _writeService.attributeFieldWriter(), _state.getAllowReconfig());
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

    DocumentDBConfig::ComparisonResult cmpres;
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
    auto replay_config = DocumentDBConfig::makeReplayConfig(configSnapshot);
    bool equalReplayConfig =
        *replay_config ==
        *DocumentDBConfig::makeReplayConfig(_activeConfigSnapshot);
    bool tlsReplayDone = _feedHandler->getTransactionLogReplayDone();
    FeedHandler::CommitResult commit_result;
    if (!equalReplayConfig && tlsReplayDone) {
        sync(_feedHandler->getSerialNum());
        serialNum = _feedHandler->inc_serial_num();
        _config_store->saveConfig(*replay_config, serialNum);
        replay_config.reset();
        // save entry in transaction log
        NewConfigOperation op(serialNum, *_config_store);
        commit_result = _feedHandler->storeOperationSync(op);
        sync(op.getSerialNum());
    }
    {
        bool elidedConfigSave = equalReplayConfig && tlsReplayDone;
        forceCommitAndWait(*_feedView.get(), elidedConfigSave ? serialNum : serialNum - 1, std::move(commit_result));
    }
    if (params.shouldMaintenanceControllerChange()) {
        _maintenanceController.killJobs();
    }

    if (_state.getState() >= DDBState::State::APPLY_LIVE_CONFIG) {
        _writeServiceConfig.update(configSnapshot->get_threading_service_config());
    }
    _writeService.set_task_limits(_writeServiceConfig.master_task_limit(),
                                  _writeServiceConfig.defaultTaskLimit(),
                                  _writeServiceConfig.defaultTaskLimit());
    if (params.shouldSubDbsChange()) {
        applySubDBConfig(*configSnapshot, serialNum, params);
        if (serialNum < _feedHandler->get_replay_end_serial_num()) {
            // Not last entry in tls.  Reprocessing should already be done.
            _subDBs.getReprocessingRunner().reset();
        }
        if (_state.getState() == DDBState::State::ONLINE) {
            // Changes applied while online should not trigger reprocessing
            assert(_subDBs.getReprocessingRunner().empty());
        }
        syncFeedView();
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
    setActiveConfig(configSnapshot, generation);
    if (params.shouldMaintenanceControllerChange() || _maintenanceController.getPaused()) {
        forwardMaintenanceConfig();
    }
    _writeFilter.setConfig(configSnapshot->getMaintenanceConfigSP()->getAttributeUsageFilterConfig());
    if (_subDBs.getReprocessingRunner().empty()) {
        _subDBs.pruneRemovedFields(serialNum);
    }
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
    DocumentDBReferenceResolver resolver(*registry, *docType, activeConfig->getImportedFieldsConfig(), *docType,
                                         _refCount, _writeService.attributeFieldWriter(), false);
    _subDBs.tearDownReferences(resolver);
    registry->remove(_docTypeName.getName());
}

void
DocumentDB::close()
{
    waitForOnlineState();
    {
        lock_guard guard(_configMutex);
        _state.enterShutdownState();
        _configCV.notify_all();
    }
    // Abort any ongoing maintenance
    stopMaintenance();
    masterExecute([this]() {
        _feedView.get()->forceCommitAndWait(search::CommitParam(getCurrentSerialNumber()));
        tearDownReferences();
    });
    _writeService.master().sync();
    // Wait until inflight feed operations to this document db has left.
    // Caller should have removed document DB from feed router.
    _refCount.waitForZeroRefCount();

    masterExecute([this] () {
        _feedView.get()->forceCommitAndWait(search::CommitParam(getCurrentSerialNumber()));
    });
    _writeService.master().sync();

    // The attributes in the ready sub db is also the total set of attributes.
    DocumentDBTaggedMetrics &metrics = getMetrics();
    _metricsWireService.cleanAttributes(metrics.ready.attributes);
    _metricsWireService.cleanAttributes(metrics.notReady.attributes);

    masterExecute([this] () {
        closeSubDBs();
    });
    // What about queued tasks ?
    _writeService.shutdown();
    _maintenanceController.kill();
    _feedHandler->close();
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
    if (_state.get_load_done()) {
        return _subDBs.getReadySubDB()->getNumDocs();
    } else {
        return 0u;
    }
}

ActiveDocs
DocumentDB::getNumActiveDocs() const
{
    if (_state.get_load_done()) {
        return { _subDBs.getReadySubDB()->getNumActiveDocs(), _subDBs.getBucketDB().getNumActiveDocs() };
    } else {
        return {0u, 0u};
    }
}

void
DocumentDB::saveInitialConfig(std::shared_ptr<DocumentDBConfig> configSnapshot)
{
    // Only called from ctor

    lock_guard guard(_configMutex);
    if (_config_store->getBestSerialNum() != 0) {
        return;             // Initial config already present
    }

    SerialNum confSerial = _feedHandler->inc_replay_end_serial_num();
    _feedHandler->setSerialNum(confSerial);
    // Elide save of new config entry in transaction log, it would be
    // pruned at once anyway.
    // save noop entry in transaction log
    NoopOperation op;
    op.setSerialNum(_feedHandler->inc_replay_end_serial_num());
    (void) _feedHandler->storeOperationSync(op);
    sync(op.getSerialNum());
    // Wipe everything in transaction log before initial config.
    try {
        _feedHandler->tlsPrune(confSerial);  // throws on error
    } catch (const vespalib::IllegalStateException & e) {
        LOG(warning, "DocumentDB(%s): saveInitialConfig() failed pruning due to '%s'",
            _docTypeName.toString().c_str(), e.what());
    }
    auto replay_config = DocumentDBConfig::makeReplayConfig(configSnapshot);
    _config_store->saveConfig(*replay_config, confSerial);
}

void
DocumentDB::resumeSaveConfig()
{
    SerialNum bestSerial = _config_store->getBestSerialNum();
    assert(bestSerial != 0);
    if (bestSerial != _feedHandler->get_replay_end_serial_num() + 1) {
        return;
    }
    LOG(warning, "DocumentDB(%s): resumeSaveConfig() resuming save config for serial %" PRIu64,
        _docTypeName.toString().c_str(), bestSerial);
    // proton was interrupted when saving later config.
    SerialNum confSerial = _feedHandler->inc_replay_end_serial_num();
    assert(confSerial == bestSerial);
    // resume operation, i.e. save config entry in transaction log
    NewConfigOperation op(confSerial, *_config_store);
    (void) _feedHandler->storeOperationSync(op);
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
    if (_validateAndSanitizeDocStore) {
        LOG(info, "Validating documentdb %s", getName().c_str());
        SerialNum serialNum = _feedHandler->getSerialNum();
        sync(serialNum);
        _subDBs.validateDocStore(*_feedHandler, serialNum);
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
    LOG(debug, "DocumentDB(%s): Database starting.", _docTypeName.toString().c_str());

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
    _feedHandler->replayTransactionLog(readySubDB->getIndexManager()->
                                      getFlushedSerialNum(),
                                      readySubDB->getSummaryManager()->
                                      getBackingStore().lastSyncToken(),
                                      oldestFlushedSerial,
                                      newestFlushedSerial,
                                      *_config_store,
                                      *_replay_throttling_policy);
    _initGate.countDown();

    LOG(debug, "DocumentDB(%s): Database started.", _docTypeName.toString().c_str());
}

std::shared_ptr<std::vector<IDocumentRetriever::SP> >
DocumentDB::getDocumentRetrievers(IDocumentRetriever::ReadConsistency consistency)
{
    return _subDBs.getRetrievers(consistency);
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
DocumentDB::match(const SearchRequest &req, vespalib::ThreadBundle &threadBundle) const
{
    ISearchHandler::SP view(_subDBs.getReadySubDB()->getSearchView());
    return view->match(req, threadBundle);
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
    _feedHandler->flushDone(flushedSerial);
}

void
DocumentDB::setIndexSchema(const DocumentDBConfig &configSnapshot, SerialNum serialNum)
{
    // Called by executor thread
    _subDBs.getReadySubDB()->setIndexSchema(configSnapshot.getSchemaSP(), serialNum);

    // TODO: Adjust tune.
}

void
DocumentDB::reconfigure(DocumentDBConfig::SP snapshot)
{
    masterExecute([this, snapshot]() mutable { newConfigSnapshot(std::move(snapshot)); });
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
        _subDBs.onReprocessDone(_feedHandler->getSerialNum());
        NoopOperation op;
        (void) _feedHandler->storeOperationSync(op);
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
    } else if (_feedHandler->isDoingReplay()) {
        float progress = _feedHandler->getReplayProgress() * 100.0f;
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
    if ( ! configSnapshot) {
        LOG(warning, "DocumentDB(%s): Missing old config when replaying config, serialNum=%" PRIu64,
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
    LOG(info, "DocumentDB(%s): Replayed config with serialNum=%" PRIu64,
              _docTypeName.toString().c_str(), serialNum);
}

int64_t
DocumentDB::getActiveGeneration() const {
    lock_guard guard(_configMutex);
    return _activeConfigSnapshotGeneration;
}

void
DocumentDB::syncFeedView()
{
    assert(_writeService.master().isCurrentThread());
    IFeedView::SP newFeedView(_subDBs.getFeedView());

    _maintenanceController.killJobs();

    _feedView.set(newFeedView);
    _feedHandler->setActiveFeedView(newFeedView.get());
    _subDBs.createRetrievers();
    _subDBs.maintenanceSync(_maintenanceController);
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
            _bucketExecutor,
            *_feedHandler, // IHeartBeatHandler
            *_sessionManager, // ISessionCachePruner
            *_feedHandler, // IOperationStorer
            _subDBs.getBucketCreateNotifier(),
            _bucketSpace,
            *_feedHandler, // IPruneRemovedDocumentsHandler
            *_feedHandler, // IDocumentMoveHandler
            _clusterStateHandler, // IBucketModifiedHandler
            _clusterStateHandler, // IClusterStateChangedNotifier
            _bucketHandler, // IBucketStateChangedNotifier
            _calc, // IBucketStateCalculator::SP
            _dmUsageForwarder,
            _jobTrackers,
            _subDBs.getReadySubDB()->getAttributeManager(),
            _subDBs.getNotReadySubDB()->getAttributeManager(),
            _writeFilter);
}

void
DocumentDB::performStartMaintenance()
{
    // Called by executor thread
    // Only start once, after replay done

    std::shared_ptr<DocumentDBConfig> activeConfig;
    {
        lock_guard guard(_configMutex);
        if (_state.getClosed())
            return;
        activeConfig = _activeConfigSnapshot;
    }
    assert(activeConfig);
    if (_maintenanceController.getStopping()) {
        return;
    }
    auto maintenanceConfig = activeConfig->getMaintenanceConfigSP();
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
    assert(activeConfig);
    auto maintenanceConfig(activeConfig->getMaintenanceConfigSP());
    if (!_state.getClosed()) {
        if (_maintenanceController.getPaused()) {
            injectMaintenanceJobs(*maintenanceConfig);
        }
        _maintenanceController.newConfig(maintenanceConfig);
    }
}

void
DocumentDB::notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc)
{
    // Called by executor thread
    _calc = newCalc; // Save for maintenance job injection
    // Forward changes of cluster state to feed view
    IFeedView::SP feedView(_feedView.get());
    if (feedView) {
        // Try downcast to avoid polluting API
        auto *cfv = dynamic_cast<CombiningFeedView *>(feedView.get());
        if (cfv != nullptr)
            cfv->setCalculator(newCalc);
    }
    _subDBs.setBucketStateCalculator(newCalc, std::shared_ptr<vespalib::IDestructorCallback>());
}


namespace {

void
notifyBucketsChanged(const documentmetastore::IBucketHandler &metaStore,
                     IBucketModifiedHandler &handler,
                     const vespalib::string &name)
{
    bucketdb::Guard guard = metaStore.getBucketDB().takeGuard();
    document::BucketId::List buckets = guard->getBuckets();
    for (document::BucketId bucketId : buckets) {
        handler.notifyBucketModified(bucketId);
    }
    LOG(debug, "notifyBucketsChanged(%s, %zu)", name.c_str(), buckets.size());
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

void
DocumentDB::updateMetrics(const metrics::MetricLockGuard & guard)
{
    if (_state.getState() < DDBState::State::REPLAY_TRANSACTION_LOG) {
        return;
    }
    _metricsUpdater.updateMetrics(guard, _metrics);
    _maintenanceController.updateMetrics(_metrics);
    auto heart_beat_time = _feedHandler->get_heart_beat_time();
    if (heart_beat_time != vespalib::steady_time()) {
        vespalib::steady_time now = vespalib::steady_clock::now();
        _metrics.heart_beat_age.set(vespalib::to_s(now - heart_beat_time));
    } else {
        _metrics.heart_beat_age.set(0.0);
    }
}

void
DocumentDB::sync(SerialNum syncTo)
{
    LOG(spam, "DocumentDB(%s): sync(): serialNum=%" PRIu64, _docTypeName.toString().c_str(), syncTo);
    _feedHandler->syncTls(syncTo);
}

SerialNum
DocumentDB::getCurrentSerialNumber() const
{
    // Called by flush scheduler thread, by executor task or
    // visitor callback.
    // XXX: Contains future value during replay.
    return _feedHandler->getSerialNum();
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

std::shared_ptr<const ITransientResourceUsageProvider>
DocumentDB::transient_usage_provider()
{
    return _transient_usage_provider;
}

void
DocumentDB::set_attribute_usage_listener(std::unique_ptr<IAttributeUsageListener> listener)
{
    _writeFilter.set_listener(std::move(listener));
}

} // namespace proton
