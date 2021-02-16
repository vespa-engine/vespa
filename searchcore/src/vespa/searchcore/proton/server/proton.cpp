// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "disk_mem_usage_sampler.h"
#include "document_db_explorer.h"
#include "fileconfigmanager.h"
#include "flushhandlerproxy.h"
#include "memoryflush.h"
#include "persistencehandlerproxy.h"
#include "prepare_restart_handler.h"
#include "proton.h"
#include "proton_config_snapshot.h"
#include "proton_disk_layout.h"
#include "proton_thread_pools_explorer.h"
#include "resource_usage_explorer.h"
#include "searchhandlerproxy.h"
#include "simpleflush.h"

#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/metrics/updatehook.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/searchcore/proton/flushengine/flush_engine_explorer.h>
#include <vespa/searchcore/proton/flushengine/flushengine.h>
#include <vespa/searchcore/proton/flushengine/tls_stats_factory.h>
#include <vespa/searchcore/proton/matchengine/matchengine.h>
#include <vespa/searchcore/proton/persistenceengine/persistenceengine.h>
#include <vespa/searchcore/proton/reference/document_db_reference_registry.h>
#include <vespa/searchcore/proton/summaryengine/docsum_by_slime.h>
#include <vespa/searchcore/proton/summaryengine/summaryengine.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/transactionlog/trans_log_server_explorer.h>
#include <vespa/searchlib/transactionlog/translogserverapp.h>
#include <vespa/searchlib/util/fileheadertk.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/net/state_server.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/mmap_file_allocator_factory.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/vespalib/util/size_literals.h>

#include <vespa/searchlib/aggregation/forcelink.hpp>
#include <vespa/searchlib/expression/forcelink.hpp>
#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.proton");

using document::DocumentTypeRepo;
using vespalib::FileHeader;
using vespalib::IllegalStateException;
using vespalib::Slime;
using vespalib::makeLambdaTask;
using vespalib::slime::ArrayInserter;
using vespalib::slime::Cursor;

using search::transactionlog::DomainStats;
using vespa::config::search::core::ProtonConfig;
using vespa::config::search::core::internal::InternalProtonType;
using vespalib::compression::CompressionConfig;

