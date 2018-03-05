// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "buckethandler.h"
#include "clusterstatehandler.h"
#include "configstore.h"
#include "ddbstate.h"
#include "disk_mem_usage_forwarder.h"
#include "documentdbconfig.h"
#include "documentsubdbcollection.h"
#include "executorthreadingservice.h"
#include "feedhandler.h"
#include "i_document_db_config_owner.h"
#include "i_document_subdb_owner.h"
#include "i_feed_handler_owner.h"
#include "i_lid_space_compaction_handler.h"
#include "ifeedview.h"
#include "ireplayconfig.h"
#include "maintenancecontroller.h"
#include "threading_service_config.h"
#include "visibilityhandler.h"

#include <vespa/metrics/updatehook.h>
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/monitored_refcount.h>
#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>
#include <vespa/searchcore/proton/metrics/documentdb_metrics_collection.h>
#include <vespa/searchcore/proton/persistenceengine/bucket_guard.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/vespalib/util/varholder.h>
#include <mutex>
#include <condition_variable>

namespace search {
    namespace common { class FileHeaderContext; }
    namespace transactionlog { class TransLogClient; }
}

namespace proton {
class IDocumentDBOwner;
class MetricsWireService;
class StatusReport;
class ExecutorThreadingServiceStats;

namespace matching { class SessionManager; }

/**
 * The document database contains all the necessary structures required per
 * document type. It has an internal single-threaded Executor to process input
 * to ensure that there are never multiple writers. Unless explicitly stated,
 * none of the methods of this class are thread-safe.
 */
class DocumentDB : public IDocumentDBConfigOwner,
                   public IReplayConfig,
                   public IFeedHandlerOwner,
                   public IDocumentSubDBOwner,
                   public IClusterStateChangedHandler,
                   public search::transactionlog::SyncProxy
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
    using IFlushTargetList = std::vector<std::shared_ptr<searchcorespi::IFlushTarget>>;
    using StatusReportUP = std::unique_ptr<StatusReport>;
    using ProtonConfig = vespa::config::search::core::ProtonConfig;

    DocTypeName                   _docTypeName;
    document::BucketSpace         _bucketSpace;
    vespalib::string              _baseDir;
    ThreadingServiceConfig        _writeServiceConfig;
    // Only one thread per executor, or dropFeedView() will fail.
    ExecutorThreadingService      _writeService;
    // threads for initializer tasks during proton startup
    InitializeThreads             _initializeThreads;

    typedef search::SerialNum      SerialNum;
    typedef fastos::TimeStamp      TimeStamp;
    typedef vespalib::Closure      Closure;
    typedef search::index::Schema  Schema;
    using lock_guard = std::lock_guard<std::mutex>;
    // variables related to reconfig
    DocumentDBConfig::SP          _initConfigSnapshot;
    SerialNum                     _initConfigSerialNum;
    vespalib::VarHolder<DocumentDBConfig::SP> _pendingConfigSnapshot;
    mutable std::mutex            _configMutex;  // protects _active* below.
    mutable std::condition_variable _configCV;
    DocumentDBConfig::SP          _activeConfigSnapshot;
    int64_t                       _activeConfigSnapshotGeneration;
    SerialNum                     _activeConfigSnapshotSerialNum;

    vespalib::Gate                _initGate;

    typedef DocumentDBConfig::ComparisonResult ConfigComparisonResult;

    ClusterStateHandler           _clusterStateHandler;
    BucketHandler                 _bucketHandler;
    ProtonConfig::Index           _protonIndexCfg;
    ConfigStore::UP               _config_store;
    std::shared_ptr<matching::SessionManager>  _sessionManager; // TODO: This should not have to be a shared pointer.
    MetricsWireService             &_metricsWireService;
    MetricsUpdateHook             _metricsHook;
    vespalib::VarHolder<IFeedView::SP> _feedView;
    MonitoredRefCount             _refCount;
    bool                          _syncFeedViewEnabled;
    IDocumentDBOwner             &_owner;
    DDBState                      _state;
    DiskMemUsageForwarder         _dmUsageForwarder;
    AttributeUsageFilter          _writeFilter;
    FeedHandler                   _feedHandler;

    DocumentSubDBCollection       _subDBs;
    MaintenanceController         _maintenanceController;
    VisibilityHandler             _visibility;
    ILidSpaceCompactionHandler::Vector _lidSpaceCompactionHandlers;
    DocumentDBJobTrackers         _jobTrackers;

