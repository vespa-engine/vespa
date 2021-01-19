// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "bootstrapconfig.h"
#include "bootstrapconfigmanager.h"
#include "documentdb.h"
#include "health_adapter.h"
#include "i_proton_configurer_owner.h"
#include "idocumentdbowner.h"
#include "memory_flush_config_updater.h"
#include "proton_config_fetcher.h"
#include "proton_configurer.h"
#include "rpc_hooks.h"
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/metrics_engine.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchcore/proton/persistenceengine/ipersistenceengineowner.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/engine/monitorapi.h>
#include <vespa/vespalib/net/component_config_producer.h>
#include <vespa/vespalib/net/generic_state_handler.h>
#include <vespa/vespalib/net/json_get_handler.h>
#include <vespa/vespalib/net/json_handler_repo.h>
#include <vespa/vespalib/net/state_explorer.h>
#include <vespa/vespalib/util/varholder.h>
#include <vespa/eval/eval/llvm/compile_cache.h>
#include <mutex>
#include <shared_mutex>

namespace vespalib { class StateServer; }
namespace search::transactionlog { class TransLogServerApp; }
namespace metrics { class MetricLockGuard; }
namespace storage::spi { struct PersistenceProvider; }

namespace proton {

class DiskMemUsageSampler;
class IDocumentDBReferenceRegistry;
class IProtonDiskLayout;
class PrepareRestartHandler;
class SummaryEngine;
class DocsumBySlime;
class FlushEngine;
class MatchEngine;
class PersistenceEngine;

class Proton : public IProtonConfigurerOwner,
               public search::engine::MonitorServer,
               public IDocumentDBOwner,
               public StatusProducer,
               public IPersistenceEngineOwner,
               public vespalib::ComponentConfigProducer,
               public vespalib::StateExplorer
{
private:
    using TLS = search::transactionlog::TransLogServerApp;
    using MonitorRequest = search::engine::MonitorRequest;
    using MonitorReply = search::engine::MonitorReply;
    using MonitorClient = search::engine::MonitorClient;
    using DocumentDBMap = std::map<DocTypeName, DocumentDB::SP>;
    using ProtonConfigSP = BootstrapConfig::ProtonConfigSP;
    using InitializeThreads = std::shared_ptr<vespalib::SyncableThreadExecutor>;
    using BucketSpace = document::BucketSpace;

    class ProtonFileHeaderContext : public search::common::FileHeaderContext
    {
        vespalib::string _hostName;
        vespalib::string _creator;
        vespalib::string _cluster;
        pid_t _pid;

    public:
        ProtonFileHeaderContext(const vespalib::string &creator);
        ~ProtonFileHeaderContext() override;

        void addTags(vespalib::GenericHeader &header, const vespalib::string &name) const override;
        void setClusterName(const vespalib::string &clusterName, const vespalib::string &baseDir);
    };

    const config::ConfigUri              _configUri;
    mutable std::shared_mutex            _mutex;
    std::unique_ptr<metrics::UpdateHook> _metricsHook;
    std::unique_ptr<MetricsEngine>       _metricsEngine;
    ProtonFileHeaderContext              _fileHeaderContext;
    std::unique_ptr<TLS>                 _tls;
    std::unique_ptr<DiskMemUsageSampler> _diskMemUsageSampler;
    std::unique_ptr<PersistenceEngine>   _persistenceEngine;
    DocumentDBMap                   _documentDBMap;
    std::unique_ptr<MatchEngine>    _matchEngine;
    std::unique_ptr<SummaryEngine>  _summaryEngine;
    std::unique_ptr<DocsumBySlime>  _docsumBySlime;
    MemoryFlushConfigUpdater::UP    _memoryFlushConfigUpdater;
    std::unique_ptr<FlushEngine>    _flushEngine;
    std::unique_ptr<PrepareRestartHandler> _prepareRestartHandler;
    RPCHooks::UP                    _rpcHooks;
    HealthAdapter                   _healthAdapter;
    vespalib::GenericStateHandler   _genericStateHandler;
    vespalib::JsonHandlerRepo::Token::UP _customComponentBindToken;
    vespalib::JsonHandlerRepo::Token::UP _customComponentRootToken;
    std::unique_ptr<vespalib::StateServer>  _stateServer;
    vespalib::ThreadStackExecutor   _executor;
    std::unique_ptr<IProtonDiskLayout> _protonDiskLayout;
    ProtonConfigurer                _protonConfigurer;
    ProtonConfigFetcher             _protonConfigFetcher;
    std::unique_ptr<vespalib::ThreadStackExecutorBase> _warmupExecutor;
    std::shared_ptr<vespalib::ThreadStackExecutorBase> _sharedExecutor;
    vespalib::eval::CompileCache::ExecutorBinding::UP _compile_cache_executor_binding;
    matching::QueryLimiter          _queryLimiter;
    vespalib::Clock                 _clock;
    FastOS_ThreadPool               _threadPool;
    uint32_t                        _distributionKey;
    bool                            _isInitializing;
    bool                            _abortInit;
    bool                            _initStarted;
    bool                            _initComplete;
    bool                            _initDocumentDbsInSequence;
    bool                            _has_shut_down_config_and_state_components;
    std::shared_ptr<IDocumentDBReferenceRegistry> _documentDBReferenceRegistry;
    std::mutex                      _nodeUpLock;
    std::set<BucketSpace>           _nodeUp;   // bucketspaces where node is up

    std::shared_ptr<DocumentDBConfigOwner>
    addDocumentDB(const DocTypeName & docTypeName, BucketSpace bucketSpace, const vespalib::string & configid,
                  const BootstrapConfig::SP & bootstrapConfig, const std::shared_ptr<DocumentDBConfig> &documentDBConfig,
                  InitializeThreads initializeThreads) override;

    void removeDocumentDB(const DocTypeName &docTypeName) override;

    void applyConfig(const BootstrapConfig::SP & configSnapshot) override;
    MonitorReply::UP ping(MonitorRequest::UP request, MonitorClient &client) override;

    void waitForInitDone();
    void waitForOnlineState();
    uint32_t getDistributionKey() const override { return _distributionKey; }
    BootstrapConfig::SP getActiveConfigSnapshot() const;
    std::shared_ptr<IDocumentDBReferenceRegistry> getDocumentDBReferenceRegistry() const override;
    bool updateNodeUp(BucketSpace bucketSpace, bool nodeUpInBucketSpace);
    void closeDocumentDBs(vespalib::ThreadStackExecutorBase & executor);
public:
    typedef std::unique_ptr<Proton> UP;
    typedef std::shared_ptr<Proton> SP;

    Proton(const config::ConfigUri & configUri,
           const vespalib::string &progName,
           std::chrono::milliseconds subscribeTimeout);
    ~Proton() override;

    /**
     * Called by the metrics update hook (typically in the context of
     * the metric manager). Do not call this function in multiple
     * threads at once.
     **/
    void updateMetrics(const metrics::MetricLockGuard &guard);

    /**
     * This method must be called after the constructor and before the destructor.
     * If not I will force a 'core' upon you.
     * All relevant initialization is conducted here.
     *
     * 1st phase init: start cheap clock thread and get initial config
     */
    BootstrapConfig::SP init();

    /**
     * Shuts down metric manager and state server functionality to avoid
     * calls to these during service layer component tear-down.
     *
     * Explicitly noexcept to avoid consistency issues between this and the
     * destructor if something throws during shutdown.
     */
    void shutdown_config_fetching_and_state_exposing_components_once() noexcept;

    // 2nd phase init: setup data structures.
    void init(const BootstrapConfig::SP & configSnapshot);

    DocumentDB::SP
    addDocumentDB(const document::DocumentType &docType, BucketSpace bucketSpace, const BootstrapConfig::SP &configSnapshot,
                  const std::shared_ptr<DocumentDBConfig> &documentDBConfig, InitializeThreads initializeThreads);

    metrics::MetricManager & getMetricManager() { return _metricsEngine->getManager(); }
    FastOS_ThreadPool & getThreadPool() { return _threadPool; }

    bool triggerFlush();
    bool prepareRestart();

    void getComponentConfig(Consumer &consumer) override;
    void setClusterState(BucketSpace bucketSpace, const storage::spi::ClusterState &calc) override;

    // Return the oldest active config generation used by proton.
    int64_t getConfigGeneration();

    size_t getNumDocs() const;
    size_t getNumActiveDocs() const;
    DocsumBySlime & getDocsumBySlime() { return *_docsumBySlime; }

    search::engine::SearchServer &get_search_server();
    search::engine::DocsumServer &get_docsum_server();
    search::engine::MonitorServer &get_monitor_server();

    vespalib::string getDelayedConfigs() const;

    StatusReport::List getStatusReports() const override;

    MatchEngine & getMatchEngine() { return *_matchEngine; }
    vespalib::ThreadStackExecutorBase & getExecutor() { return _executor; }

    bool isInitializing() const override { return _isInitializing; }

    bool hasAbortedInit() const { return _abortInit; }
    storage::spi::PersistenceProvider & getPersistence();

    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    std::vector<vespalib::string> get_children_names() const override;
    std::unique_ptr<vespalib::StateExplorer> get_child(vespalib::stringref name) const override;
};

} // namespace proton
