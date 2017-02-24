// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

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
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/common/eventlogger.h>
#include <vespa/searchcore/proton/common/schemautil.h>
#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/searchcore/proton/initializer/task_runner.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reference/i_document_db_referent_registry.h>
#include <vespa/searchlib/attribute/attributefactory.h>
#include <vespa/searchlib/attribute/configconverter.h>
#include <vespa/searchlib/engine/docsumreply.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/exceptions.h>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.documentdb");

using vespa::config::search::AttributesConfig;
using vespa::config::search::core::ProtonConfig;
using search::index::SchemaBuilder;
using vespalib::JSONStringer;
using vespalib::FileHeader;
using vespalib::Executor;
using vespalib::IllegalStateException;
using vespalib::StateExplorer;
using vespalib::make_string;
using vespalib::makeTask;
using vespalib::makeClosure;
using namespace proton::matching;
using namespace search;
using namespace search::docsummary;
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
using search::makeLambdaTask;

namespace proton {

namespace {

constexpr uint32_t indexing_thread_stack_size = 128 * 1024;

uint32_t semiUnboundTaskLimit(uint32_t semiUnboundExecutorTaskLimit,
                              uint32_t indexingThreads)
{
    uint32_t taskLimit = semiUnboundExecutorTaskLimit / indexingThreads;
    return taskLimit;
}

}

DocumentDB::DocumentDB(const vespalib::string &baseDir,
                       const DocumentDBConfig::SP & configSnapshot,
                       const vespalib::string &tlsSpec,
                       matching::QueryLimiter & queryLimiter,
                       const vespalib::Clock &clock,
                       const DocTypeName &docTypeName,
                       const ProtonConfig &protonCfg,
                       IDocumentDBOwner & owner,
                       vespalib::ThreadExecutor & warmupExecutor,
                       vespalib::ThreadStackExecutorBase & summaryExecutor,
                       search::transactionlog::Writer * tlsDirectWriter,
                       MetricsWireService &metricsWireService,
                       const FileHeaderContext &fileHeaderContext,
                       ConfigStore::UP config_store,
                       InitializeThreads initializeThreads,
                       const HwInfo &hwInfo)
    : IDocumentDBConfigOwner(),
      IReplayConfig(),
      FeedHandler::IOwner(),
      IDocumentSubDB::IOwner(),
      IClusterStateChangedHandler(),
      IWipeOldRemovedFieldsHandler(),
      search::transactionlog::SyncProxy(),
      _docTypeName(docTypeName),
      _baseDir(baseDir + "/" + _docTypeName.toString()),
      // Only one thread per executor, or performDropFeedView() will fail.
      _defaultExecutorTaskLimit(protonCfg.indexing.tasklimit),
      _semiUnboundExecutorTaskLimit(protonCfg.indexing.semiunboundtasklimit),
      _indexingThreads(protonCfg.indexing.threads),
      _writeService(std::max(1, protonCfg.indexing.threads),
                    indexing_thread_stack_size,
                    _defaultExecutorTaskLimit),
      _initializeThreads(initializeThreads),
      _initConfigSnapshot(),
      _initConfigSerialNum(0u),
      _pendingConfigSnapshot(configSnapshot),
      _configLock(),
      _activeConfigSnapshot(),
      _activeConfigSnapshotGeneration(0),
      _activeConfigSnapshotSerialNum(0u),
      _initGate(),
      _clusterStateHandler(_writeService.master()),
      _bucketHandler(_writeService.master()),
      _protonSummaryCfg(protonCfg.summary),
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
      _feedHandler(_writeService,
                   tlsSpec,
                   docTypeName,
                   getMetricsCollection().getLegacyMetrics().feed,
                   _state,
                   *this,
                   _writeFilter,
                   *this,
                   tlsDirectWriter),
      _historySchema(),
      _unionSchema(),
      _subDBs(*this,
              *this,
              _feedHandler,
              _docTypeName,
              _writeService,
              warmupExecutor,
              summaryExecutor,
              fileHeaderContext,
              metricsWireService,
              getMetricsCollection(),
              queryLimiter,
              clock,
              _configLock,
              _baseDir,
              protonCfg,
              hwInfo),
      _maintenanceController(_writeService.master(), _docTypeName),
      _visibility(_feedHandler, _writeService, _feedView),
      _lidSpaceCompactionHandlers(),
      _jobTrackers(),
      _lastDocStoreCacheStats(),
      _calc()
{
    assert(configSnapshot.get() != NULL);

    LOG(debug, "DocumentDB(%s): Creating database in directory '%s'",
        _docTypeName.toString().c_str(), _baseDir.c_str());

    _feedHandler.init(_config_store->getOldestSerialNum());
    _feedHandler.setBucketDBHandler(&_subDBs.getBucketDBHandler());
    saveInitialConfig(*configSnapshot);
    resumeSaveConfig();
    SerialNum configSerial = _config_store->getPrevValidSerial(
            _feedHandler.getPrunedSerialNum() + 1);
    assert(configSerial > 0);
    DocumentDBConfig::SP loaded_config;
    _config_store->loadConfig(*configSnapshot, configSerial,
                              loaded_config, _historySchema);
    // Grab relevant parts from pending config
    loaded_config = DocumentDBConfigScout::scout(loaded_config,
                                                 *_pendingConfigSnapshot.get());
    // Ignore configs that are not relevant during replay of transaction log
    loaded_config = DocumentDBConfig::makeReplayConfig(loaded_config);

    reconfigureSchema(*loaded_config, *loaded_config);
    _initConfigSnapshot = loaded_config;
    _initConfigSerialNum = configSerial;
    // Forward changes of cluster state to feed view via us
    _clusterStateHandler.addClusterStateChangedHandler(this);
    // Forward changes of cluster state to bucket handler
    _clusterStateHandler.addClusterStateChangedHandler(&_bucketHandler);
    for (auto subDb : _subDBs) {
        _lidSpaceCompactionHandlers.push_back(ILidSpaceCompactionHandler::UP
                (new LidSpaceCompactionHandler(*subDb,
                        _docTypeName.getName())));
    }
    _writeFilter.setConfig(loaded_config->getMaintenanceConfigSP()->
                           getAttributeUsageFilterConfig());
    fastos::TimeStamp visibilityDelay =
        loaded_config->getMaintenanceConfigSP()->getVisibilityDelay();
    _visibility.setVisibilityDelay(visibilityDelay);
    if (_visibility.getVisibilityDelay() > 0) {
        _writeService.setTaskLimit(semiUnboundTaskLimit(_semiUnboundExecutorTaskLimit, _indexingThreads));
    }
}

void DocumentDB::registerReferent()
{
    if (_state.getAllowReconfig()) {
        auto registry = _owner.getDocumentDBReferentRegistry();
        if (registry) {
            auto referent = _subDBs.getReadySubDB()->getDocumentDBReferent();
            if (referent) {
                registry->add(_docTypeName.getName(), referent);
            }
        }
    }
}

void DocumentDB::setActiveConfig(const DocumentDBConfig::SP &config,
                                 SerialNum serialNum, int64_t generation) {
    vespalib::LockGuard guard(_configLock);
    registerReferent();
    _activeConfigSnapshot = config;
    assert(generation >= config->getGeneration());
    if (_activeConfigSnapshotGeneration < generation) {
        _activeConfigSnapshotGeneration = generation;
    }
    _activeConfigSnapshotSerialNum = serialNum;
}

DocumentDBConfig::SP DocumentDB::getActiveConfig() const {
    vespalib::LockGuard guard(_configLock);
    return _activeConfigSnapshot;
}

void
DocumentDB::internalInit()
{
    (void) _state.enterLoadState();
    _writeService.master().execute(makeTask(makeClosure(this,
                                           &DocumentDB::initManagers)));
}


void
DocumentDB::initManagers()
{
    // Called by executor thread
    DocumentDBConfig::SP configSnapshot(_initConfigSnapshot);
    _initConfigSnapshot.reset();
    InitializerTask::SP rootTask =
        _subDBs.createInitializer(*configSnapshot, _initConfigSerialNum,
                                  _unionSchema, _protonSummaryCfg,
                                  _protonIndexCfg);
    InitializeThreads initializeThreads = _initializeThreads;
    _initializeThreads.reset();
    std::shared_ptr<TaskRunner> taskRunner(std::make_shared<TaskRunner>
                                           (*initializeThreads));
    // Note: explicit listing in lambda to keep variables live
    auto doneTask = makeLambdaTask([initializeThreads, taskRunner,
                                    configSnapshot, this]()
                                   { initFinish(configSnapshot); });
    taskRunner->runTask(rootTask, _writeService.master(), std::move(doneTask));
}


void
DocumentDB::initFinish(DocumentDBConfig::SP configSnapshot)
{
    // Called by executor thread
    _bucketHandler.setReadyBucketHandler(
            _subDBs.getReadySubDB()->getDocumentMetaStoreContext().get());
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
        vespalib::LockGuard guard(_configLock);
        if (_activeConfigSnapshot.get() == NULL) {
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
    _writeService.master().execute(makeTask(makeClosure(this,
                                           &DocumentDB::performReconfig,
                                           _pendingConfigSnapshot.get())));
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
        _feedHandler.storeOperation(op);
        sync(op.getSerialNum());
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
    applyConfig(configSnapshot, getCurrentSerialNumber());
    if (_state.getState() == DDBState::State::APPLY_LIVE_CONFIG) {
        enterReprocessState();
    }
}


void
DocumentDB::handleRejectedConfig(DocumentDBConfig::SP &configSnapshot,
                                 const ConfigValidator::Result &cvr,
                                 const DDBState::ConfigState &cs)
{
    _state.setConfigState(cs);
    if (cs == DDBState::ConfigState::NEED_RESTART) {
        LOG(warning, "DocumentDB(%s): Cannot apply new config snapshot directly: '%s'."
            " Search node must be restarted for new config to take effect",
            _docTypeName.toString().c_str(), cvr.what().c_str());
    } else {
        LOG(error, "DocumentDB(%s): Cannot apply new config snapshot, new schema is in conflict"
            " with old schema or history schema: '%s'."
            " Feed interface is disabled until old config is redeployed",
            _docTypeName.toString().c_str(), cvr.what().c_str());
    }
    LOG(info, "DocumentDB(%s): Config from config server rejected: %s",
        _docTypeName.toString().c_str(),
        (cs == DDBState::ConfigState::NEED_RESTART ? "need restart" : "feed disabled"));
    DocumentDBConfig::SP oaconfig = _activeConfigSnapshot->
                                    getOriginalConfig();
    if (!oaconfig ||
        _state.getState() != DDBState::State::APPLY_LIVE_CONFIG) {
        configSnapshot = _activeConfigSnapshot;
    } else {
        configSnapshot = oaconfig;
    }
}

namespace {
struct EmptyDocumentDBReferenceResolver : public IDocumentDBReferenceResolver {
    std::unique_ptr<ImportedAttributesRepo> resolve(const search::IAttributeManager &, const search::IAttributeManager &) override {
        return std::make_unique<ImportedAttributesRepo>();
    }
    void teardown(const search::IAttributeManager &) override { }
};
}

void
DocumentDB::applyConfig(DocumentDBConfig::SP configSnapshot,
                        SerialNum serialNum)
{
    // Always called by executor thread:
    // Called by performReconfig() by executor thread during normal
    // feed mode and when switching to normal feed mode after replay.
    // Called by replayConfig() in visitor callback by executor thread
    // when using config from transaction log.
    ConfigComparisonResult cmpres;
    Schema::SP oldSchema;
    bool fallbackConfig = false;
    int64_t generation = configSnapshot->getGeneration();
    {
        vespalib::LockGuard guard(_configLock);
        assert(_activeConfigSnapshot.get());
        if (_activeConfigSnapshot.get() == configSnapshot.get() ||
            *_activeConfigSnapshot == *configSnapshot) {
            // generation might have changed but config is unchanged.
            if (_state.getRejectedConfig()) {
                // Illegal reconfig has been reverted.
                _state.clearRejectedConfig();
                LOG(info,
                    "DocumentDB(%s): Config from config server accepted (reverted config)",
                    _docTypeName.toString().c_str());
            }
        } else {
            oldSchema = _activeConfigSnapshot->getSchemaSP();
            ConfigValidator::Result cvr =
                ConfigValidator::validate(ConfigValidator::Config
                                          (*configSnapshot->getSchemaSP(),
                                           configSnapshot->getAttributesConfig()),
                                          ConfigValidator::Config
                                          (*oldSchema, _activeConfigSnapshot->getAttributesConfig()),
                                          *_historySchema);
            DDBState::ConfigState cs = _state.calcConfigState(cvr.type());
            if (DDBState::getRejectedConfig(cs))
            {
                handleRejectedConfig(configSnapshot, cvr, cs);
                fallbackConfig = true;
            }
            cmpres = _activeConfigSnapshot->compare(*configSnapshot);
        }
    }
    const ReconfigParams params(cmpres);
    if (params.shouldSchemaChange()) {
        reconfigureSchema(*configSnapshot, *_activeConfigSnapshot);
    }
    // Save config via config manager if replay is done.
    bool equalReplayConfig =
        *DocumentDBConfig::makeReplayConfig(configSnapshot) ==
        *DocumentDBConfig::makeReplayConfig(_activeConfigSnapshot);
    assert(!fallbackConfig || equalReplayConfig);
    bool tlsReplayDone = _feedHandler.getTransactionLogReplayDone();
    if (!equalReplayConfig && tlsReplayDone) {
        sync(_feedHandler.getSerialNum());
        serialNum = _feedHandler.incSerialNum();
        _config_store->saveConfig(*configSnapshot, *_historySchema, serialNum);
        // save entry in transaction log
        NewConfigOperation op(serialNum, *_config_store);
        _feedHandler.storeOperation(op);
        sync(op.getSerialNum());
    }
    bool hasVisibilityDelayChanged = false;
    {
        bool elidedConfigSave = equalReplayConfig && tlsReplayDone;
        // Flush changes to attributes and memory index, cf. visibilityDelay
        _feedView.get()->forceCommit(elidedConfigSave ? serialNum :
                                     serialNum - 1);
        _writeService.sync();
        fastos::TimeStamp visibilityDelay =
            configSnapshot->getMaintenanceConfigSP()->getVisibilityDelay();
        hasVisibilityDelayChanged = (visibilityDelay != _visibility.getVisibilityDelay());
        _visibility.setVisibilityDelay(visibilityDelay);
    }
    if (_visibility.getVisibilityDelay() > 0) {
        _writeService.setTaskLimit(semiUnboundTaskLimit(_semiUnboundExecutorTaskLimit, _indexingThreads));
    } else {
        _writeService.setTaskLimit(_defaultExecutorTaskLimit);
    }
    if (params.shouldSubDbsChange() || hasVisibilityDelayChanged) {
        EmptyDocumentDBReferenceResolver resolver;
        _subDBs.applyConfig(*configSnapshot, *_activeConfigSnapshot, serialNum, params, resolver);
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
        setIndexSchema(*configSnapshot);
    }
    if (!fallbackConfig) { 
        if (_state.getRejectedConfig()) {
            LOG(info, "DocumentDB(%s): Rejected config replaced with new config",
                _docTypeName.toString().c_str());
        }
        _state.clearRejectedConfig();
    }
    setActiveConfig(configSnapshot, serialNum, generation);
    forwardMaintenanceConfig();
    _writeFilter.setConfig(configSnapshot->getMaintenanceConfigSP()->
                           getAttributeUsageFilterConfig());
}


void
DocumentDB::reconfigureSchema(const DocumentDBConfig &configSnapshot,
                              const DocumentDBConfig &oldConfigSnapshot)
{
    // Called by CTOR and executor thread
    const Schema &newSchema = *configSnapshot.getSchemaSP();
    const Schema &oldSchema = *oldConfigSnapshot.getSchemaSP();
    Schema::SP oldHistory = _historySchema;
    _historySchema =
        SchemaUtil::makeHistorySchema(newSchema, oldSchema, *oldHistory);
    _unionSchema = SchemaUtil::makeUnionSchema(newSchema, *_historySchema);
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

    // Feed view is kept alive in the closure's shared ptr.
    _writeService.index().execute(makeTask(makeClosure(this,
                                                &proton::DocumentDB::
                                                performDropFeedView2,
                                                feedView)));
}


void
DocumentDB::performDropFeedView2(IFeedView::SP feedView)
{
    // Called by executor task, delays when feed view is dropped.
    // Also called by DocumentDB::receive() method to keep feed view alive
    _writeService.indexFieldInverter().sync();
    _writeService.indexFieldWriter().sync();

    // Feed view is kept alive in the closure's shared ptr.
    _writeService.master().execute(makeTask(makeClosure(&doNothing, feedView)));
}


void
DocumentDB::close()
{
    {
        vespalib::LockGuard guard(_configLock);
        _state.enterShutdownState();
    }
    _writeService.master().sync(); // Complete all tasks that didn't observe shutdown
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
    _writeService.master().execute(makeTask(makeClosure(this, &DocumentDB::closeSubDBs)));
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

    vespalib::LockGuard guard(_configLock);
    if (_config_store->getBestSerialNum() != 0)
        return;				// Initial config already present

    SerialNum confSerial = _feedHandler.incSerialNum();
    // Elide save of new config entry in transaction log, it would be
    // pruned at once anyway.
    // save noop entry in transaction log
    NoopOperation op;
    _feedHandler.storeOperation(op);
    sync(op.getSerialNum());
    // Wipe everything in transaction log before initial config.
    try {
        _feedHandler.tlsPrune(confSerial);  // throws on error
    } catch (const vespalib::IllegalStateException & e) {
        LOG(warning, "DocumentDB(%s): saveInitialConfig() failed pruning due to '%s'",
            _docTypeName.toString().c_str(), e.what());
    }
    _config_store->saveConfig(configSnapshot, Schema(), confSerial);
}


void
DocumentDB::resumeSaveConfig(void)
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
    _feedHandler.storeOperation(op);
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
DocumentDB::getAllowPrune(void) const
{
    return _state.getAllowPrune();
}


bool
DocumentDB::isFeedBlockedByRejectedConfig()
{
    return _state.isFeedBlockedByRejectedConfig();
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

    LOG(debug,
        "DocumentDB(%s): Database started.",
        _docTypeName.toString().c_str());
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
DocumentDB::setIndexSchema(const DocumentDBConfig &configSnapshot)
{
    // Called by executor thread
    _subDBs.getReadySubDB()->setIndexSchema(configSnapshot.getSchemaSP(),
                                            _unionSchema);

    // TODO: Adjust tune.
}


void
DocumentDB::reconfigure(const DocumentDBConfig::SP & snapshot)
{
    _writeService.master().execute(makeTask(makeClosure(this,
                                           &DocumentDB::newConfigSnapshot,
                                           snapshot)));
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
        _feedHandler.storeOperation(op);
        sync(op.getSerialNum());
    }
    enterApplyLiveConfigState();
}


void
DocumentDB::enterApplyLiveConfigState()
{
    assert(_writeService.master().isCurrentThread());
    // Enable reconfig and queue currently pending config as executor task.
    {
        vespalib::LockGuard guard(_configLock);
        (void) _state.enterApplyLiveConfigState();
    }
    _writeService.master().execute(makeTask(makeClosure(this,
                                           &DocumentDB::performReconfig,
                                           _pendingConfigSnapshot.get())));
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
        return StatusReport::create(params.state(StatusReport::PARTIAL).
                progress(progress).
                message(msg));
    } else if (rawState == DDBState::State::APPLY_LIVE_CONFIG) {
        return StatusReport::create(params.state(StatusReport::PARTIAL).
                message("DocumentDB apply live config on startup"));
    } else if (rawState == DDBState::State::REPROCESS ||
               rawState == DDBState::State::REDO_REPROCESS)
    {
        float progress = _subDBs.getReprocessingProgress() * 100.0f;
        vespalib::string msg = make_string("DocumentDB reprocess on startup (%u%% done)",
                        static_cast<uint32_t>(progress));
        return StatusReport::create(params.state(StatusReport::PARTIAL).
                progress(progress).
                message(msg));
    } else if (_state.getRejectedConfig()) {
        return StatusReport::create(params.state(StatusReport::PARTIAL).
                message("DocumentDB rejecting config"));
    } else {
        return StatusReport::create(params.state(StatusReport::UPOK));
    }
}


void
DocumentDB::wipeHistory(void)
{
    // Called from RPC handler
    _writeService.master().execute(makeTask(makeClosure(this,
                                           &DocumentDB::performWipeHistory)));
    _writeService.master().sync();
}


void
DocumentDB::performWipeHistory()
{
    // Called by executor thread
    if (_historySchema->empty())
        return;
    if (_feedHandler.getTransactionLogReplayDone()) {
        sync(_feedHandler.getSerialNum()); // Sync before wiping history
        DocumentDBConfig::SP configSnapshot = getActiveConfig();
        SerialNum wipeSerial = _feedHandler.incSerialNum();
        Schema::UP newHistory(new Schema);
        writeWipeHistoryTransactionLogEntry(wipeSerial, 0,
                                            *configSnapshot, *newHistory);
        internalWipeHistory(wipeSerial, std::move(newHistory), *_historySchema);
    }
}


void DocumentDB::writeWipeHistoryTransactionLogEntry(
        SerialNum wipeSerial, fastos::TimeStamp wipeTimeLimit,
        const DocumentDBConfig &configSnapshot,
        const Schema &newHistorySchema) {
    // Caller must have synced transaction log
    _config_store->saveConfig(configSnapshot, newHistorySchema, wipeSerial);
    // save entry in transaction log
    WipeHistoryOperation op(wipeSerial, wipeTimeLimit);
    _feedHandler.storeOperation(op);
    sync(op.getSerialNum());
}


void
DocumentDB::internalWipeHistory(SerialNum wipeSerial,
                                Schema::UP newHistorySchema,
                                const Schema &wipeSchema)
{
    // Called by executor thread
    _subDBs.wipeHistory(wipeSerial, *newHistorySchema, wipeSchema);
    _historySchema.reset(newHistorySchema.release());
    _unionSchema = getActiveConfig()->getSchemaSP();
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
    // Load historyschema before applyConfig to preserve the history
    // field timestamps.
    _config_store->loadConfig(*configSnapshot, serialNum,
                            configSnapshot, _historySchema);
    // Grab relevant parts from pending config
    configSnapshot = DocumentDBConfigScout::scout(configSnapshot,
                                                 *_pendingConfigSnapshot.get());
    // Ignore configs that are not relevant during replay of transaction log
    configSnapshot = DocumentDBConfig::makeReplayConfig(configSnapshot);
    applyConfig(configSnapshot, serialNum);
    LOG(info,
        "DocumentDB(%s): Replayed config with serialNum=%" PRIu64,
        _docTypeName.toString().c_str(), serialNum);
}

void
DocumentDB::replayWipeHistory(search::SerialNum serialNum,
                              fastos::TimeStamp wipeTimeLimit)
{
    // Called by executor thread
    DocumentDBConfig::SP configSnapshot = getActiveConfig();
    if (configSnapshot.get() == NULL) {
        LOG(warning,
            "DocumentDB(%s): Missing old config when replaying wipe history, serialNum=%" PRIu64,
            _docTypeName.toString().c_str(),
            serialNum);
        return;
    }
    Schema::UP wipeSchemaOwner;
    Schema *wipeSchema;
    Schema::UP newHistory;
    if (wipeTimeLimit) {
        wipeSchemaOwner = _historySchema->getOldFields(wipeTimeLimit);
        wipeSchema = wipeSchemaOwner.get();
        newHistory = Schema::set_difference(*_historySchema, *wipeSchema);
    } else {  // wipeTimeLimit == 0 means old style wipeHistory.
        wipeSchema = _historySchema.get();
        newHistory.reset(new Schema);
    }
    LOG(info, "DocumentDB(%s): Replayed history wipe with serialNum=%" PRIu64,
        _docTypeName.toString().c_str(), serialNum);
    internalWipeHistory(serialNum, std::move(newHistory), *wipeSchema);
}


void
DocumentDB::listSchema(std::vector<vespalib::string> &fieldNames,
                       std::vector<vespalib::string> &fieldDataTypes,
                       std::vector<vespalib::string> &fieldCollectionTypes,
                       std::vector<vespalib::string> &fieldLocations)
{
    DocumentDBConfig::SP activeSnapshot = getActiveConfig();
    if (activeSnapshot.get() == NULL ||
        activeSnapshot->getSchemaSP().get() == NULL)
    {
        return;
    }
    SchemaUtil::listSchema(*activeSnapshot->getSchemaSP(),
                           fieldNames,
                           fieldDataTypes,
                           fieldCollectionTypes,
                           fieldLocations);
}


int64_t DocumentDB::getActiveGeneration() const {
    vespalib::LockGuard guard(_configLock);
    return _activeConfigSnapshotGeneration;
}


void
DocumentDB::syncFeedView(void)
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
            *this, // IWipeOldRemovedFieldsHandler
            _lidSpaceCompactionHandlers,
            _feedHandler, // IOperationStorer
            _maintenanceController, // IFrozenBucketHandler
            _docTypeName.getName(),
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
DocumentDB::performStartMaintenance(void)
{
    // Called by executor thread
    // Only start once, after replay done

    DocumentDBMaintenanceConfig::SP maintenanceConfig;
    {
        vespalib::LockGuard guard(_configLock);
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
DocumentDB::stopMaintenance(void)
{
    _maintenanceController.stop();
}

void
DocumentDB::forwardMaintenanceConfig(void)
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
DocumentDB::notifyClusterStateChanged(
        const IBucketStateCalculator::SP &newCalc)
{
    // Called by executor thread
    _calc = newCalc; // Save for maintenance job injection
    // Forward changes of cluster state to feed view
    IFeedView::SP feedView(_feedView.get());
    if (feedView.get() != NULL) {
        // Try downcast to avoid polluting API
        CombiningFeedView *cfv = dynamic_cast<CombiningFeedView *>
                                 (feedView.get());
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

vespalib::string
getSchemaFieldsList(const Schema &schema)
{
    vespalib::asciistream oss;
    for (uint32_t i = 0; i < schema.getNumIndexFields(); ++i) {
        if (i > 0) oss << ",";
        oss << schema.getIndexField(i).getName();
    }
    for (uint32_t i = 0; i < schema.getNumAttributeFields(); ++i) {
        if (!oss.str().empty()) oss << ",";
        oss << schema.getAttributeField(i).getName();
    }
    return oss.str();
}

}

void
DocumentDB::wipeOldRemovedFields(fastos::TimeStamp wipeTimeLimit)
{
    // Called by executor thread

    if (_historySchema->empty())
        return;
    DocumentDBConfig::SP configSnapshot = getActiveConfig();

    Schema::UP wipeSchema = _historySchema->getOldFields(wipeTimeLimit);
    Schema::UP newHistorySchema =
        Schema::set_difference(*_historySchema, *wipeSchema);

    sync(_feedHandler.getSerialNum()); // Sync before wiping history
    SerialNum wipeSerial = _feedHandler.incSerialNum();
    writeWipeHistoryTransactionLogEntry(wipeSerial, wipeTimeLimit,
                                        *configSnapshot, *newHistorySchema);
    internalWipeHistory(wipeSerial, std::move(newHistorySchema), *wipeSchema);

    LOG(debug, "DocumentDB(%s): Done wipeOldRemovedFields: wipe(%s), history(%s) timeLimit(%" PRIu64 ")",
        _docTypeName.toString().c_str(),
        getSchemaFieldsList(*wipeSchema).c_str(),
        getSchemaFieldsList(*_historySchema).c_str(),
        static_cast<uint64_t>(wipeTimeLimit.sec()));
}

searchcorespi::IIndexManagerFactory::SP
DocumentDB::getIndexManagerFactory(const vespalib::stringref &name) const
{
    return _owner.getIndexManagerFactory(name);
}

namespace {

void
updateIndexMetrics(DocumentDBMetricsCollection &metrics,
                   const search::SearchableStats &stats)
{
    DocumentDBTaggedMetrics::IndexMetrics &indexMetrics = metrics.getTaggedMetrics().index;
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
    {
    }
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
fillTempAttributeMetrics(TempAttributeMetrics &metrics,
                         const vespalib::string &attrName,
                         const MemoryUsage &memoryUsage,
                         uint32_t bitVectors)
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
        if (attrMgr.get()) {
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
updateLegacyAttributeMetrics(LegacyAttributeMetrics &metrics,
                             const TempAttributeMetrics &tmpMetrics)
{
    for (const auto &attr : tmpMetrics._attrs) {
        LegacyAttributeMetrics::List::Entry::LP entry = metrics.list.get(attr.first);
        if (entry.get()) {
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
updateAttributeMetrics(AttributeMetrics &metrics,
                       const TempAttributeMetrics &tmpMetrics)
{
    for (const auto &attr : tmpMetrics._attrs) {
        auto entry = metrics.get(attr.first);
        if (entry.get()) {
            entry->memoryUsage.update(attr.second._memoryUsage);
        }
    }
}

void
updateAttributeMetrics(DocumentDBMetricsCollection &metrics,
                       const DocumentSubDBCollection &subDbs)
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
updateMatchingMetrics(LegacyDocumentDBMetrics::MatchingMetrics &metrics,
                      const IDocumentSubDB &ready)
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
        cache_stats += summaryMgr->getBackingStore().getCacheStats();
        memoryUsage += summaryMgr->getBackingStore().memoryUsed();
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
updateLidSpaceMetrics(MetricSetType &metrics,
                      const search::IDocumentMetaStore &metaStore)
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
    updateLegacyMetrics(metrics.getLegacyMetrics());
    updateIndexMetrics(metrics, _subDBs.getReadySubDB()->getSearchableStats());
    updateAttributeMetrics(metrics, _subDBs);
    updateMetrics(metrics.getTaggedMetrics());
}

void
DocumentDB::updateLegacyMetrics(LegacyDocumentDBMetrics &metrics)
{
    updateMatchingMetrics(metrics.matching, *_subDBs.getReadySubDB());
    metrics.executor.update(_writeService.getMasterExecutor().getStats());
    metrics.indexExecutor.update(_writeService.getIndexExecutor().getStats());
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

    metrics.numBadConfigs.set(_state.getRejectedConfig() ? 1u : 0u);
}

void
DocumentDB::
updateMetrics(DocumentDBTaggedMetrics::AttributeMetrics &metrics)
{
    AttributeUsageFilter &writeFilter(_writeFilter);
    AttributeUsageStats attributeUsageStats =
        writeFilter.getAttributeUsageStats();
    bool feedBlocked = !writeFilter.acceptWriteOperation();
    double enumStoreUsed =
        attributeUsageStats.enumStoreUsage().getUsage().usage();
    double multiValueUsed =
        attributeUsageStats.multiValueUsage().getUsage().usage();
    metrics.resourceUsage.enumStore.set(enumStoreUsed);
    metrics.resourceUsage.multiValue.set(multiValueUsed);
    metrics.resourceUsage.feedingBlocked.set(feedBlocked ? 1 : 0);
}

void
DocumentDB::updateMetrics(DocumentDBTaggedMetrics &metrics)
{
    _jobTrackers.updateMetrics(metrics.job);

    updateMetrics(metrics.attribute);
    updateDocumentStoreMetrics(metrics.ready.documentStore,
                               _subDBs.getReadySubDB());
    updateDocumentStoreMetrics(metrics.removed.documentStore,
                               _subDBs.getRemSubDB());
    updateDocumentStoreMetrics(metrics.notReady.documentStore,
                               _subDBs.getNotReadySubDB());
    DocumentMetaStoreReadGuards dmss(_subDBs);
    updateLidSpaceMetrics(metrics.ready.lidSpace, dmss.readydms->get());
    updateLidSpaceMetrics(metrics.notReady.lidSpace, dmss.notreadydms->get());
    updateLidSpaceMetrics(metrics.removed.lidSpace, dmss.remdms->get());
}

void
DocumentDB::sync(SerialNum syncTo)
{
    LOG(spam,
        "DocumentDB(%s): sync(): serialNum=%" PRIu64,
        _docTypeName.toString().c_str(), syncTo);
    _feedHandler.syncTls(syncTo);
}


void
DocumentDB::waitForOnlineState()
{
    _state.waitForOnlineState();
}

uint32_t
DocumentDB::getDistributionKey() const
{
    return _owner.getDistributionKey();
}


} // namespace proton