    // Last updated cache statistics. Necessary due to metrics implementation is upside down.
    search::CacheStats            _lastDocStoreCacheStats;
    IBucketStateCalculator::SP    _calc;

    void registerReference();
    void setActiveConfig(const DocumentDBConfig::SP &config, SerialNum serialNum, int64_t generation);
    DocumentDBConfig::SP getActiveConfig() const;
    void internalInit();
    void initManagers();
    void initFinish(DocumentDBConfig::SP configSnapshot);
    void performReconfig(DocumentDBConfig::SP configSnapshot);
    void closeSubDBs();

    void applySubDBConfig(const DocumentDBConfig &newConfigSnapshot,
                          SerialNum serialNum, const ReconfigParams &params);
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
    void resumeSaveConfig();

    void setIndexSchema(const DocumentDBConfig &configSnapshot, SerialNum serialNum);

    /**
     * Redo interrupted reprocessing if last entry in transaction log
     * is a config change.
     */
    void enterRedoReprocessState() override;
    void enterApplyLiveConfigState();

    /**
     * Drop old field view in a controlled manner.  The feed view will
     * be kept alive until the index executor is done with all current
     * tasks.
     *
     * Called by executor thread.
     *
     * @param feedView  shared pointer to feed view to be dropped.
     */
    void performDropFeedView(IFeedView::SP feedView);
    void performDropFeedView2(IFeedView::SP feedView);

    /**
     * Implements IFeedHandlerOwner
     */
    virtual void onTransactionLogReplayDone() override __attribute__((noinline));
    virtual void onPerformPrune(SerialNum flushedSerial) override;

    /**
     * Implements IFeedHandlerOwner
     **/
    virtual bool getAllowPrune() const override;

    void startTransactionLogReplay();


    /**
     * Implements IClusterStateChangedHandler
     */
    virtual void notifyClusterStateChanged(const IBucketStateCalculator::SP &newCalc) override;
    void notifyAllBucketsChanged();

    void updateLegacyMetrics(LegacyDocumentDBMetrics &metrics, const ExecutorThreadingServiceStats &threadingServiceStats);
    void updateMetrics(DocumentDBTaggedMetrics &metrics, const ExecutorThreadingServiceStats &threadingServiceStats);
    void updateMetrics(DocumentDBTaggedMetrics::AttributeMetrics &metrics);

    /*
     * Tear down references to this document db (e.g. listeners for
     * gid to lid changes) from other document dbs.
     */
    void tearDownReferences();

    template <typename FunctionType>
    inline void masterExecute(FunctionType &&function);

    // Invokes initFinish() on self
    friend class InitDoneTask;

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
               const DocumentDBConfig::SP &currentSnapshot,
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
    metrics::UpdateHook & getMetricsUpdateHook() {
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
    FeedHandler & getFeedHandler() { return _feedHandler; }

    /**
     * Returns the bucket handler for this database.
     */
    BucketHandler & getBucketHandler() { return _bucketHandler; }

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

    IFlushTargetList getFlushTargets();
    void flushDone(SerialNum flushedSerial);

    virtual SerialNum
    getCurrentSerialNumber() const
    {
        // Called by flush scheduler thread, by executor task or
        // visitor callback.
        // XXX: Contains future value during replay.
        return _feedHandler.getSerialNum();
    }

    StatusReportUP reportStatus() const;

    /**
     * Reference counting
     */
    void retain() { _refCount.retain(); }
    void release() { _refCount.release(); }

    bool getDelayedConfig() const { return _state.getDelayedConfig(); }
    void replayConfig(SerialNum serialNum) override;
    const DocTypeName & getDocTypeName() const { return _docTypeName; }
    void newConfigSnapshot(DocumentDBConfig::SP snapshot);
    void reconfigure(const DocumentDBConfig::SP & snapshot) override;
    int64_t getActiveGeneration() const;
    /*
     * Implements IDocumentSubDBOwner
     */
    void syncFeedView() override;
    document::BucketSpace getBucketSpace() const override;
    vespalib::string getName() const override;
    uint32_t getDistributionKey() const override;

    /**
     * Implements IFeedHandlerOwner
     **/
    void injectMaintenanceJobs(const DocumentDBMaintenanceConfig &config);
    void performStartMaintenance();
    void stopMaintenance();
    void forwardMaintenanceConfig();

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
    void sync(SerialNum syncTo) override;
    void enterReprocessState();
    void enterOnlineState();
    void waitForOnlineState();
    IDiskMemUsageListener *diskMemUsageListener() { return &_dmUsageForwarder; }
};

} // namespace proton
