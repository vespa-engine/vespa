// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once


#include "buckethandler.h"
#include "clusterstatehandler.h"
#include "configstore.h"
#include "ddbstate.h"
#include "documentdbconfig.h"
#include "documentsubdbcollection.h"
#include "feedhandler.h"
#include "i_lid_space_compaction_handler.h"
#include "ifeedview.h"
#include "ireplayconfig.h"
#include "iwipeoldremovedfieldshandler.h"
#include "maintenancecontroller.h"
#include "protonconfigurer.h"
#include "searchable_doc_subdb_configurer.h"
#include "searchabledocsubdb.h"
#include "summaryadapter.h"
#include "visibilityhandler.h"

#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/statusreport.h>
#include <vespa/searchcore/proton/common/monitored_refcount.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/i_document_meta_store.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>
#include <vespa/searchcore/proton/metrics/documentdb_metrics_collection.h>
#include <vespa/searchcore/proton/persistenceengine/bucket_guard.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/vespalib/util/varholder.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include "disk_mem_usage_forwarder.h"
#include <vespa/metrics/updatehook.h>

using vespa::config::search::core::ProtonConfig;

namespace search
{

namespace common
{

class FileHeaderContext;

}

namespace transactionlog { class TransLogClient; }
}  // namespace search

namespace proton {
class MetricsWireService;
class IDocumentDBOwner;

/**
 * The document database contains all the necessary structures required per
 * document type. It has an internal single-threaded Executor to process input
 * to ensure that there are never multiple writers. Unless explicitly stated,
 * none of the methods of this class are thread-safe.
 */
class DocumentDB : public IDocumentDBConfigOwner,
                   public IReplayConfig,
                   public FeedHandler::IOwner,
                   public IDocumentSubDB::IOwner,
                   public IClusterStateChangedHandler,
                   public IWipeOldRemovedFieldsHandler,
                   public search::transactionlog::SyncProxy,
                   public MonitoredRefCount
{
private:
    class MetricsUpdateHook : public metrics::UpdateHook {
        DocumentDBMetricsCollection _metrics;
        DocumentDB &_db;
    public:
        MetricsUpdateHook(DocumentDB &s, const std::string &doc_type, size_t maxNumThreads)
            : metrics::UpdateHook("documentdb-hook"),
              _metrics(doc_type, maxNumThreads),
              _db(s) {}
        void updateMetrics(const MetricLockGuard & ) override { _db.updateMetrics(_metrics); }
        DocumentDBMetricsCollection &getMetrics() { return _metrics; }
    };


    using InitializeThreads = std::shared_ptr<vespalib::ThreadStackExecutorBase>;

    DocTypeName                   _docTypeName;
    vespalib::string              _baseDir;
    uint32_t                      _defaultExecutorTaskLimit;
    uint32_t                      _semiUnboundExecutorTaskLimit;
    uint32_t                      _indexingThreads;
    // Only one thread per executor, or dropFeedView() will fail.
    ExecutorThreadingService      _writeService;
    // threads for initializer tasks during proton startup
    InitializeThreads             _initializeThreads;

    typedef search::SerialNum      SerialNum;
    typedef fastos::TimeStamp      TimeStamp;
    typedef vespalib::Closure      Closure;
    typedef search::index::Schema  Schema;
    // variables related to reconfig
    DocumentDBConfig::SP          _initConfigSnapshot;
    SerialNum                     _initConfigSerialNum;
    vespalib::VarHolder<DocumentDBConfig::SP> _pendingConfigSnapshot;
    vespalib::Lock                _configLock;  // protects _active* below.
    DocumentDBConfig::SP          _activeConfigSnapshot;
    int64_t                       _activeConfigSnapshotGeneration;
    SerialNum                     _activeConfigSnapshotSerialNum;

    vespalib::Gate                _initGate;

    typedef DocumentDBConfig::ComparisonResult ConfigComparisonResult;

    ClusterStateHandler           _clusterStateHandler;
    BucketHandler                 _bucketHandler;
    ProtonConfig::Summary         _protonSummaryCfg;
    ProtonConfig::Index           _protonIndexCfg;
    ConfigStore::UP               _config_store;
    matching::SessionManager::SP  _sessionManager; // TODO: This should not have to be a shared pointer.
    MetricsWireService             &_metricsWireService;
    MetricsUpdateHook             _metricsHook;
    vespalib::VarHolder<IFeedView::SP>      _feedView;
    bool                          _syncFeedViewEnabled;
    IDocumentDBOwner             &_owner;
    DDBState                      _state;
    DiskMemUsageForwarder         _dmUsageForwarder;
    AttributeUsageFilter          _writeFilter;
    FeedHandler                   _feedHandler;

    // Members only accessed by executor thread
    // (+ ctor and after executor stops)
    Schema::SP                    _historySchema; // Removed fields
    // current schema + _historySchema
    Schema::SP                    _unionSchema;
    // End members only accessed by executor thread.

    DocumentSubDBCollection       _subDBs;
    MaintenanceController         _maintenanceController;
    VisibilityHandler             _visibility;
    ILidSpaceCompactionHandler::Vector _lidSpaceCompactionHandlers;
    DocumentDBJobTrackers         _jobTrackers;

    // Last updated cache statistics. Necessary due to metrics implementation is upside down.
    search::CacheStats            _lastDocStoreCacheStats;
    IBucketStateCalculator::SP    _calc;

    void registerReferent();
    void setActiveConfig(const DocumentDBConfig::SP &config, SerialNum serialNum, int64_t generation);
    DocumentDBConfig::SP getActiveConfig() const;
    void internalInit();
    void initManagers();
    void initFinish(DocumentDBConfig::SP configSnapshot);
    void performReconfig(DocumentDBConfig::SP configSnapshot);
    void closeSubDBs();

    void
    handleRejectedConfig(DocumentDBConfig::SP &configSnapshot,
                         const ConfigValidator::Result &cvr,
                         const DDBState::ConfigState &cs);
    void applyConfig(DocumentDBConfig::SP configSnapshot, SerialNum serialNum);

    /**
     * Save initial config if we don't have any saved config snapshots.
     *
     * @param configSnapshot initial config snapshot.
     */
    void saveInitialConfig(const DocumentDBConfig &configSnapshot);

    /**
     * Resume interrupted config save if needed.
     */
    void resumeSaveConfig(void);

    void
    reconfigureSchema(const DocumentDBConfig &configSnapshot,
                      const DocumentDBConfig &oldConfigSnapshot);

    void setIndexSchema(const DocumentDBConfig &configSnapshot);

    /**
     * Redo interrupted reprocessing if last entry in transaction log
     * is a config change.
     */
    virtual void enterRedoReprocessState();
    void enterApplyLiveConfigState();

    /**
     * Drop old field view in a controlled manner.  The feed view will
     * be kept alive until the index executor is done with all current
     * tasks.
     *
     * Called by executor thread.
     *
     * @param feedView	shared pointer to feed view to be dropped.
     */
    void performDropFeedView(IFeedView::SP feedView);
    void performDropFeedView2(IFeedView::SP feedView);

    /**
     * Implements FeedHandler::IOwner
     */
    virtual void onTransactionLogReplayDone() __attribute__((noinline));
    virtual void onPerformPrune(SerialNum flushedSerial);
    virtual bool isFeedBlockedByRejectedConfig();

    /**
     * Implements FeedHandler::IOwner
     **/
    virtual void performWipeHistory();
    virtual bool getAllowPrune(void) const;

    void
    writeWipeHistoryTransactionLogEntry(
            SerialNum wipeSerial,
            TimeStamp wipeTimeLimit,
            const DocumentDBConfig &configSnapshot,
            const Schema &newHistorySchema);

    void internalWipeHistory(SerialNum wipeSerial, Schema::UP newHistorySchema, const Schema &wipeSchema);

    void startTransactionLogReplay();


    /**
     * Implements IClusterStateChangedHandler
     */
    virtual void notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc);
    void notifyAllBucketsChanged();

