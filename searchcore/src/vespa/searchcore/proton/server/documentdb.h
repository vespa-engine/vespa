// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "buckethandler.h"
#include "clusterstatehandler.h"
#include "configstore.h"
#include "ddbstate.h"
#include "disk_mem_usage_forwarder.h"
#include "documentdb_metrics_updater.h"
#include "document_db_config_owner.h"
#include "documentdbconfig.h"
#include "documentsubdbcollection.h"
#include "executorthreadingservice.h"
#include "i_document_subdb_owner.h"
#include "i_feed_handler_owner.h"
#include "i_lid_space_compaction_handler.h"
#include "ifeedview.h"
#include "ireplayconfig.h"
#include "maintenancecontroller.h"
#include "threading_service_config.h"
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/common/monitored_refcount.h>
#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>
#include <vespa/searchcore/proton/persistenceengine/bucket_guard.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchcore/proton/index/indexmanager.h>
#include <vespa/searchlib/docstore/cachestats.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/vespalib/util/varholder.h>
#include <mutex>
#include <condition_variable>

namespace search {
    namespace common { class FileHeaderContext; }
    namespace transactionlog {
        class TransLogClient;
        class WriterFactory;
    }
}

namespace vespa::config::search::core::internal { class InternalProtonType; }
namespace metrics {
    class UpdateHook;
    class MetricLockGuard;
}
namespace storage::spi { struct BucketExecutor; }

namespace proton {
class AttributeConfigInspector;
class IDocumentDBOwner;
class ITransientMemoryUsageProvider;
struct MetricsWireService;
class StatusReport;
class ExecutorThreadingServiceStats;
class TransientMemoryUsageProvider;

namespace matching { class SessionManager; }

/**
 * The document database contains all the necessary structures required per
 * document type. It has an internal single-threaded Executor to process input
 * to ensure that there are never multiple writers. Unless explicitly stated,
 * none of the methods of this class are thread-safe.
 */
class DocumentDB : public DocumentDBConfigOwner,
                   public IReplayConfig,
                   public IFeedHandlerOwner,
                   public IDocumentSubDBOwner,
                   public IClusterStateChangedHandler,
                   public search::transactionlog::SyncProxy
{
private:
    using InitializeThreads = std::shared_ptr<vespalib::SyncableThreadExecutor>;
    using IFlushTargetList = std::vector<std::shared_ptr<searchcorespi::IFlushTarget>>;
    using StatusReportUP = std::unique_ptr<StatusReport>;
    using ProtonConfig = const vespa::config::search::core::internal::InternalProtonType;

    DocTypeName                   _docTypeName;
    document::BucketSpace         _bucketSpace;
    vespalib::string              _baseDir;
    ThreadingServiceConfig        _writeServiceConfig;
    // Only one thread per executor, or dropFeedView() will fail.
    ExecutorThreadingService      _writeService;
    // threads for initializer tasks during proton startup
    InitializeThreads             _initializeThreads;

    typedef search::SerialNum      SerialNum;
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
    const bool                    _validateAndSanitizeDocStore;

    vespalib::Gate                _initGate;

    typedef DocumentDBConfig::ComparisonResult ConfigComparisonResult;

    ClusterStateHandler           _clusterStateHandler;
    BucketHandler                 _bucketHandler;
    index::IndexConfig            _indexCfg;
    ConfigStore::UP               _config_store;
    std::shared_ptr<matching::SessionManager>  _sessionManager; // TODO: This should not have to be a shared pointer.
    MetricsWireService            &_metricsWireService;
    DocumentDBTaggedMetrics        _metrics;
    std::unique_ptr<metrics::UpdateHook>  _metricsHook;
    vespalib::VarHolder<IFeedView::SP>    _feedView;
    MonitoredRefCount             _refCount;
    bool                          _syncFeedViewEnabled;
    IDocumentDBOwner             &_owner;
    storage::spi::BucketExecutor &_bucketExecutor;
    DDBState                      _state;
    DiskMemUsageForwarder         _dmUsageForwarder;
    AttributeUsageFilter          _writeFilter;
    std::shared_ptr<TransientMemoryUsageProvider> _transient_memory_usage_provider;
    std::unique_ptr<FeedHandler>                  _feedHandler;
    DocumentSubDBCollection                       _subDBs;
    MaintenanceController                         _maintenanceController;
    ILidSpaceCompactionHandler::Vector            _lidSpaceCompactionHandlers;
    DocumentDBJobTrackers                         _jobTrackers;
    std::shared_ptr<IBucketStateCalculator>       _calc;
    DocumentDBMetricsUpdater                      _metricsUpdater;

    void registerReference();
    void setActiveConfig(const DocumentDBConfig::SP &config, int64_t generation);
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
    void onTransactionLogReplayDone() override __attribute__((noinline));
    void onPerformPrune(SerialNum flushedSerial) override;

    /**
     * Implements IFeedHandlerOwner
     **/
    bool getAllowPrune() const override;
    void startTransactionLogReplay();


    /**
     * Implements IClusterStateChangedHandler
     */
    void notifyClusterStateChanged(const std::shared_ptr<IBucketStateCalculator> &newCalc) override;
    void notifyAllBucketsChanged();

    /*
     * Tear down references to this document db (e.g. listeners for
     * gid to lid changes) from other document dbs.
     */
    void tearDownReferences();

    void syncFeedView();

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
               vespalib::SyncableThreadExecutor &warmupExecutor,
               vespalib::ThreadStackExecutorBase &sharedExecutor,
               storage::spi::BucketExecutor & bucketExecutor,
               const search::transactionlog::WriterFactory &tlsWriterFactory,
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
    ~DocumentDB() override;

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
     * Obtain the metrics for this document db.
     *
     * @return document db metrics
     **/
    DocumentDBTaggedMetrics &getMetrics() {
        return _metrics;
    }

    /**
     * Obtain the metrics update hook for this document db.
     *
     * @return metrics update hook
     **/
    metrics::UpdateHook & getMetricsUpdateHook() {
        return *_metricsHook;
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
    FeedHandler & getFeedHandler() { return *_feedHandler; }

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
    match(const search::engine::SearchRequest &req, vespalib::ThreadBundle &threadBundle) const;

    std::unique_ptr<search::engine::DocsumReply>
    getDocsums(const search::engine::DocsumRequest & request);

    IFlushTargetList getFlushTargets();
    void flushDone(SerialNum flushedSerial);
    virtual SerialNum getCurrentSerialNumber() const;
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
    document::BucketSpace getBucketSpace() const override;
    vespalib::string getName() const override;
    uint32_t getDistributionKey() const override;

    /**
     * Implements IFeedHandlerOwner
     **/
    void injectMaintenanceJobs(const DocumentDBMaintenanceConfig &config, std::unique_ptr<const AttributeConfigInspector> attribute_config_inspector);
    void performStartMaintenance();
    void stopMaintenance();
    void forwardMaintenanceConfig();

    /**
     * Updates metrics collection object, and resets executor stats.
     * Called by the metrics update hook (typically in the context of
     * the metric manager). Do not call this function in multiple
     * threads at once.
     **/
    void updateMetrics(const metrics::MetricLockGuard & guard);

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
    std::shared_ptr<const ITransientMemoryUsageProvider> transient_memory_usage_provider();
    ExecutorThreadingService & getWriteService() { return _writeService; }

    void set_attribute_usage_listener(std::unique_ptr<IAttributeUsageListener> listener);
};

} // namespace proton
