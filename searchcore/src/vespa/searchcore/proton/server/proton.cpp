// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "proton.h"
#include "disk_mem_usage_sampler.h"
#include "document_db_explorer.h"
#include "documentdbconfig.h"
#include "fileconfigmanager.h"
#include "flushhandlerproxy.h"
#include "hw_info_explorer.h"
#include "initialize_threads_calculator.h"
#include "memoryflush.h"
#include "persistencehandlerproxy.h"
#include "prepare_restart_handler.h"
#include "proton_config_snapshot.h"
#include "proton_disk_layout.h"
#include "proton_thread_pools_explorer.h"
#include "resource_usage_explorer.h"
#include "searchhandlerproxy.h"
#include "simpleflush.h"

#include <vespa/document/base/exceptions.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/fnet/transport.h>
#include <vespa/metrics/updatehook.h>
#include <vespa/searchcore/proton/attribute/i_attribute_usage_listener.h>
#include <vespa/searchcore/proton/flushengine/flush_engine_explorer.h>
#include <vespa/searchcore/proton/flushengine/flushengine.h>
#include <vespa/searchcore/proton/flushengine/tls_stats_factory.h>
#include <vespa/searchcore/proton/matchengine/matchengine.h>
#include <vespa/searchcore/proton/metrics/content_proton_metrics.h>
#include <vespa/searchcore/proton/metrics/metrics_engine.h>
#include <vespa/searchcore/proton/persistenceengine/persistenceengine.h>
#include <vespa/searchcore/proton/reference/document_db_reference_registry.h>
#include <vespa/searchcore/proton/summaryengine/summaryengine.h>
#include <vespa/searchcore/proton/matching/session_manager_explorer.h>
#include <vespa/searchcore/proton/common/scheduled_forward_executor.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/diskindex/posting_list_cache.h>
#include <vespa/searchlib/transactionlog/trans_log_server_explorer.h>
#include <vespa/searchlib/transactionlog/translogserverapp.h>
#include <vespa/searchlib/util/fileheadertk.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/net/http/state_server.h>
#include <vespa/vespalib/util/blockingthreadstackexecutor.h>
#include <vespa/vespalib/util/cpu_usage.h>
#include <vespa/vespalib/util/host_name.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/mmap_file_allocator_factory.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/vespalib/util/sequencedtaskexecutor.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/fastos/file.h>
#ifdef __linux__
#include <malloc.h>
#endif

#include <sstream>

#include <vespa/log/log.h>
LOG_SETUP(".proton.server.proton");

using CpuCategory = vespalib::CpuUsage::Category;

using document::DocumentTypeRepo;
using search::diskindex::IPostingListCache;
using search::diskindex::PostingListCache;
using search::engine::MonitorReply;
using search::transactionlog::DomainStats;
using vespa::config::search::core::ProtonConfig;
using vespa::config::search::core::internal::InternalProtonType;
using vespalib::CpuUsage;
using vespalib::FileHeader;
using vespalib::IllegalStateException;
using vespalib::Slime;
using vespalib::compression::CompressionConfig;
using vespalib::makeLambdaTask;
using vespalib::slime::ArrayInserter;
using vespalib::slime::Cursor;

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
diskMemUsageSamplerConfig(const ProtonConfig &proton, const vespalib::HwInfo &hwInfo)
{
    return { proton.writefilter.memorylimit,
             proton.writefilter.disklimit,
             vespalib::from_s(proton.writefilter.sampleinterval),
             hwInfo };
}

uint32_t
computeRpcTransportThreads(const ProtonConfig & cfg, const vespalib::HwInfo::Cpu &cpuInfo) {
    bool areSearchAndDocsumAsync = cfg.docsum.async && cfg.search.async;
    return (cfg.rpc.transportthreads > 0)
            ? cfg.rpc.transportthreads
            : areSearchAndDocsumAsync
                ? cpuInfo.cores()/8
                : cpuInfo.cores();
}

struct MetricsUpdateHook : metrics::UpdateHook
{
    Proton &self;
    explicit MetricsUpdateHook(Proton &s)
        : metrics::UpdateHook("proton-hook", 5s),
          self(s)
    {}
    void updateMetrics(const MetricLockGuard &guard) override {
        self.updateMetrics(guard);
    }
};