    /**
     * Implements IWipeOldRemovedFieldsHandler
     */
    virtual void wipeOldRemovedFields(TimeStamp wipeTimeLimit);
    void updateLegacyMetrics(LegacyDocumentDBMetrics &metrics);
    void updateMetrics(DocumentDBTaggedMetrics &metrics);
    void updateMetrics(DocumentDBTaggedMetrics::AttributeMetrics &metrics);

public:
    typedef std::unique_ptr<DocumentDB> UP;
    typedef std::shared_ptr<DocumentDB> SP;

    /**
     * Constructs a new document database for the given document type.
     *
     * @param baseDir The base directory to use for persistent data.
     * @param configId The config id used to subscribe to config for this
     *                 database.
     * @param tlsSpec The frt connection spec for the TLS.
     * @param docType The document type that this database will handle.
     * @param docMgrCfg Current document manager config
     * @param docMgrSP  The document manager holding the document type.
     * @param protonCfg The global proton config this database is a part of.
     * @param tuneFileDocumentDB file tune config for this database.
     * @param config_store Access to read and write configs.
     */
    DocumentDB(const vespalib::string &baseDir,
               const DocumentDBConfig::SP & currentSnapshot,
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
               const search::common::FileHeaderContext &fileHeaderContext,
               ConfigStore::UP config_store,
               InitializeThreads initializeThreads,
               const HwInfo &hwInfo);

    /**
     * Expose a cost view of the session manager. This is used by the
     * document db explorer.
     **/
    const matching::SessionManager &session_manager() const {
        return *_sessionManager;
    }

    /**
     * Frees any allocated resources. This will also stop the internal thread
     * and wait for it to finish. All pending tasks are deleted.
     */
    ~DocumentDB();

    /**
     * Starts initialization of the document db in the init & executor threads,
     * and after that replay of the transaction log.
     * Should be used during normal startup.
     */
    void start();

    /**
     * Used to wait for init completion without also waiting for a
     * full replay to complete.
     **/
    void waitForInitDone();

    /**
     Close down all threads and make sure everything is ready to be shutdown.
     */
    void close();

