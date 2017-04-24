// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "health_adapter.h"
#include "idocumentdbowner.h"
#include "bootstrapconfigmanager.h"
#include "documentdb.h"
#include "memory_flush_config_updater.h"
#include "proton_config_fetcher.h"
#include "i_proton_configurer_owner.h"
#include "proton_configurer.h"
#include "rpc_hooks.h"
#include "bootstrapconfig.h"
#include <vespa/persistence/proxy/providerstub.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/flushengine/flushengine.h>
#include <vespa/searchcore/proton/matchengine/matchengine.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/metrics_engine.h>
#include <vespa/searchcore/proton/persistenceengine/i_resource_write_filter.h>
#include <vespa/searchcore/proton/persistenceengine/ipersistenceengineowner.h>
#include <vespa/searchcore/proton/persistenceengine/persistenceengine.h>
#include <vespa/searchcore/proton/summaryengine/summaryengine.h>
#include <vespa/searchcore/proton/summaryengine/docsum_by_slime.h>
#include <vespa/searchcorespi/plugin/factoryloader.h>
#include <vespa/searchcorespi/plugin/factoryregistry.h>
#include <vespa/searchlib/common/fileheadercontext.h>
#include <vespa/searchlib/engine/monitorapi.h>
#include <vespa/searchlib/engine/transportserver.h>
#include <vespa/searchlib/transactionlog/translogserverapp.h>
#include <vespa/vespalib/net/component_config_producer.h>
#include <vespa/vespalib/net/generic_state_handler.h>
#include <vespa/vespalib/net/json_get_handler.h>
#include <vespa/vespalib/net/simple_component_config_producer.h>
#include <vespa/vespalib/net/state_explorer.h>
#include <vespa/vespalib/net/state_server.h>
#include <vespa/vespalib/util/varholder.h>
#include <mutex>
#include <shared_mutex>