namespace proton {

namespace {

using search::fs4transport::FS4PersistentPacketStreamer;

CompressionConfig::Type
convert(InternalProtonType::Packetcompresstype type)
{
    switch (type) {
      case InternalProtonType::Packetcompresstype::LZ4: return CompressionConfig::LZ4;
      default: return CompressionConfig::LZ4;
    }
}

void
setBucketCheckSumType(const ProtonConfig & proton)
{
    switch (proton.bucketdb.checksumtype) {
    case InternalProtonType::Bucketdb::Checksumtype::LEGACY:
        bucketdb::BucketState::setChecksumType(bucketdb::BucketState::ChecksumType::LEGACY);
        break;
    case InternalProtonType::Bucketdb::Checksumtype::XXHASH64:
        bucketdb::BucketState::setChecksumType(bucketdb::BucketState::ChecksumType::XXHASH64);
        break;
    }
}

void
setFS4Compression(const ProtonConfig & proton)
{
    FS4PersistentPacketStreamer & fs4(FS4PersistentPacketStreamer::Instance);
    fs4.SetCompressionLimit(proton.packetcompresslimit);
    fs4.SetCompressionLevel(proton.packetcompresslevel);
    fs4.SetCompressionType(convert(proton.packetcompresstype));
}

DiskMemUsageSampler::Config
diskMemUsageSamplerConfig(const ProtonConfig &proton, const HwInfo &hwInfo)
{
    return DiskMemUsageSampler::Config(
            proton.writefilter.memorylimit,
            proton.writefilter.disklimit,
            vespalib::from_s(proton.writefilter.sampleinterval),
            hwInfo);
}

size_t
derive_shared_threads(const ProtonConfig &proton,
                      const HwInfo::Cpu &cpuInfo) {
    size_t scaledCores = (size_t)std::ceil(cpuInfo.cores() * proton.feeding.concurrency);

    // We need at least 1 guaranteed free worker in order to ensure progress so #documentsdbs + 1 should suffice,
    // but we will not be cheap and give it one extra.
    return std::max(scaledCores, proton.documentdb.size() + proton.flush.maxconcurrent + 1);
}

struct MetricsUpdateHook : metrics::UpdateHook
{
    Proton &self;
    MetricsUpdateHook(Proton &s)
        : metrics::UpdateHook("proton-hook"),
          self(s)
    {}
    void updateMetrics(const MetricLockGuard &guard) override {
        self.updateMetrics(guard);
    }
};

const vespalib::string CUSTOM_COMPONENT_API_PATH = "/state/v1/custom/component";

VESPA_THREAD_STACK_TAG(proton_shared_executor)
VESPA_THREAD_STACK_TAG(index_warmup_executor)
VESPA_THREAD_STACK_TAG(initialize_executor)
VESPA_THREAD_STACK_TAG(close_executor)

}

Proton::ProtonFileHeaderContext::ProtonFileHeaderContext(const vespalib::string &creator)
    : _hostName(),
      _creator(creator),
      _cluster(),
      _pid(getpid())
{
    _hostName = vespalib::HostName::get();
    assert(!_hostName.empty());
}

Proton::ProtonFileHeaderContext::~ProtonFileHeaderContext() = default;

void
Proton::ProtonFileHeaderContext::addTags(vespalib::GenericHeader &header,
        const vespalib::string &name) const
{
    typedef vespalib::GenericHeader::Tag Tag;

    search::FileHeaderTk::addVersionTags(header);
    header.putTag(Tag("fileName", name));
    addCreateAndFreezeTime(header);
    header.putTag(Tag("hostName", _hostName));
    header.putTag(Tag("pid", _pid));
    header.putTag(Tag("creator", _creator));
    if (!_cluster.empty()) {
        header.putTag(Tag("cluster", _cluster));
    }
}


void
Proton::ProtonFileHeaderContext::setClusterName(const vespalib::string & clusterName,
                                                const vespalib::string & baseDir)
{
    if (!clusterName.empty()) {
        _cluster = clusterName;
        return;
    }
    // Derive cluster name from base dir.
    size_t cpos(baseDir.rfind('/'));
    if (cpos == vespalib::string::npos)
        return;
    size_t rpos(baseDir.rfind('/', cpos - 1));
    if (rpos == vespalib::string::npos)
        return;
    size_t clpos(baseDir.rfind('/', rpos - 1));
    if (clpos == vespalib::string::npos)
        return;
    if (baseDir.substr(clpos + 1, 8) != "cluster.")
        return;
    _cluster = baseDir.substr(clpos + 9, rpos - clpos - 9);
}


Proton::Proton(const config::ConfigUri & configUri,
               const vespalib::string &progName,
               std::chrono::milliseconds subscribeTimeout)
    : IProtonConfigurerOwner(),
      search::engine::MonitorServer(),
      IDocumentDBOwner(),
      StatusProducer(),
      IPersistenceEngineOwner(),
      ComponentConfigProducer(),
      _configUri(configUri),
      _mutex(),
      _metricsHook(std::make_unique<MetricsUpdateHook>(*this)),
      _metricsEngine(std::make_unique<MetricsEngine>()),
      _fileHeaderContext(progName),
      _tls(),
      _diskMemUsageSampler(),
      _persistenceEngine(),
      _documentDBMap(),
      _matchEngine(),
      _summaryEngine(),
      _docsumBySlime(),
      _memoryFlushConfigUpdater(),
      _flushEngine(),
      _prepareRestartHandler(),
      _rpcHooks(),
      _healthAdapter(*this),
      _genericStateHandler(CUSTOM_COMPONENT_API_PATH, *this),
      _customComponentBindToken(),
      _customComponentRootToken(),
      _stateServer(),
      // This executor can only have 1 thread as it is used for
      // serializing startup.
      _executor(1, 128_Ki),
      _protonDiskLayout(),
      _protonConfigurer(_executor, *this, _protonDiskLayout),
      _protonConfigFetcher(configUri, _protonConfigurer, subscribeTimeout),
      _warmupExecutor(),
      _sharedExecutor(),
      _compile_cache_executor_binding(),
      _queryLimiter(),
      _clock(0.001),
      _threadPool(128_Ki),
      _distributionKey(-1),
      _isInitializing(true),
      _abortInit(false),
      _initStarted(false),
      _initComplete(false),
      _initDocumentDbsInSequence(false),
      _has_shut_down_config_and_state_components(false),
      _documentDBReferenceRegistry(std::make_shared<DocumentDBReferenceRegistry>()),
      _nodeUpLock(),
      _nodeUp()
{ }

BootstrapConfig::SP
Proton::init()
{
    assert( ! _initStarted && ! _initComplete );
    _initStarted = true;
    if (_threadPool.NewThread(_clock.getRunnable(), nullptr) == nullptr) {
        throw IllegalStateException("Failed starting thread for the cheap clock");
    }
    _protonConfigFetcher.start();
    auto configSnapshot = _protonConfigurer.getPendingConfigSnapshot();
    assert(configSnapshot);
    auto bootstrapConfig = configSnapshot->getBootstrapConfig();
    assert(bootstrapConfig);

    return bootstrapConfig;
}

void
Proton::init(const BootstrapConfig::SP & configSnapshot)
{
    assert( _initStarted && ! _initComplete );
    const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
    const HwInfo & hwInfo = configSnapshot->getHwInfo();

    setBucketCheckSumType(protonConfig);
    setFS4Compression(protonConfig);
    _diskMemUsageSampler = std::make_unique<DiskMemUsageSampler>(protonConfig.basedir,
                                                                 diskMemUsageSamplerConfig(protonConfig, hwInfo));

    _tls = std::make_unique<TLS>(_configUri.createWithNewId(protonConfig.tlsconfigid), _fileHeaderContext);
    _metricsEngine->addMetricsHook(*_metricsHook);
    _fileHeaderContext.setClusterName(protonConfig.clustername, protonConfig.basedir);
    _matchEngine = std::make_unique<MatchEngine>(protonConfig.numsearcherthreads,
                                                 protonConfig.numthreadspersearch,
                                                 protonConfig.distributionkey);
    _distributionKey = protonConfig.distributionkey;
    _summaryEngine= std::make_unique<SummaryEngine>(protonConfig.numsummarythreads);
    _docsumBySlime = std::make_unique<DocsumBySlime>(*_summaryEngine);

    IFlushStrategy::SP strategy;
    const ProtonConfig::Flush & flush(protonConfig.flush);
    switch (flush.strategy) {
    case ProtonConfig::Flush::Strategy::MEMORY: {
        auto memoryFlush = std::make_shared<MemoryFlush>(
                MemoryFlushConfigUpdater::convertConfig(flush.memory, hwInfo.memory()), vespalib::system_clock::now());
        _memoryFlushConfigUpdater = std::make_unique<MemoryFlushConfigUpdater>(memoryFlush, flush.memory, hwInfo.memory());
        _diskMemUsageSampler->notifier().addDiskMemUsageListener(_memoryFlushConfigUpdater.get());
        strategy = memoryFlush;
        break;
    }
    case ProtonConfig::Flush::Strategy::SIMPLE:
    default:
        strategy = std::make_shared<SimpleFlush>();
        break;
    }
    _protonDiskLayout = std::make_unique<ProtonDiskLayout>(protonConfig.basedir, protonConfig.tlsspec);
    vespalib::chdir(protonConfig.basedir);
    vespalib::alloc::MmapFileAllocatorFactory::instance().setup(protonConfig.basedir + "/swapdirs");
    _tls->start();
    _flushEngine = std::make_unique<FlushEngine>(std::make_shared<flushengine::TlsStatsFactory>(_tls->getTransLogServer()),
                                                 strategy, flush.maxconcurrent, vespalib::from_s(flush.idleinterval));
    _metricsEngine->addExternalMetrics(_summaryEngine->getMetrics());

    char tmp[1024];
    LOG(debug, "Start proton server with root at %s and cwd at %s",
        protonConfig.basedir.c_str(), getcwd(tmp, sizeof(tmp)));

    _persistenceEngine = std::make_unique<PersistenceEngine>(*this, _diskMemUsageSampler->writeFilter(),
                                                             _diskMemUsageSampler->notifier(),
                                                             protonConfig.visit.defaultserializedsize,
                                                             protonConfig.visit.ignoremaxbytes);

    vespalib::string fileConfigId;
    _warmupExecutor = std::make_unique<vespalib::ThreadStackExecutor>(4, 128_Ki, index_warmup_executor);

    const size_t sharedThreads = derive_shared_threads(protonConfig, hwInfo.cpu());
    _sharedExecutor = std::make_shared<vespalib::BlockingThreadStackExecutor>(sharedThreads, 128_Ki, sharedThreads*16, proton_shared_executor);
    _compile_cache_executor_binding = vespalib::eval::CompileCache::bind(_sharedExecutor);
    InitializeThreads initializeThreads;
    if (protonConfig.initialize.threads > 0) {
        initializeThreads = std::make_shared<vespalib::ThreadStackExecutor>(protonConfig.initialize.threads, 128_Ki, initialize_executor);
        _initDocumentDbsInSequence = (protonConfig.initialize.threads == 1);
    }
    _protonConfigurer.applyInitialConfig(initializeThreads);
    initializeThreads.reset();

    _prepareRestartHandler = std::make_unique<PrepareRestartHandler>(*_flushEngine);
    RPCHooks::Params rpcParams(*this, protonConfig.rpcport, _configUri.getConfigId(),
                               std::max(2u, hwInfo.cpu().cores()/4));
    rpcParams.slobrok_config = _configUri.createWithNewId(protonConfig.slobrokconfigid);
    _rpcHooks = std::make_unique<RPCHooks>(rpcParams);
    _metricsEngine->addExternalMetrics(_rpcHooks->proto_rpc_adapter_metrics());

    waitForInitDone();

    _metricsEngine->start(_configUri);
    _stateServer = std::make_unique<vespalib::StateServer>(protonConfig.httpport, _healthAdapter,
                                                           _metricsEngine->metrics_producer(), *this);
    _customComponentBindToken = _stateServer->repo().bind(CUSTOM_COMPONENT_API_PATH, _genericStateHandler);
    _customComponentRootToken = _stateServer->repo().add_root_resource(CUSTOM_COMPONENT_API_PATH);

    _executor.sync();
    waitForOnlineState();
    _rpcHooks->set_online();

    _flushEngine->start();
    _isInitializing = false;
    _protonConfigurer.setAllowReconfig(true);
    _initComplete = true;
}

BootstrapConfig::SP
Proton::getActiveConfigSnapshot() const
{
    return _protonConfigurer.getActiveConfigSnapshot()->getBootstrapConfig();
}

void
Proton::applyConfig(const BootstrapConfig::SP & configSnapshot)
{
    // Called by executor thread during reconfig.
    const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
    setFS4Compression(protonConfig);

    _queryLimiter.configure(protonConfig.search.memory.limiter.maxthreads,
                            protonConfig.search.memory.limiter.mincoverage,
                            protonConfig.search.memory.limiter.minhits);
    const std::shared_ptr<const DocumentTypeRepo> repo = configSnapshot->getDocumentTypeRepoSP();

    _diskMemUsageSampler->setConfig(diskMemUsageSamplerConfig(protonConfig, configSnapshot->getHwInfo()));
    if (_memoryFlushConfigUpdater) {
        _memoryFlushConfigUpdater->setConfig(protonConfig.flush.memory);
        _flushEngine->kick();
    }
}

std::shared_ptr<DocumentDBConfigOwner>
Proton::addDocumentDB(const DocTypeName &docTypeName,
                      document::BucketSpace bucketSpace,
                      const vespalib::string &configId,
                      const BootstrapConfig::SP &bootstrapConfig,
                      const DocumentDBConfig::SP &documentDBConfig,
                      InitializeThreads initializeThreads)
{
    try {
        const std::shared_ptr<const DocumentTypeRepo> repo = bootstrapConfig->getDocumentTypeRepoSP();
        const document::DocumentType *docType = repo->getDocumentType(docTypeName.getName());
        if (docType != nullptr) {
            LOG(info, "Add document database: doctypename(%s), configid(%s)",
                docTypeName.toString().c_str(), configId.c_str());
            return addDocumentDB(*docType, bucketSpace, bootstrapConfig, documentDBConfig, initializeThreads);
        } else {

            LOG(warning,
                "Did not find document type '%s' in the document manager. "
                "Skipping creating document database for this type",
                docTypeName.toString().c_str());
            return std::shared_ptr<DocumentDBConfigOwner>();
        }
    } catch (const document::DocumentTypeNotFoundException & e) {
        LOG(warning,
            "Did not find document type '%s' in the document manager. "
            "Skipping creating document database for this type",
            docTypeName.toString().c_str());
        return std::shared_ptr<DocumentDBConfigOwner>();
    }
}

Proton::~Proton()
{
    assert(_initStarted);
    if ( ! _initComplete ) {
        LOG(warning, "Initialization of proton was halted. Shutdown sequence has been initiated.");
    }
    shutdown_config_fetching_and_state_exposing_components_once();
    _executor.sync();
    if (_matchEngine) {
        _matchEngine->close();
    }
    if (_metricsEngine && _summaryEngine) {
        _metricsEngine->removeExternalMetrics(_summaryEngine->getMetrics());
    }
    if (_summaryEngine) {
        _summaryEngine->close();
    }
    if (_rpcHooks) {
        _rpcHooks->close();
        _metricsEngine->removeExternalMetrics(_rpcHooks->proto_rpc_adapter_metrics());
    }
    if (_memoryFlushConfigUpdater) {
        _diskMemUsageSampler->notifier().removeDiskMemUsageListener(_memoryFlushConfigUpdater.get());
    }
    _executor.shutdown();
    _executor.sync();
    _rpcHooks.reset();
    if (_flushEngine) {
        _flushEngine->close();
    }
    if (_warmupExecutor) {
        _warmupExecutor->sync();
    }
    if (_sharedExecutor) {
        _sharedExecutor->sync();
    }

    if ( ! _documentDBMap.empty()) {
        size_t numCores = 4;
        const std::shared_ptr<proton::ProtonConfigSnapshot> pcsp = _protonConfigurer.getActiveConfigSnapshot();
        if (pcsp) {
            const std::shared_ptr<proton::BootstrapConfig> bcp = pcsp->getBootstrapConfig();
            if (bcp) {
                numCores = std::max(bcp->getHwInfo().cpu().cores(), 1u);
            }
        }

        vespalib::ThreadStackExecutor closePool(std::min(_documentDBMap.size(), numCores), 0x20000, close_executor);
        closeDocumentDBs(closePool);
    }
    _documentDBMap.clear();
    _persistenceEngine.reset();
    _tls.reset();
    _warmupExecutor.reset();
    _compile_cache_executor_binding.reset();
    _sharedExecutor.reset();
    _clock.stop();
    LOG(debug, "Explicit destructor done");
}

void
Proton::shutdown_config_fetching_and_state_exposing_components_once() noexcept
{
    if (_has_shut_down_config_and_state_components) {
        return;
    }
    _protonConfigFetcher.close();
    _protonConfigurer.setAllowReconfig(false);
    _executor.sync();
    _customComponentRootToken.reset();
    _customComponentBindToken.reset();
    _stateServer.reset();
    if (_metricsEngine) {
        _metricsEngine->removeMetricsHook(*_metricsHook);
        _metricsEngine->stop();
    }
    _has_shut_down_config_and_state_components = true;
}

void
Proton::closeDocumentDBs(vespalib::ThreadStackExecutorBase & executor) {
    // Need to extract names first as _documentDBMap is modified while removing.
    std::vector<DocTypeName> docTypes;
    docTypes.reserve(_documentDBMap.size());
    for (const auto & entry : _documentDBMap) {
        docTypes.push_back(entry.first);
    }
    for (const auto & docTypeName : docTypes) {
        executor.execute(makeLambdaTask([this, docTypeName]() { removeDocumentDB(docTypeName); }));
    }
    executor.sync();
}

size_t Proton::getNumDocs() const
{
    size_t numDocs(0);
    std::shared_lock<std::shared_mutex> guard(_mutex);
    for (const auto &kv : _documentDBMap) {
        numDocs += kv.second->getNumDocs();
    }
    return numDocs;
}

size_t Proton::getNumActiveDocs() const
{
    size_t numDocs(0);
    std::shared_lock<std::shared_mutex> guard(_mutex);
    for (const auto &kv : _documentDBMap) {
        numDocs += kv.second->getNumActiveDocs();
    }
    return numDocs;
}

search::engine::SearchServer &
Proton::get_search_server()
{
    return *_matchEngine;
}

search::engine::DocsumServer &
Proton::get_docsum_server()
{
    return *_summaryEngine;
}

search::engine::MonitorServer &
Proton::get_monitor_server()
{
    return *this;
}

vespalib::string
Proton::getDelayedConfigs() const
{
    std::ostringstream res;
    bool first = true;
    std::shared_lock<std::shared_mutex> guard(_mutex);
    for (const auto &kv : _documentDBMap) {
        if (kv.second->getDelayedConfig()) {
            if (!first) {
                res << ", ";
            }
            first = false;
            res << kv.first.toString();
        }
    }
    return res.str();
}

StatusReport::List
Proton::getStatusReports() const
{
    StatusReport::List reports;
    std::shared_lock<std::shared_mutex> guard(_mutex);
    reports.push_back(StatusReport::SP(_matchEngine->reportStatus()));
    for (const auto &kv : _documentDBMap) {
        reports.push_back(StatusReport::SP(kv.second->reportStatus()));
    }
    return reports;
}

DocumentDB::SP
Proton::addDocumentDB(const document::DocumentType &docType,
                      document::BucketSpace bucketSpace,
                      const BootstrapConfig::SP &bootstrapConfig,
                      const DocumentDBConfig::SP &documentDBConfig,
                      InitializeThreads initializeThreads)
{
    const ProtonConfig &config(bootstrapConfig->getProtonConfig());

    std::lock_guard<std::shared_mutex> guard(_mutex);
    DocTypeName docTypeName(docType.getName());
    auto it = _documentDBMap.find(docTypeName);
    if (it != _documentDBMap.end()) {
        return it->second;
    }

    vespalib::string db_dir = config.basedir + "/documents/" + docTypeName.toString();
    vespalib::mkdir(db_dir, false); // Assume parent is created.
    auto config_store = std::make_unique<FileConfigManager>(db_dir + "/config",
                                                            documentDBConfig->getConfigId(),
                                                            docTypeName.getName());
    config_store->setProtonConfig(bootstrapConfig->getProtonConfigSP());
    if (!initializeThreads) {
        // If configured value for initialize threads was 0, or we
        // are performing a reconfig after startup has completed, then use
        // 1 thread per document type.
        initializeThreads = std::make_shared<vespalib::ThreadStackExecutor>(1, 128_Ki);
    }
    auto ret = std::make_shared<DocumentDB>(config.basedir + "/documents", documentDBConfig, config.tlsspec,
                                            _queryLimiter, _clock, docTypeName, bucketSpace, config, *this,
                                            *_warmupExecutor, *_sharedExecutor, *_persistenceEngine, *_tls->getTransLogServer(),
                                            *_metricsEngine, _fileHeaderContext, std::move(config_store),
                                            initializeThreads, bootstrapConfig->getHwInfo());
    try {
        ret->start();
    } catch (vespalib::Exception &e) {
        LOG(warning, "Failed to start database for document type '%s'; %s",
            docTypeName.toString().c_str(), e.what());
        return DocumentDB::SP();
    }
    // Wait for replay done on document dbs added due to reconfigs, since engines are already up and running.
    // Also wait for document db reaching online state if initializing in sequence.
    if (!_isInitializing || _initDocumentDbsInSequence) {
        ret->waitForOnlineState();
    }
    _metricsEngine->addDocumentDBMetrics(ret->getMetrics());
    _metricsEngine->addMetricsHook(ret->getMetricsUpdateHook());
    _documentDBMap[docTypeName] = ret;
    if (_persistenceEngine) {
        // Not allowed to get to service layer to call pause().
        std::unique_lock<std::shared_mutex> persistenceWGuard(_persistenceEngine->getWLock());
        auto persistenceHandler = std::make_shared<PersistenceHandlerProxy>(ret);
        if (!_isInitializing) {
            _persistenceEngine->propagateSavedClusterState(bucketSpace, *persistenceHandler);
            _persistenceEngine->populateInitialBucketDB(persistenceWGuard, bucketSpace, *persistenceHandler);
        }
        // TODO: Fix race with new cluster state setting.
        _persistenceEngine->putHandler(persistenceWGuard, bucketSpace, docTypeName, persistenceHandler);
        ret->set_attribute_usage_listener(
                _persistenceEngine->get_resource_usage_tracker().make_attribute_usage_listener(docTypeName.getName()));
    }
    auto searchHandler = std::make_shared<SearchHandlerProxy>(ret);
    _summaryEngine->putSearchHandler(docTypeName, searchHandler);
    _matchEngine->putSearchHandler(docTypeName, searchHandler);
    auto flushHandler = std::make_shared<FlushHandlerProxy>(ret);
    _flushEngine->putFlushHandler(docTypeName, flushHandler);
    _diskMemUsageSampler->notifier().addDiskMemUsageListener(ret->diskMemUsageListener());
    _diskMemUsageSampler->add_transient_memory_usage_provider(ret->transient_memory_usage_provider());
    return ret;
}


void
Proton::removeDocumentDB(const DocTypeName &docTypeName)
{
    DocumentDB::SP old;
    {
        std::lock_guard<std::shared_mutex> guard(_mutex);
        auto it = _documentDBMap.find(docTypeName);
        if (it == _documentDBMap.end()) {
            return;
        }
        old = it->second;
        _documentDBMap.erase(it);
    }

    // Remove all entries into document db
    if (_persistenceEngine) {
        {
            // Not allowed to get to service layer to call pause().
            std::unique_lock<std::shared_mutex> persistenceWguard(_persistenceEngine->getWLock());
            IPersistenceHandler::SP  oldHandler = _persistenceEngine->removeHandler(persistenceWguard, old->getBucketSpace(), docTypeName);
            if (_initComplete && oldHandler) {
                // TODO: Fix race with bucket db modifying ops.
                _persistenceEngine->grabExtraModifiedBuckets(old->getBucketSpace(), *oldHandler);
            }
        }
        _persistenceEngine->destroyIterators();
    }
    _matchEngine->removeSearchHandler(docTypeName);
    _summaryEngine->removeSearchHandler(docTypeName);
    _flushEngine->removeFlushHandler(docTypeName);
    _metricsEngine->removeMetricsHook(old->getMetricsUpdateHook());
    _metricsEngine->removeDocumentDBMetrics(old->getMetrics());
    _diskMemUsageSampler->notifier().removeDiskMemUsageListener(old->diskMemUsageListener());
    _diskMemUsageSampler->remove_transient_memory_usage_provider(old->transient_memory_usage_provider());
    // Caller should have removed & drained relevant timer tasks
    old->close();
}


Proton::MonitorReply::UP
Proton::ping(MonitorRequest::UP request, MonitorClient & client)
{
    (void) client;
    auto reply = std::make_unique<MonitorReply>();
    MonitorReply &ret = *reply;

    BootstrapConfig::SP configSnapshot = getActiveConfigSnapshot();
    const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
    ret.partid = protonConfig.partition;
    ret.distribution_key = protonConfig.distributionkey;
    ret.timestamp = (_matchEngine->isOnline()) ? 42 : 0;
    ret.activeDocs = (_matchEngine->isOnline()) ? getNumActiveDocs() : 0;
    ret.activeDocsRequested = request->reportActiveDocs;
    ret.is_blocking_writes = !_diskMemUsageSampler->writeFilter().acceptWriteOperation();
    return reply;
}

bool
Proton::triggerFlush()
{
    if (!_flushEngine || ! _flushEngine->HasThread()) {
        return false;
    }
    _flushEngine->triggerFlush();
    return true;
}

bool
Proton::prepareRestart()
{
    BootstrapConfig::SP configSnapshot = getActiveConfigSnapshot();
    return _prepareRestartHandler->prepareRestart(configSnapshot->getProtonConfig());
}

namespace {

void
updateExecutorMetrics(ExecutorMetrics &metrics,
                      const vespalib::ThreadStackExecutor::Stats &stats)
{
    metrics.update(stats);
}

}

void
Proton::updateMetrics(const metrics::MetricLockGuard &)
{
    {
        ContentProtonMetrics &metrics = _metricsEngine->root();
        auto tls = _tls->getTransLogServer();
        if (tls) {
            metrics.transactionLog.update(tls->getDomainStats());
        }

        const DiskMemUsageFilter &usageFilter = _diskMemUsageSampler->writeFilter();
        DiskMemUsageState usageState = usageFilter.usageState();
        metrics.resourceUsage.disk.set(usageState.diskState().usage());
        metrics.resourceUsage.diskUtilization.set(usageState.diskState().utilization());
        metrics.resourceUsage.memory.set(usageState.memoryState().usage());
        metrics.resourceUsage.memoryUtilization.set(usageState.memoryState().utilization());
        metrics.resourceUsage.transient_memory.set(usageFilter.get_relative_transient_memory_usage());
        metrics.resourceUsage.memoryMappings.set(usageFilter.getMemoryStats().getMappingsCount());
        metrics.resourceUsage.openFileDescriptors.set(FastOS_File::count_open_files());
        metrics.resourceUsage.feedingBlocked.set((usageFilter.acceptWriteOperation() ? 0.0 : 1.0));
    }
    {
        ContentProtonMetrics::ProtonExecutorMetrics &metrics = _metricsEngine->root().executor;
        updateExecutorMetrics(metrics.proton, _executor.getStats());
        if (_flushEngine) {
            updateExecutorMetrics(metrics.flush, _flushEngine->getExecutorStats());
        }
        if (_matchEngine) {
            updateExecutorMetrics(metrics.match, _matchEngine->getExecutorStats());
        }
        if (_summaryEngine) {
            updateExecutorMetrics(metrics.docsum, _summaryEngine->getExecutorStats());
        }
        if (_sharedExecutor) {
            metrics.shared.update(_sharedExecutor->getStats());
        }
        if (_warmupExecutor) {
            metrics.warmup.update(_warmupExecutor->getStats());
        }
    }
}

void
Proton::waitForInitDone()
{
    std::shared_lock<std::shared_mutex> guard(_mutex);
    for (const auto &kv : _documentDBMap) {
        kv.second->waitForInitDone();
    }
}

void
Proton::waitForOnlineState()
{
    std::shared_lock<std::shared_mutex> guard(_mutex);
    for (const auto &kv : _documentDBMap) {
        kv.second->waitForOnlineState();
    }
}

void
Proton::getComponentConfig(Consumer &consumer)
{
    _protonConfigurer.getComponentConfig().getComponentConfig(consumer);
    std::vector<DocumentDB::SP> dbs;
    {
        std::shared_lock<std::shared_mutex> guard(_mutex);
        for (const auto &kv : _documentDBMap) {
            dbs.push_back(kv.second);
        }
    }
    for (const auto &docDb : dbs) {
        vespalib::string name("proton.documentdb.");
        name.append(docDb->getDocTypeName().getName());
        int64_t gen = docDb->getActiveGeneration();
        if (docDb->getDelayedConfig()) {
            consumer.add(Config(name, gen, "has delayed attribute aspect change in config"));
        } else {
            consumer.add(Config(name, gen));
        }
    }
}

int64_t
Proton::getConfigGeneration()
{
    return _protonConfigurer.getActiveConfigSnapshot()->getBootstrapConfig()->getGeneration();
}

bool
Proton::updateNodeUp(BucketSpace bucketSpace, bool nodeUpInBucketSpace)
{
    std::lock_guard guard(_nodeUpLock);
    if (nodeUpInBucketSpace) {
        _nodeUp.insert(bucketSpace);
    } else {
        _nodeUp.erase(bucketSpace);
    }
    return !_nodeUp.empty();
}

void
Proton::setClusterState(BucketSpace bucketSpace, const storage::spi::ClusterState &calc)
{
    // forward info sent by cluster controller to persistence engine
    // about whether node is supposed to be up or not.  Match engine
    // needs to know this in order to stop serving queries.
    bool nodeUpInBucketSpace(calc.nodeUp());
    bool nodeRetired(calc.nodeRetired());
    bool nodeUp = updateNodeUp(bucketSpace, nodeUpInBucketSpace);
    _matchEngine->setNodeUp(nodeUp);
    if (_memoryFlushConfigUpdater) {
        _memoryFlushConfigUpdater->setNodeRetired(nodeRetired);
    }
}

namespace {

const vespalib::string MATCH_ENGINE = "matchengine";
const vespalib::string DOCUMENT_DB = "documentdb";
const vespalib::string FLUSH_ENGINE = "flushengine";
const vespalib::string TLS_NAME = "tls";
const vespalib::string RESOURCE_USAGE = "resourceusage";
const vespalib::string THREAD_POOLS = "threadpools";

struct StateExplorerProxy : vespalib::StateExplorer {
    const StateExplorer &explorer;
    explicit StateExplorerProxy(const StateExplorer &explorer_in) : explorer(explorer_in) {}
    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override { explorer.get_state(inserter, full); }
    std::vector<vespalib::string> get_children_names() const override { return explorer.get_children_names(); }
    std::unique_ptr<vespalib::StateExplorer> get_child(vespalib::stringref name) const override { return explorer.get_child(name); }
};

struct DocumentDBMapExplorer : vespalib::StateExplorer {
    typedef std::map<DocTypeName, DocumentDB::SP> DocumentDBMap;
    const DocumentDBMap &documentDBMap;
    std::shared_mutex &mutex;
    DocumentDBMapExplorer(const DocumentDBMap &documentDBMap_in, std::shared_mutex &mutex_in)
        : documentDBMap(documentDBMap_in), mutex(mutex_in) {}
    void get_state(const vespalib::slime::Inserter &, bool) const override {}
    std::vector<vespalib::string> get_children_names() const override {
        std::shared_lock<std::shared_mutex> guard(mutex);
        std::vector<vespalib::string> names;
        for (const auto &item: documentDBMap) {
            names.push_back(item.first.getName());
        }
        return names;
    }
    std::unique_ptr<vespalib::StateExplorer> get_child(vespalib::stringref name) const override {
        typedef std::unique_ptr<StateExplorer> Explorer_UP;
        std::shared_lock<std::shared_mutex> guard(mutex);
        auto result = documentDBMap.find(DocTypeName(vespalib::string(name)));
        if (result == documentDBMap.end()) {
            return Explorer_UP(nullptr);
        }
        return std::make_unique<DocumentDBExplorer>(result->second);
    }
};

} // namespace proton::<unnamed>

void
Proton::get_state(const vespalib::slime::Inserter &, bool) const
{
}

std::vector<vespalib::string>
Proton::get_children_names() const
{
    return {DOCUMENT_DB, THREAD_POOLS, MATCH_ENGINE, FLUSH_ENGINE, TLS_NAME, RESOURCE_USAGE};
}

std::unique_ptr<vespalib::StateExplorer>
Proton::get_child(vespalib::stringref name) const
{
    typedef std::unique_ptr<StateExplorer> Explorer_UP;
    if (name == MATCH_ENGINE && _matchEngine) {
        return std::make_unique<StateExplorerProxy>(*_matchEngine);
    } else if (name == DOCUMENT_DB) {
        return std::make_unique<DocumentDBMapExplorer>(_documentDBMap, _mutex);
    } else if (name == FLUSH_ENGINE && _flushEngine) {
        return std::make_unique<FlushEngineExplorer>(*_flushEngine);
    } else if (name == TLS_NAME && _tls) {
        return std::make_unique<search::transactionlog::TransLogServerExplorer>(_tls->getTransLogServer());
    } else if (name == RESOURCE_USAGE && _diskMemUsageSampler) {
        return std::make_unique<ResourceUsageExplorer>(_diskMemUsageSampler->writeFilter());
    } else if (name == THREAD_POOLS) {
        return std::make_unique<ProtonThreadPoolsExplorer>(_sharedExecutor.get(),
                                                           (_matchEngine) ? &_matchEngine->get_executor() : nullptr,
                                                           (_summaryEngine) ? &_summaryEngine->get_executor() : nullptr,
                                                           (_flushEngine) ? &_flushEngine->get_executor() : nullptr,
                                                           &_executor,
                                                           _warmupExecutor.get());
    }
    return Explorer_UP(nullptr);
}

std::shared_ptr<IDocumentDBReferenceRegistry>
Proton::getDocumentDBReferenceRegistry() const
{
    return _documentDBReferenceRegistry;
}

storage::spi::PersistenceProvider &
Proton::getPersistence()
{
    return *_persistenceEngine;
}

} // namespace proton