    /**
     * Obtain the metrics collection for this document db.
     *
     * @return document db metrics
     **/
    DocumentDBMetricsCollection &getMetricsCollection() { return _metricsHook.getMetrics(); }

    /**
     * Obtain the metrics update hook for this document db.
     *
     * @return metrics update hook
     **/
    metrics::UpdateHook & getMetricsUpdateHook(void) {
        return _metricsHook;
    }

    /**
     * Returns the number of documents that are contained in this database.
     *
     * @return The document count.
     */
    size_t getNumDocs() const;

    /**
     * Returns the number of documents that are active for search in this database.
     *
     * @return The active-document count.
     */
    size_t getNumActiveDocs() const;

    /**
     * Returns the base directory that this document database uses when
     * persisting data to disk.
     *
     * @return The directory name.
     */
    const vespalib::string &getBaseDirectory() const { return _baseDir; }


    const DocumentSubDBCollection &getDocumentSubDBs() const { return _subDBs; }
    IDocumentSubDB *getReadySubDB() { return _subDBs.getReadySubDB(); }
    const IDocumentSubDB *getReadySubDB() const { return _subDBs.getReadySubDB(); }

    bool hasDocument(const document::DocumentId &id);

    /**
     * Returns the feed handler for this database.
     */
    FeedHandler & getFeedHandler(void) { return _feedHandler; }

    /**
     * Returns the bucket handler for this database.
     */
    BucketHandler & getBucketHandler(void) { return _bucketHandler; }

    /**
     * Returns the cluster state handler for this database.
     */
    ClusterStateHandler & getClusterStateHandler() { return _clusterStateHandler; }

    /**
     * Create a set of document retrievers for this database. Note
     * that the returned objects will not retain/release the database,
     * and may only be used as long as the database is retained by
     * some other means. The returned objects will protect from
     * reconfiguration, however.
     */
    std::shared_ptr<std::vector<IDocumentRetriever::SP> >
    getDocumentRetrievers(IDocumentRetriever::ReadConsistency consistency);

    MaintenanceController &getMaintenanceController() {
        return _maintenanceController;
    }

    BucketGuard::UP lockBucket(const document::BucketId &bucket);

    virtual SerialNum getOldestFlushedSerial();

    virtual SerialNum getNewestFlushedSerial();

    std::unique_ptr<search::engine::SearchReply>
    match(const ISearchHandler::SP &searchHandler,
          const search::engine::SearchRequest &req,
          vespalib::ThreadBundle &threadBundle) const;

    std::unique_ptr<search::engine::DocsumReply>
    getDocsums(const search::engine::DocsumRequest & request);

    IFlushTarget::List getFlushTargets();
    void flushDone(SerialNum flushedSerial);

    virtual SerialNum
    getCurrentSerialNumber() const
    {
	// Called by flush scheduler thread, by executor task or
	// visitor callback.
        // XXX: Contains future value during replay.
        return _feedHandler.getSerialNum();
    }

    StatusReport::UP reportStatus() const;

    bool getRejectedConfig() const { return _state.getRejectedConfig(); }
    void wipeHistory(void);

    /**
     * Implements IReplayConfig API.
     */
    virtual void replayConfig(SerialNum serialNum);

    virtual void replayWipeHistory(SerialNum serialNum, TimeStamp wipeTimeLimit);

    const DocTypeName & getDocTypeName(void) const { return _docTypeName; }

    void
    listSchema(std::vector<vespalib::string> &fieldNames,
               std::vector<vespalib::string> &fieldDataTypes,
               std::vector<vespalib::string> &fieldCollectionTypes,
               std::vector<vespalib::string> &fieldLocations);

    void newConfigSnapshot(DocumentDBConfig::SP snapshot);

    // Implements DocumentDBConfigOwner
    void reconfigure(const DocumentDBConfig::SP & snapshot);

    int64_t getActiveGeneration() const;

    // Implements IDocSubDB::IOwner
    void syncFeedView() override;

    std::shared_ptr<searchcorespi::IIndexManagerFactory>
    getIndexManagerFactory(const vespalib::stringref & name) const override;

    vespalib::string getName() const override { return _docTypeName.getName(); }
    uint32_t getDistributionKey() const override;

    /**
     * Implements FeedHandler::IOwner
     **/
    void injectMaintenanceJobs(const DocumentDBMaintenanceConfig &config);
    void performStartMaintenance(void);
    void stopMaintenance(void);
    void forwardMaintenanceConfig(void);

    /**
     * Updates metrics collection object, and resets executor stats.
     * Called by the metrics update hook (typically in the context of
     * the metric manager). Do not call this function in multiple
     * threads at once.
     **/
    void updateMetrics(DocumentDBMetricsCollection &metrics);

    /**
     * Implement search::transactionlog::SyncProxy API.
     *
     * Sync transaction log to syncTo.
     */
    virtual void sync(SerialNum syncTo);
    void enterReprocessState();
    void enterOnlineState();
    void waitForOnlineState();
    IDiskMemUsageListener *diskMemUsageListener() { return &_dmUsageForwarder; }
};

} // namespace proton