namespace proton {

class DiskMemUsageSampler;
class HwInfoSampler;
class IDocumentDBReferenceRegistry;

class Proton : public IProtonConfigurerOwner,
               public search::engine::MonitorServer,
               public IDocumentDBOwner,
               public StatusProducer,
               public storage::spi::ProviderStub::PersistenceProviderFactory,
               public IPersistenceEngineOwner,
               public vespalib::ComponentConfigProducer,
               public vespalib::StateExplorer
{
private:
    typedef search::transactionlog::TransLogServerApp     TLS;
    typedef search::engine::TransportServer               TransportServer;
    typedef search::engine::MonitorRequest                MonitorRequest;
    typedef search::engine::MonitorReply                  MonitorReply;
    typedef search::engine::MonitorClient                 MonitorClient;
    typedef storage::spi::ProviderStub                    ProviderStub;
    typedef std::map<DocTypeName, DocumentDB::SP>         DocumentDBMap;
    typedef BootstrapConfig::ProtonConfigSP               ProtonConfigSP;
    typedef std::shared_ptr<FastOS_DynamicLibrary>      DynamicLibrarySP;
    typedef std::map<vespalib::string, DynamicLibrarySP>  LibraryMap;
    using InitializeThreads = std::shared_ptr<vespalib::ThreadStackExecutorBase>;
    using lock_guard = std::lock_guard<std::mutex>;

    struct MetricsUpdateHook : metrics::UpdateHook
    {
        Proton &self;
        MetricsUpdateHook(Proton &s)
            : metrics::UpdateHook("proton-hook"),
              self(s) {}
        void updateMetrics(const vespalib::MonitorGuard &guard) override { self.updateMetrics(guard); }
    };
    friend struct MetricsUpdateHook;

    class ProtonFileHeaderContext : public search::common::FileHeaderContext
    {
        const Proton &_proton;
        vespalib::string _hostName;
        vespalib::string _creator;
        vespalib::string _cluster;
        pid_t _pid;

    public:
        ProtonFileHeaderContext(const Proton &proton_,
                                const vespalib::string &creator);
        ~ProtonFileHeaderContext();

        virtual void
        addTags(vespalib::GenericHeader &header,
                const vespalib::string &name) const override;

        void
        setClusterName(const vespalib::string &clusterName,
                       const vespalib::string &baseDir);
    };

    const config::ConfigUri         _configUri;
    vespalib::string                _dbFile;
    mutable std::shared_timed_mutex _mutex;
    MetricsUpdateHook               _metricsHook;
    MetricsEngine::UP               _metricsEngine;
    ProtonFileHeaderContext         _fileHeaderContext;
    TLS::UP                         _tls;
    std::unique_ptr<DiskMemUsageSampler> _diskMemUsageSampler;
    PersistenceEngine::UP           _persistenceEngine;
    ProviderStub::UP                _persistenceProxy;
    DocumentDBMap                   _documentDBMap;
    MatchEngine::UP                 _matchEngine;
    SummaryEngine::UP               _summaryEngine;
    DocsumBySlime::UP               _docsumBySlime;
    MemoryFlushConfigUpdater::UP    _memoryFlushConfigUpdater;
    FlushEngine::UP                 _flushEngine;
    RPCHooks::UP                    _rpcHooks;
    HealthAdapter                   _healthAdapter;
    vespalib::SimpleComponentConfigProducer _componentConfig;
    vespalib::GenericStateHandler   _genericStateHandler;
    vespalib::JsonHandlerRepo::Token::UP _customComponentBindToken;
    vespalib::JsonHandlerRepo::Token::UP _customComponentRootToken;
    vespalib::StateServer::UP       _stateServer;
    TransportServer::UP             _fs4Server;
    vespalib::ThreadStackExecutor   _executor;
    ProtonConfigurer                _protonConfigurer;
    ProtonConfigFetcher             _protonConfigFetcher;
    std::unique_ptr<vespalib::ThreadStackExecutorBase> _warmupExecutor;
    std::unique_ptr<vespalib::ThreadStackExecutorBase> _summaryExecutor;
    matching::QueryLimiter          _queryLimiter;
    vespalib::Clock                 _clock;
    FastOS_ThreadPool               _threadPool;
    searchcorespi::FactoryLoader    _libraries;
    searchcorespi::FactoryRegistry  _indexManagerFactoryRegistry;
    vespalib::Monitor               _configGenMonitor;
    int64_t                         _configGen;
    uint32_t                        _distributionKey;
    bool                            _isInitializing;
    bool                            _isReplayDone;
    bool                            _abortInit;
    bool                            _initStarted;
    bool                            _initComplete;
    bool                            _initDocumentDbsInSequence;
    HwInfo                          _hwInfo;
    std::unique_ptr<HwInfoSampler>  _hwInfoSampler;
    std::shared_ptr<IDocumentDBReferenceRegistry> _documentDBReferenceRegistry;

    bool performDataDirectoryUpgrade(const vespalib::string &baseDir);
    void loadLibrary(const vespalib::string &libName);

    virtual IDocumentDBConfigOwner *addDocumentDB(const DocTypeName & docTypeName,
                                                  const vespalib::string & configid,
                                                  const BootstrapConfig::SP & bootstrapConfig,
                                                  const std::shared_ptr<DocumentDBConfig> &documentDBConfig,
                                                  InitializeThreads initializeThreads) override;

    virtual void removeDocumentDB(const DocTypeName &docTypeName) override;

    virtual void applyConfig(const BootstrapConfig::SP & configSnapshot) override;
    virtual MonitorReply::UP ping(MonitorRequest::UP request, MonitorClient &client) override;

    /**
     * Called by the metrics update hook (typically in the context of
     * the metric manager). Do not call this function in multiple
     * threads at once.
     **/
    void updateMetrics(const vespalib::MonitorGuard &guard);

    void waitForInitDone();
    void waitForOnlineState();
    virtual storage::spi::PersistenceProvider::UP create() const override;
    searchcorespi::IIndexManagerFactory::SP
    getIndexManagerFactory(const vespalib::stringref & name) const override;
    uint32_t getDistributionKey() const override { return _distributionKey; }
    BootstrapConfig::SP getActiveConfigSnapshot() const;
    virtual std::shared_ptr<IDocumentDBReferenceRegistry> getDocumentDBReferenceRegistry() const override;


public:
    typedef std::unique_ptr<Proton> UP;
    typedef std::shared_ptr<Proton> SP;

    Proton(const config::ConfigUri & configUri,
           const vespalib::string &progName,
           uint64_t subscribeTimeout);
    virtual ~Proton();

    /**
     * This method must be called after the constructor and before the destructor.
     * If not I will force a 'core' upon you.
     * All relevant initialization is conducted here.
     *
     * 1st phase init: start cheap clock thread and get initial config
     */
    BootstrapConfig::SP
    init();

    /*
     * 2nd phase init: setup data structures.
     */
    void
    init(const BootstrapConfig::SP & configSnapshot);


    DocumentDB::SP
    getDocumentDB(const document::DocumentType &docType);

    DocumentDB::SP
    addDocumentDB(const document::DocumentType &docType,
                  const BootstrapConfig::SP &configSnapshot,
                  const std::shared_ptr<DocumentDBConfig> &documentDBConfig,
                  InitializeThreads initializeThreads);

    metrics::MetricManager & getMetricManager() { return _metricsEngine->getManager(); }
    FastOS_ThreadPool & getThreadPool() { return _threadPool; }

    bool triggerFlush();
    bool prepareRestart();
    void listDocTypes(std::vector<vespalib::string> &documentTypes);

    void
    listSchema(const vespalib::string &documentType,
               std::vector<vespalib::string> &fieldNames,
               std::vector<vespalib::string> &fieldDataTypes,
               std::vector<vespalib::string> &fieldCollectionTypes,
               std::vector<vespalib::string> &fieldLocations);


    // implements ComponentConfigProducer interface
    virtual void getComponentConfig(Consumer &consumer) override;

    // implements IPersistenceEngineOwner interface
    virtual void setClusterState(const storage::spi::ClusterState &calc) override;

    /**
     * Return the oldest active config generation used by proton.
     */
    int64_t getConfigGeneration(void);

    size_t getNumDocs() const;
    size_t getNumActiveDocs() const;
    DocsumBySlime & getDocsumBySlime() { return *_docsumBySlime; }

    vespalib::string getBadConfigs(void) const;

    virtual StatusReport::List getStatusReports() const override;

    MatchEngine & getMatchEngine() { return *_matchEngine; }
    FlushEngine & getFlushEngine() { return *_flushEngine; }
    vespalib::ThreadStackExecutorBase & getExecutor() { return _executor; }

    bool isReplayDone() const { return _isReplayDone; }

    virtual bool isInitializing() const override {
        return _isInitializing;
    }

    bool hasAbortedInit() const { return _abortInit; }
    storage::spi::PersistenceProvider & getPersistence() { return *_persistenceEngine; }

    // Implements vespalib::StateExplorer
    virtual void get_state(const vespalib::slime::Inserter &inserter, bool full) const override;
    virtual std::vector<vespalib::string> get_children_names() const override;
    virtual std::unique_ptr<vespalib::StateExplorer> get_child(vespalib::stringref name) const override;
};

} // namespace proton

