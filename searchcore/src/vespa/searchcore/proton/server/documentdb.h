// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include "buckethandler.h"
#include "clusterstatehandler.h"
#include "configstore.h"
#include "ddbstate.h"
#include "disk_mem_usage_forwarder.h"
#include "document_db_config_owner.h"
#include "documentdb_metrics_updater.h"
#include "documentsubdbcollection.h"
#include "executorthreadingservice.h"
#include "i_document_subdb_owner.h"
#include "i_feed_handler_owner.h"
#include "ifeedview.h"
#include "ireplayconfig.h"
#include "maintenancecontroller.h"
#include "threading_service_config.h"
#include <vespa/searchcore/proton/attribute/attribute_usage_filter.h>
#include <vespa/searchcore/proton/common/doctypename.h>
#include <vespa/searchcore/proton/index/indexmanager.h>
#include <vespa/searchcore/proton/metrics/documentdb_job_trackers.h>
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchlib/transactionlog/syncproxy.h>
#include <vespa/vespalib/stllike/cache_stats.h>
#include <vespa/vespalib/util/retain_guard.h>
#include <vespa/vespalib/util/varholder.h>
#include <mutex>
#include <condition_variable>

namespace vespalib {
    struct ThreadBundle;
}
namespace search {
    namespace attribute { class Interlock; }
    namespace common { class FileHeaderContext; }
    namespace transactionlog {
        class TransLogClient;
        class WriterFactory;
    }
    namespace engine {
        class SearchReply;
        class SearchRequest;
        class DocsumReply;
        class DocsumRequest;
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
class DocumentDBReconfig;
class ExecutorThreadingServiceStats;
class IDocumentDBOwner;
class ISharedThreadingService;
class ITransientResourceUsageProvider;
class ReplayThrottlingPolicy;
class StatusReport;
struct MetricsWireService;
class DocumentDBMaintenanceConfig;

namespace matching { class SessionManager; }

struct ActiveDocs {
    ActiveDocs() noexcept : active(0), target_active(0) { }
    ActiveDocs(size_t active_in, size_t target_active_in) noexcept : active(active_in), target_active(target_active_in) { }
    ActiveDocs & operator += (const ActiveDocs & b) {
        active += b.active;
        target_active += b.target_active;
        return *this;
    }
    size_t active;
    size_t target_active;
};

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
                   public search::transactionlog::SyncProxy,
                   public std::enable_shared_from_this<DocumentDB>
{
private:
    using InitializeThreads = std::shared_ptr<vespalib::ThreadExecutor>;
    using IFlushTargetList = std::vector<std::shared_ptr<searchcorespi::IFlushTarget>>;
    using StatusReportUP = std::unique_ptr<StatusReport>;
    using ProtonConfig = const vespa::config::search::core::internal::InternalProtonType;
    using lock_guard = std::lock_guard<std::mutex>;
    using SerialNum = search::SerialNum;
    using Schema = search::index::Schema;
    using DocumentDBConfigSP = std::shared_ptr<DocumentDBConfig>;

    DocTypeName                   _docTypeName;
    document::BucketSpace         _bucketSpace;
    vespalib::string              _baseDir;
    ThreadingServiceConfig        _writeServiceConfig;
    // Only one thread per executor, or dropFeedView() will fail.
    ExecutorThreadingService      _writeService;
    // threads for initializer tasks during proton startup
    InitializeThreads             _initializeThreads;

    // variables related to reconfig
    DocumentDBConfigSP                      _initConfigSnapshot;
    SerialNum                               _initConfigSerialNum;
    vespalib::VarHolder<DocumentDBConfigSP> _pendingConfigSnapshot;
    mutable std::mutex                      _configMutex;  // protects _active* below.
    mutable std::condition_variable         _configCV;
    DocumentDBConfigSP                      _activeConfigSnapshot;
    const bool                              _validateAndSanitizeDocStore;
    vespalib::Gate                          _initGate;

    ClusterStateHandler                              _clusterStateHandler;
    BucketHandler                                    _bucketHandler;
    index::IndexConfig                               _indexCfg;
    std::unique_ptr<ReplayThrottlingPolicy>          _replay_throttling_policy;
    ConfigStore::UP                                  _config_store;
    MetricsWireService                              &_metricsWireService;
    DocumentDBTaggedMetrics                          _metrics;
    std::unique_ptr<metrics::UpdateHook>             _metricsHook;
    vespalib::VarHolder<IFeedView::SP>               _feedView;
    vespalib::MonitoredRefCount                      _refCount;
    IDocumentDBOwner                                &_owner;
    storage::spi::BucketExecutor                    &_bucketExecutor;
    DDBState                                         _state;
    DiskMemUsageForwarder                            _dmUsageForwarder;
    AttributeUsageFilter                             _writeFilter;
    std::shared_ptr<ITransientResourceUsageProvider> _transient_usage_provider;
    std::unique_ptr<FeedHandler>                     _feedHandler;
    DocumentSubDBCollection                          _subDBs;
    MaintenanceController                            _maintenanceController;
    DocumentDBJobTrackers                            _jobTrackers;
    std::shared_ptr<IBucketStateCalculator>          _calc;
    DocumentDBMetricsUpdater                         _metricsUpdater;

    void registerReference();
    void setActiveConfig(DocumentDBConfigSP config);
    DocumentDBConfigSP getActiveConfig() const;
    void internalInit();
    void initManagers();
    void initFinish(DocumentDBConfigSP configSnapshot);
    void performReconfig(DocumentDBConfigSP configSnapshot, std::unique_ptr<DocumentDBReconfig> prepared_reconfig);
    void closeSubDBs();

    void applySubDBConfig(const DocumentDBConfig &newConfigSnapshot,
                          SerialNum serialNum, const ReconfigParams &params,
                          const DocumentDBReconfig& prepared_reconfig);
    void applyConfig(DocumentDBConfigSP configSnapshot, SerialNum serialNum, std::unique_ptr<DocumentDBReconfig> prepared_reconfig);

    /**
     * Save initial config if we don't have any saved config snapshots.
     *
     * @param configSnapshot initial config snapshot.
     */
    void saveInitialConfig(std::shared_ptr<DocumentDBConfig> configSnapshot);

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

    DocumentDB(const vespalib::string &baseDir,
               DocumentDBConfigSP currentSnapshot,
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
               const HwInfo &hwInfo);
public:
    using SP = std::shared_ptr<DocumentDB>;

    /**
     * Constructs a new document database for the given document type.
     *
     * @param baseDir The base directory to use for persistent data.
     * @param tlsSpec The frt connection spec for the TLS.
     * @param docType The document type that this database will handle.
     * @param docMgrSP  The document manager holding the document type.
     * @param protonCfg The global proton config this database is a part of.
     * @param config_store Access to read and write configs.
     */
    static DocumentDB::SP
    create(const vespalib::string &baseDir,
           DocumentDBConfigSP currentSnapshot,
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
           const HwInfo &hwInfo);

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
     * Returns the number of documents that are active for search in this database,
     * and the number of documents that will be active once ideal state is reached.
     *
     * @return The active and target-active document count.
     */
    ActiveDocs getNumActiveDocs() const;

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
    vespalib::RetainGuard retain() { return {_refCount}; }

    bool getDelayedConfig() const { return _state.getDelayedConfig(); }
    void replayConfig(SerialNum serialNum) override;
    const DocTypeName & getDocTypeName() const { return _docTypeName; }
    std::unique_ptr<DocumentDBReconfig> prepare_reconfig(const DocumentDBConfig& new_config_snapshot, std::optional<SerialNum> serial_num);
    void reconfigure(DocumentDBConfigSP snapshot) override;
    int64_t getActiveGeneration() const;
    /*
     * Implements IDocumentSubDBOwner
     */
    document::BucketSpace getBucketSpace() const override;
    vespalib::string getName() const override;
    uint32_t getDistributionKey() const override;
    matching::SessionManager &session_manager() override;

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
    std::shared_ptr<const ITransientResourceUsageProvider> transient_usage_provider();
    ExecutorThreadingService & getWriteService() { return _writeService; }

    void set_attribute_usage_listener(std::unique_ptr<IAttributeUsageListener> listener);
    const DDBState& get_state() const noexcept { return _state; }
};

} // namespace proton