const std::string CUSTOM_COMPONENT_API_PATH = "/state/v1/custom/component";

VESPA_THREAD_STACK_TAG(proton_close_executor);
VESPA_THREAD_STACK_TAG(proton_executor);

void ensureWritableDir(const std::string &dirName) {
    auto filename = dirName + "/tmp.filesystem.probe";
    vespalib::File probe(filename);
    probe.unlink();
    probe.open(vespalib::File::CREATE);
    probe.write("probe\n", 6, 0);
    probe.close();
    probe.unlink();
}

std::shared_ptr<IPostingListCache>
make_posting_list_cache(const ProtonConfig& cfg)
{
    if (cfg.search.io == ProtonConfig::Search::Io::MMAP ||
        (cfg.index.cache.postinglist.maxbytes == 0 && cfg.index.cache.bitvector.maxbytes == 0)) {
        return {};
    }
    return std::make_shared<PostingListCache>(cfg.index.cache.postinglist.maxbytes, cfg.index.cache.bitvector.maxbytes);
}

} // namespace <unnamed>

Proton::ProtonFileHeaderContext::ProtonFileHeaderContext(const std::string &creator)
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
        const std::string &name) const
{
    using Tag = vespalib::GenericHeader::Tag;

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
Proton::ProtonFileHeaderContext::setClusterName(const std::string & clusterName,
                                                const std::string & baseDir)
{
    if (!clusterName.empty()) {
        _cluster = clusterName;
        return;
    }
    // Derive cluster name from base dir.
    size_t cpos(baseDir.rfind('/'));
    if (cpos == std::string::npos)
        return;
    size_t rpos(baseDir.rfind('/', cpos - 1));
    if (rpos == std::string::npos)
        return;
    size_t clpos(baseDir.rfind('/', rpos - 1));
    if (clpos == std::string::npos)
        return;
    if (baseDir.substr(clpos + 1, 8) != "cluster.")
        return;
    _cluster = baseDir.substr(clpos + 9, rpos - clpos - 9);
}


Proton::Proton(FNET_Transport & transport, const config::ConfigUri & configUri,
               const std::string &progName, vespalib::duration subscribeTimeout)
    : IProtonConfigurerOwner(),
      search::engine::MonitorServer(),
      IDocumentDBOwner(),
      StatusProducer(),
      IPersistenceEngineOwner(),
      ComponentConfigProducer(),
      _cpu_util(),
      _hw_info(),
      _transport(transport),
      _configUri(configUri),
      _mutex(),
      _metricsHook(std::make_unique<MetricsUpdateHook>(*this)),
      _metricsEngine(std::make_unique<MetricsEngine>()),
      _fileHeaderContext(progName),
      _attribute_interlock(std::make_shared<search::attribute::Interlock>()),
      _tls(),
      _diskMemUsageSampler(),
      _persistenceEngine(),
      _documentDBMap(),
      _matchEngine(),
      _summaryEngine(),
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
      _executor(1, CpuUsage::wrap(proton_executor, CpuCategory::SETUP)),
      _protonDiskLayout(),
      _protonConfigurer(_executor, *this, _protonDiskLayout),
      _protonConfigFetcher(_transport, configUri, _protonConfigurer, subscribeTimeout),
      _shared_service(),
      _sessionManager(),
      _scheduler(),
      _compile_cache_executor_binding(),
      _queryLimiter(),
      _distributionKey(-1),
      _numThreadsPerSearch(1),
      _isInitializing(true),
      _abortInit(false),
      _initStarted(false),
      _initComplete(false),
      _initDocumentDbsInSequence(false),
      _has_shut_down_config_and_state_components(false),
      _documentDBReferenceRegistry(std::make_shared<DocumentDBReferenceRegistry>()),
      _nodeUpLock(),
      _nodeUp(),
      _posting_list_cache(),
      _last_posting_list_cache_stats(),
      _last_bitvector_cache_stats()
{ }

BootstrapConfig::SP
Proton::init()
{
    assert( ! _initStarted && ! _initComplete );
    _initStarted = true;
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
    ensureWritableDir(protonConfig.basedir);
    const vespalib::HwInfo & hwInfo = configSnapshot->getHwInfo();
    _hw_info = hwInfo;
    _numThreadsPerSearch = std::min(hwInfo.cpu().cores(), uint32_t(protonConfig.numthreadspersearch));

    setBucketCheckSumType(protonConfig);
    setFS4Compression(protonConfig);
    _diskMemUsageSampler = std::make_unique<DiskMemUsageSampler>(protonConfig.basedir, hwInfo);
    _posting_list_cache = make_posting_list_cache(protonConfig);

    _tls = std::make_unique<TLS>(_configUri.createWithNewId(protonConfig.tlsconfigid), _fileHeaderContext);
    _metricsEngine->addMetricsHook(*_metricsHook);
    _fileHeaderContext.setClusterName(protonConfig.clustername, protonConfig.basedir);
    _matchEngine = std::make_unique<MatchEngine>(protonConfig.numsearcherthreads,
                                                 getNumThreadsPerSearch(),
                                                 protonConfig.distributionkey,
                                                 protonConfig.search.async);
    _matchEngine->set_issue_forwarding(protonConfig.forwardIssues);
    _distributionKey = protonConfig.distributionkey;
    _summaryEngine = std::make_unique<SummaryEngine>(protonConfig.numsummarythreads, protonConfig.docsum.async);
    _summaryEngine->set_issue_forwarding(protonConfig.forwardIssues);
    _sessionManager = std::make_unique<matching::SessionManager>(protonConfig.grouping.sessionmanager.maxentries);

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
    _protonDiskLayout = std::make_unique<ProtonDiskLayout>(_transport, protonConfig.basedir, protonConfig.tlsspec);
    std::filesystem::current_path(std::filesystem::path(protonConfig.basedir));
    vespalib::alloc::MmapFileAllocatorFactory::instance().setup(protonConfig.basedir + "/swapdirs");
    _tls->start(_transport, hwInfo.cpu().cores());
    _flushEngine = std::make_unique<FlushEngine>(std::make_shared<flushengine::TlsStatsFactory>(_tls->getTransLogServer()),
                                                 strategy, flush.maxconcurrent, vespalib::from_s(flush.idleinterval));
    _metricsEngine->addExternalMetrics(_summaryEngine->getMetrics());

    LOG(debug, "Start proton server with root at %s and cwd at %s",
        protonConfig.basedir.c_str(), std::filesystem::current_path().string().c_str());

    _persistenceEngine = std::make_unique<PersistenceEngine>(*this, _diskMemUsageSampler->writeFilter(),
                                                             _diskMemUsageSampler->notifier(),
                                                             protonConfig.visit.defaultserializedsize,
                                                             protonConfig.visit.ignoremaxbytes);
    _shared_service = std::make_unique<SharedThreadingService>(
            SharedThreadingServiceConfig::make(protonConfig, hwInfo.cpu()), _transport, *_persistenceEngine);
    _scheduler = std::make_unique<ScheduledForwardExecutor>(_transport, _shared_service->shared());
    _diskMemUsageSampler->setConfig(diskMemUsageSamplerConfig(protonConfig, hwInfo), *_scheduler);

    std::string fileConfigId;
    _compile_cache_executor_binding = vespalib::eval::CompileCache::bind(_shared_service->shared_raw());

    InitializeThreadsCalculator calc(hwInfo.cpu(), protonConfig.basedir, protonConfig.initialize.threads);
    LOG(info, "Start initializing components: threads=%u, configured=%u",
        calc.num_threads(), protonConfig.initialize.threads);
    _initDocumentDbsInSequence = (calc.num_threads() == 1);
    _protonConfigurer.applyInitialConfig(calc.threads());

    _prepareRestartHandler = std::make_unique<PrepareRestartHandler>(*_flushEngine);
    RPCHooks::Params rpcParams(*this, protonConfig.rpcport, _configUri, protonConfig.slobrokconfigid,
                               std::max(2u, computeRpcTransportThreads(protonConfig, hwInfo.cpu())));
    _rpcHooks = std::make_unique<RPCHooks>(rpcParams);
    _metricsEngine->addExternalMetrics(_rpcHooks->proto_rpc_adapter_metrics());

    waitForInitDone();
    LOG(info, "Done initializing components");
    calc.init_done();

    _metricsEngine->start(_configUri);
    _stateServer = std::make_unique<vespalib::StateServer>(protonConfig.httpport, _healthAdapter,
                                                           _metricsEngine->metrics_producer(), *this);
    _customComponentBindToken = _stateServer->repo().bind(CUSTOM_COMPONENT_API_PATH, _genericStateHandler);
    _customComponentRootToken = _stateServer->repo().add_root_resource(CUSTOM_COMPONENT_API_PATH);

    _executor.sync();
    waitForOnlineState();
    _rpcHooks->set_online();

    _flushEngine->start();
    vespalib::duration pruneSessionsInterval = vespalib::from_s(protonConfig.grouping.sessionmanager.pruning.interval);
    _sessionPruneHandle = _scheduler->scheduleAtFixedRate(makeLambdaTask([&]() {
        _sessionManager->pruneTimedOutSessions(vespalib::steady_clock::now(), _shared_service->shared());
    }), pruneSessionsInterval, pruneSessionsInterval);
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
    _matchEngine->set_issue_forwarding(protonConfig.forwardIssues);
    _summaryEngine->set_issue_forwarding(protonConfig.forwardIssues);

    _queryLimiter.configure(protonConfig.search.memory.limiter.maxthreads,
                            protonConfig.search.memory.limiter.mincoverage,
                            protonConfig.search.memory.limiter.minhits);
    const std::shared_ptr<const DocumentTypeRepo> repo = configSnapshot->getDocumentTypeRepoSP();

    _diskMemUsageSampler->setConfig(diskMemUsageSamplerConfig(protonConfig, configSnapshot->getHwInfo()), *_scheduler);
    if (_memoryFlushConfigUpdater) {
        _memoryFlushConfigUpdater->setConfig(protonConfig.flush.memory);
        _flushEngine->kick();
    }
}

std::shared_ptr<DocumentDBConfigOwner>
Proton::addDocumentDB(const DocTypeName &docTypeName,
                      document::BucketSpace bucketSpace,
                      const std::string &configId,
                      const BootstrapConfig::SP &bootstrapConfig,
                      const std::shared_ptr<DocumentDBConfig> &documentDBConfig,
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
            return {};
        }
    } catch (const document::DocumentTypeNotFoundException & e) {
        LOG(warning,
            "Did not find document type '%s' in the document manager. "
            "Skipping creating document database for this type",
            docTypeName.toString().c_str());
        return {};
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
    _sessionPruneHandle.reset();
    if (_diskMemUsageSampler) {
        _diskMemUsageSampler->close();
    }
    _scheduler.reset();
    _executor.shutdown();
    _executor.sync();
    _rpcHooks.reset();
    if (_flushEngine) {
        _flushEngine->close();
    }
    if (_shared_service) {
        _shared_service->sync_all_executors();
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

        vespalib::ThreadStackExecutor closePool(std::min(_documentDBMap.size(), numCores),
                                                CpuUsage::wrap(proton_close_executor, CpuCategory::SETUP));
        closeDocumentDBs(closePool);
    }
    _sessionManager.reset();
    _documentDBMap.clear();
    _persistenceEngine.reset();
    _tls.reset();
    _compile_cache_executor_binding.reset();
    _shared_service.reset();
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

ActiveDocs
Proton::getNumActiveDocs() const
{
    ActiveDocs sum;
    std::shared_lock<std::shared_mutex> guard(_mutex);
    for (const auto &kv : _documentDBMap) {
        sum += kv.second->getNumActiveDocs();
    }
    return sum;
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

std::string
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
                      const std::shared_ptr<DocumentDBConfig> &documentDBConfig,
                      InitializeThreads initializeThreads)
{
    const ProtonConfig &config(bootstrapConfig->getProtonConfig());

    std::lock_guard<std::shared_mutex> guard(_mutex);
    DocTypeName docTypeName(docType.getName());
    auto it = _documentDBMap.find(docTypeName);
    if (it != _documentDBMap.end()) {
        return it->second;
    }

    std::string db_dir = config.basedir + "/documents/" + docTypeName.toString();
    std::filesystem::create_directory(std::filesystem::path(db_dir)); // Assume parent is created.
    auto config_store = std::make_unique<FileConfigManager>(_transport, db_dir + "/config",
                                                            documentDBConfig->getConfigId(), docTypeName.getName());
    config_store->setProtonConfig(bootstrapConfig->getProtonConfigSP());
    if (!initializeThreads) {
        // If configured value for initialize threads was 0, or we
        // are performing a reconfig after startup has completed, then use
        // 1 thread per document type.
        initializeThreads = std::make_shared<vespalib::ThreadStackExecutor>(1);
    }
    auto ret = DocumentDB::create(config.basedir + "/documents",
                                  documentDBConfig,
                                  config.tlsspec,
                                  _queryLimiter,
                                  docTypeName,
                                  bucketSpace,
                                  config,
                                  *this,
                                  *_shared_service,
                                  *_tls->getTransLogServer(),
                                  *_metricsEngine,
                                  _fileHeaderContext,
                                  _attribute_interlock,
                                  std::move(config_store),
                                  initializeThreads,
                                  bootstrapConfig->getHwInfo(),
                                  _posting_list_cache);
    try {
        ret->start();
    } catch (vespalib::Exception &e) {
        LOG(warning, "Failed to start database for document type '%s'; %s",
            docTypeName.toString().c_str(), e.what());
        return {};
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
    _diskMemUsageSampler->add_transient_usage_provider(ret->transient_usage_provider());
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
    _diskMemUsageSampler->remove_transient_usage_provider(old->transient_usage_provider());
    // Caller should have removed & drained relevant timer tasks
    old->close();
}


std::unique_ptr<MonitorReply>
Proton::ping(std::unique_ptr<MonitorRequest>, MonitorClient &)
{
    auto reply = std::make_unique<MonitorReply>();
    MonitorReply &ret = *reply;

    BootstrapConfig::SP configSnapshot = getActiveConfigSnapshot();
    const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
    ret.distribution_key = protonConfig.distributionkey;
    if (_matchEngine->isOnline()) {
        ret.timestamp = 42;
        auto docs = getNumActiveDocs();
        ret.activeDocs = docs.active;
        ret.targetActiveDocs = docs.target_active;
    } else {
        ret.timestamp = 0;
        ret.activeDocs = 0;
        ret.targetActiveDocs = 0; // TODO vekterli hmm... or target anyway ...
    }
    ret.is_blocking_writes = !_diskMemUsageSampler->writeFilter().acceptWriteOperation();
    return reply;
}

bool
Proton::triggerFlush()
{
    if (!_flushEngine || ! _flushEngine->has_thread()) {
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
updateExecutorMetrics(ExecutorMetrics &metrics, const vespalib::ExecutorStats &stats)
{
    metrics.update(stats);
}

void
updateSessionCacheMetrics(ContentProtonMetrics &metrics, proton::matching::SessionManager &sessionManager)
{
    auto searchStats = sessionManager.getSearchStats();
    metrics.sessionCache.search.update(searchStats);

    auto groupingStats = sessionManager.getGroupingStats();
    metrics.sessionCache.grouping.update(groupingStats);
}

void
update_cache_stats(CacheMetrics& metrics, const vespalib::CacheStats& stats, vespalib::CacheStats& last_stats)
{
    metrics.update_metrics(stats, last_stats);
    last_stats = stats;
}

}

void
Proton::updateMetrics(const metrics::MetricLockGuard &)
{
    {
        ContentProtonMetrics &metrics = _metricsEngine->root();
        metrics.configGeneration.set(getConfigGeneration());
        auto tls = _tls->getTransLogServer();
        if (tls) {
            metrics.transactionLog.update(tls->getDomainStats());
        }

        const DiskMemUsageFilter &usageFilter = _diskMemUsageSampler->writeFilter();
        auto dm_metrics = usageFilter.get_metrics();
        metrics.resourceUsage.disk.set(dm_metrics.non_transient_disk_usage());
        metrics.resourceUsage.disk_usage.total.set(dm_metrics.total_disk_usage());
        metrics.resourceUsage.disk_usage.total_util.set(dm_metrics.total_disk_utilization());
        metrics.resourceUsage.disk_usage.transient.set(dm_metrics.transient_disk_usage());

        metrics.resourceUsage.memory.set(dm_metrics.non_transient_memory_usage());
        metrics.resourceUsage.memory_usage.total.set(dm_metrics.total_memory_usage());
        metrics.resourceUsage.memory_usage.total_util.set(dm_metrics.total_memory_utilization());
        metrics.resourceUsage.memory_usage.transient.set(dm_metrics.transient_memory_usage());

        metrics.resourceUsage.memoryMappings.set(usageFilter.getMemoryStats().getMappingsCount());
        metrics.resourceUsage.openFileDescriptors.set(FastOS_File::count_open_files());
        metrics.resourceUsage.feedingBlocked.set((usageFilter.acceptWriteOperation() ? 0.0 : 1.0));
#ifdef __linux__
#if __GLIBC_PREREQ(2, 33)
        struct mallinfo2 mallocInfo = mallinfo2();
        metrics.resourceUsage.mallocArena.set(mallocInfo.arena);
#else
        struct mallinfo mallocInfo = mallinfo();
        // Vespamalloc reports arena in 1M blocks as an 'int' is too small.
        // If we use something else than vespamalloc this must be changed.
        metrics.resourceUsage.mallocArena.set(uint64_t(mallocInfo.arena) * 1_Mi);
#endif
#else
        metrics.resourceUsage.mallocArena.set(UINT64_C(0));
#endif
        auto cpu_util = _cpu_util.get_util();
        metrics.resourceUsage.cpu_util.setup.set(cpu_util[CpuCategory::SETUP]);
        metrics.resourceUsage.cpu_util.read.set(cpu_util[CpuCategory::READ]);
        metrics.resourceUsage.cpu_util.write.set(cpu_util[CpuCategory::WRITE]);
        metrics.resourceUsage.cpu_util.compact.set(cpu_util[CpuCategory::COMPACT]);
        metrics.resourceUsage.cpu_util.other.set(cpu_util[CpuCategory::OTHER]);
        updateSessionCacheMetrics(metrics, session_manager());
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
        if (_shared_service) {
            metrics.shared.update(_shared_service->shared().getStats());
            metrics.field_writer.update(_shared_service->field_writer().getStats());
        }
    }
    if (_posting_list_cache) {
        update_cache_stats(_metricsEngine->root().index.cache.postinglist,
                           _posting_list_cache->get_stats(),
                           _last_posting_list_cache_stats);
        update_cache_stats(_metricsEngine->root().index.cache.bitvector,
                           _posting_list_cache->get_bitvector_stats(),
                           _last_bitvector_cache_stats);
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
        std::string name("proton.documentdb.");
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
    bool nodeUpInBucketSpace(calc.nodeUp()); // TODO rename calculator function to imply bucket space affinity
    bool nodeRetired = calc.nodeRetired();
    bool nodeMaintenance = calc.nodeMaintenance();
    bool nodeUp = updateNodeUp(bucketSpace, nodeUpInBucketSpace);
    _matchEngine->setNodeUp(nodeUp);
    _matchEngine->setNodeMaintenance(nodeMaintenance); // Note: _all_ bucket spaces in maintenance
    if (_memoryFlushConfigUpdater) {
        _memoryFlushConfigUpdater->set_node_retired_or_maintenance(nodeRetired || nodeMaintenance);
    }
}

namespace {

const std::string MATCH_ENGINE = "matchengine";
const std::string DOCUMENT_DB = "documentdb";
const std::string FLUSH_ENGINE = "flushengine";
const std::string TLS_NAME = "tls";
const std::string RESOURCE_USAGE = "resourceusage";
const std::string THREAD_POOLS = "threadpools";
const std::string HW_INFO = "hwinfo";
const std::string SESSION = "session";


struct StateExplorerProxy : vespalib::StateExplorer {
    const StateExplorer &explorer;
    explicit StateExplorerProxy(const StateExplorer &explorer_in) : explorer(explorer_in) {}
    void get_state(const vespalib::slime::Inserter &inserter, bool full) const override { explorer.get_state(inserter, full); }
    std::vector<std::string> get_children_names() const override { return explorer.get_children_names(); }
    std::unique_ptr<vespalib::StateExplorer> get_child(std::string_view name) const override { return explorer.get_child(name); }
};

struct DocumentDBMapExplorer : vespalib::StateExplorer {
    using DocumentDBMap = std::map<DocTypeName, DocumentDB::SP>;
    const DocumentDBMap &documentDBMap;
    std::shared_mutex &mutex;
    DocumentDBMapExplorer(const DocumentDBMap &documentDBMap_in, std::shared_mutex &mutex_in)
        : documentDBMap(documentDBMap_in), mutex(mutex_in) {}
    void get_state(const vespalib::slime::Inserter &, bool) const override {}
    std::vector<std::string> get_children_names() const override {
        std::shared_lock<std::shared_mutex> guard(mutex);
        std::vector<std::string> names;
        for (const auto &item: documentDBMap) {
            names.push_back(item.first.getName());
        }
        return names;
    }
    std::unique_ptr<vespalib::StateExplorer> get_child(std::string_view name) const override {
        std::shared_lock<std::shared_mutex> guard(mutex);
        auto result = documentDBMap.find(DocTypeName(std::string(name)));
        if (result == documentDBMap.end()) {
            return {};
        }
        return std::make_unique<DocumentDBExplorer>(result->second);
    }
};

} // namespace proton::<unnamed>

void
Proton::get_state(const vespalib::slime::Inserter &, bool) const
{
}

std::vector<std::string>
Proton::get_children_names() const
{
    return {DOCUMENT_DB, THREAD_POOLS, MATCH_ENGINE, FLUSH_ENGINE, TLS_NAME, HW_INFO, RESOURCE_USAGE, SESSION};
}

std::unique_ptr<vespalib::StateExplorer>
Proton::get_child(std::string_view name) const
{
    if (name == MATCH_ENGINE && _matchEngine) {
        return std::make_unique<StateExplorerProxy>(*_matchEngine);
    } else if (name == DOCUMENT_DB) {
        return std::make_unique<DocumentDBMapExplorer>(_documentDBMap, _mutex);
    } else if (name == FLUSH_ENGINE && _flushEngine) {
        return std::make_unique<FlushEngineExplorer>(*_flushEngine);
    } else if (name == TLS_NAME && _tls) {
        return std::make_unique<search::transactionlog::TransLogServerExplorer>(_tls->getTransLogServer());
    } else if (name == RESOURCE_USAGE && _diskMemUsageSampler && _persistenceEngine) {
        return std::make_unique<ResourceUsageExplorer>(_diskMemUsageSampler->writeFilter(),
                                                       _persistenceEngine->get_resource_usage_tracker());
    } else if (name == THREAD_POOLS) {
        return std::make_unique<ProtonThreadPoolsExplorer>((_shared_service) ? &_shared_service->shared() : nullptr,
                                                           (_matchEngine) ? &_matchEngine->get_executor() : nullptr,
                                                           (_summaryEngine) ? &_summaryEngine->get_executor() : nullptr,
                                                           (_flushEngine) ? &_flushEngine->get_executor() : nullptr,
                                                           &_executor,
                                                           (_shared_service) ? &_shared_service->field_writer() : nullptr);

    } else if (name == HW_INFO) {
        return std::make_unique<HwInfoExplorer>(_hw_info);
    } else if (name == SESSION) {
        return std::make_unique<matching::SessionManagerExplorer>(*_sessionManager);
    }
    return {};
}

std::shared_ptr<IDocumentDBReferenceRegistry>
Proton::getDocumentDBReferenceRegistry() const
{
    return _documentDBReferenceRegistry;
}

matching::SessionManager &
Proton::session_manager() {
    return *_sessionManager;
}

storage::spi::PersistenceProvider &
Proton::getPersistence()
{
    return *_persistenceEngine;
}

metrics::MetricManager &
Proton::getMetricManager() {
    return _metricsEngine->getManager();
}

} // namespace proton
