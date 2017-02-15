// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "data_directory_upgrader.h"
#include "disk_mem_usage_sampler.h"
#include "document_db_explorer.h"
#include "flushhandlerproxy.h"
#include "memoryflush.h"
#include "persistencehandlerproxy.h"
#include "persistenceproviderproxy.h"
#include "proton.h"
#include "protonconfigurer.h"
#include "resource_usage_explorer.h"
#include "searchhandlerproxy.h"
#include "simpleflush.h"

#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/messagebus/emptyreply.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/proton/flushengine/flush_engine_explorer.h>
#include <vespa/searchcore/proton/flushengine/prepare_restart_flush_strategy.h>
#include <vespa/searchcore/proton/flushengine/tls_stats_factory.h>
#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>
#include <vespa/searchlib/aggregation/forcelink.hpp>
#include <vespa/searchlib/common/packets.h>
#include <vespa/searchlib/expression/forcelink.hpp>
#include <vespa/searchlib/transactionlog/trans_log_server_explorer.h>
#include <vespa/searchlib/util/fileheadertk.h>
#include <vespa/vespalib/data/fileheader.h>
#include <vespa/vespalib/data/slime/slime.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/util/closuretask.h>
#include <vespa/vespalib/util/random.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/common/hw_info_sampler.h>
#include <vespa/searchcore/proton/reference/document_db_referent_registry.h>
#include <vespa/searchcore/proton/reference/i_document_db_referent.h>
#include <vespa/document/base/exceptions.h>
#include <vespa/log/log.h>
LOG_SETUP(".proton.server.proton");

using document::DocumentTypeRepo;
using vespalib::FileHeader;
using vespalib::IllegalStateException;
using vespalib::LockGuard;
using vespalib::MonitorGuard;
using vespalib::RWLockReader;
using vespalib::RWLockWriter;
using vespalib::Slime;
using vespalib::slime::ArrayInserter;
using vespalib::slime::Cursor;
using vespalib::slime::ObjectInserter;

using search::TuneFileDocumentDB;
using search::index::Schema;
using search::index::SchemaBuilder;
using search::transactionlog::DomainStats;
using vespa::config::search::core::ProtonConfig;
using vespa::config::search::core::internal::InternalProtonType;
using document::CompressionConfig;
using searchcorespi::IIndexManagerFactory;

namespace proton {

namespace {

using search::fs4transport::FS4PersistentPacketStreamer;

CompressionConfig::Type
convert(InternalProtonType::Packetcompresstype type)
{
    switch (type) {
      case InternalProtonType::LZ4: return CompressionConfig::LZ4;
      default: return CompressionConfig::LZ4;
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
diskMemUsageSamplerConfig(const ProtonConfig &proton)
{
    return DiskMemUsageSampler::Config(
            proton.writefilter.memorylimit,
            proton.writefilter.disklimit,
            proton.writefilter.sampleinterval);
}

}

static const vespalib::string CUSTOM_COMPONENT_API_PATH = "/state/v1/custom/component";

Proton::ProtonFileHeaderContext::ProtonFileHeaderContext(const Proton &proton_,
        const vespalib::string &creator)
    : _proton(proton_),
      _hostName(),
      _creator(creator),
      _cluster(),
      _pid(getpid())
{
    _hostName = FastOS_Socket::getHostName();
    assert(!_hostName.empty());
}


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
Proton::ProtonFileHeaderContext::setClusterName(const vespalib::string &
                                                clusterName,
                                                const vespalib::string &
                                                baseDir)
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
               uint64_t subscribeTimeout)
    : IBootstrapOwner(),
      search::engine::MonitorServer(),
      IDocumentDBOwner(),
      StatusProducer(),
      PersistenceProviderFactory(),
      IPersistenceEngineOwner(),
      ComponentConfigProducer(),
      _protonConfigurer(configUri, this, subscribeTimeout),
      _configUri(configUri),
      _lock(),
      _metricsHook(*this),
      _metricsEngine(),
      _fileHeaderContext(*this, progName),
      _tls(),
      _diskMemUsageSampler(),
      _persistenceEngine(),
      _persistenceProxy(),
      _documentDBMap(),
      _matchEngine(),
      _summaryEngine(),
      _docsumBySlime(),
      _memoryFlushConfigUpdater(),
      _flushEngine(),
      _rpcHooks(),
      _healthAdapter(*this),
      _componentConfig(),
      _genericStateHandler(CUSTOM_COMPONENT_API_PATH, *this),
      _customComponentBindToken(),
      _customComponentRootToken(),
      _stateServer(),
      _fs4Server(),
      // This executor can only have 1 thread as it is used for
      // serializing startup.
      _executor(1, 128 * 1024),
      _warmupExecutor(),
      _summaryExecutor(),
      _allowReconfig(false),
      _initialProtonConfig(),
      _activeConfigSnapshot(),
      _activeConfigSnapshotGeneration(0),
      _pendingConfigSnapshot(),
      _configLock(),
      _queryLimiter(),
      _clock(0.010),
      _threadPool(128 * 1024),
      _libraries(),
      _indexManagerFactoryRegistry(),
      _configGenMonitor(),
      _configGen(0),
      _distributionKey(-1),
      _isInitializing(true),
      _isReplayDone(false),
      _abortInit(false),
      _initStarted(false),
      _initComplete(false),
      _initDocumentDbsInSequence(false),
      _hwInfo(),
      _hwInfoSampler(),
      _documentDBReferentRegistry()
{
    _documentDBReferentRegistry = std::make_shared<DocumentDBReferentRegistry>();
}

BootstrapConfig::SP
Proton::init()
{
    assert( ! _initStarted && ! _initComplete );
    _initStarted = true;
    if (_threadPool.NewThread(&_clock, NULL) == NULL) {
        throw IllegalStateException("Failed starting thread for the cheap clock");
    }
    _protonConfigurer.start();
    BootstrapConfig::SP configSnapshot = _pendingConfigSnapshot.get();
    assert(configSnapshot.get() != NULL);

    const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();

    if (!performDataDirectoryUpgrade(protonConfig.basedir)) {
        _abortInit = true;
    }
    return configSnapshot;
}

void
Proton::init(const BootstrapConfig::SP & configSnapshot)
{
    assert( _initStarted && ! _initComplete );
    const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
    const auto &samplerCfgArgs = protonConfig.hwinfo.disk;
    HwInfoSampler::Config samplerCfg(samplerCfgArgs.writespeed,
                                     samplerCfgArgs.slowwritespeedlimit,
                                     samplerCfgArgs.samplewritesize);
    _hwInfoSampler = std::make_unique<HwInfoSampler>(protonConfig.basedir,
                                                     samplerCfg);
    _hwInfo = _hwInfoSampler->hwInfo();
    setFS4Compression(protonConfig);
    _diskMemUsageSampler = std::make_unique<DiskMemUsageSampler>
                           (protonConfig.basedir,
                            diskMemUsageSamplerConfig(protonConfig));

    _initialProtonConfig.reset(new ProtonConfig(protonConfig));
    _componentConfig.addConfig(vespalib::ComponentConfigProducer::Config("proton",
                                       configSnapshot->getGeneration(),
                                       "config obtained at startup"));

    _metricsEngine.reset(new MetricsEngine());
    _metricsEngine->addMetricsHook(_metricsHook);
    _fileHeaderContext.setClusterName(protonConfig.clustername,
                                      protonConfig.basedir);
    _tls.reset(new TLS(_configUri.createWithNewId(protonConfig.tlsconfigid), _fileHeaderContext));
    _matchEngine.reset(new MatchEngine(protonConfig.numsearcherthreads,
                                       protonConfig.numthreadspersearch,
                                       protonConfig.distributionkey));
    _distributionKey = protonConfig.distributionkey;
    _summaryEngine.reset(new SummaryEngine(protonConfig.numsummarythreads));
    _docsumBySlime.reset(new DocsumBySlime(*_summaryEngine));
    IFlushStrategy::SP strategy;
    const ProtonConfig::Flush & flush(protonConfig.flush);
    switch (flush.strategy) {
    case ProtonConfig::Flush::MEMORY: {
        MemoryFlush::SP memoryFlush = std::make_shared<MemoryFlush>(
                MemoryFlushConfigUpdater::convertConfig(flush.memory));
        _memoryFlushConfigUpdater = std::make_unique<MemoryFlushConfigUpdater>(memoryFlush, flush.memory);
        _diskMemUsageSampler->notifier().addDiskMemUsageListener(_memoryFlushConfigUpdater.get());
        strategy = memoryFlush;
        break;
    }
    case ProtonConfig::Flush::SIMPLE:
    default:
        strategy.reset(new SimpleFlush());
        break;
    }
    vespalib::mkdir(protonConfig.basedir + "/documents", true);
    vespalib::chdir(protonConfig.basedir);
    _tls->start();
    _flushEngine.reset(new FlushEngine(std::make_shared<flushengine::TlsStatsFactory>(_tls->getTransLogServer()),
                                       strategy, flush.maxconcurrent, flush.idleinterval*1000));
    _fs4Server.reset(new TransportServer(*_matchEngine, *_summaryEngine, *this, protonConfig.ptport, TransportServer::DEBUG_ALL));
    _fs4Server->setTCPNoDelay(true);
    _metricsEngine->addExternalMetrics(_fs4Server->getMetrics());

    char tmp[1024];
    LOG(debug, "Start proton server with root at %s and cwd at %s",
        protonConfig.basedir.c_str(), getcwd(tmp, sizeof(tmp)));

    _persistenceEngine.reset(new PersistenceEngine(*this,
                                                   _diskMemUsageSampler->writeFilter(),
                                                   protonConfig.visit.defaultserializedsize,
                                                   protonConfig.visit.ignoremaxbytes));


    vespalib::string fileConfigId;
    _warmupExecutor.reset(new vespalib::ThreadStackExecutor(4, 128*1024));
    _summaryExecutor.reset(new vespalib::ThreadStackExecutor(protonConfig.summary.log.numthreads, 128*1024));
    InitializeThreads initializeThreads;
    if (protonConfig.initialize.threads > 0) {
        initializeThreads = std::make_shared<vespalib::ThreadStackExecutor>
                            (protonConfig.initialize.threads, 128 * 1024);
        _initDocumentDbsInSequence = (protonConfig.initialize.threads == 1);
    }
    applyConfig(configSnapshot, initializeThreads);
    initializeThreads.reset();

    if (_persistenceEngine.get() != NULL) {
        _persistenceProxy.reset(new ProviderStub(protonConfig.
                                                 persistenceprovider.port,
                                                 protonConfig.
                                                 persistenceprovider.threads,
                                                 *configSnapshot->
                                                 getDocumentTypeRepoSP(),
                                                 *this));
    }

    RPCHooks::Params rpcParams(*this, protonConfig.rpcport, _configUri.getConfigId());
    rpcParams.slobrok_config = _configUri.createWithNewId(protonConfig.slobrokconfigid);
    _rpcHooks.reset(new RPCHooks(rpcParams));

    waitForInitDone();

    _metricsEngine->start(_configUri);
    _stateServer.reset(new vespalib::StateServer(protonConfig.httpport, _healthAdapter, _metricsEngine->metrics_producer(), *this));
    _customComponentBindToken = _stateServer->repo().bind(CUSTOM_COMPONENT_API_PATH, _genericStateHandler);
    _customComponentRootToken = _stateServer->repo().add_root_resource(CUSTOM_COMPONENT_API_PATH);

    _executor.sync();
    waitForOnlineState();
    _isReplayDone = true;
    bool startOk = _fs4Server->start();
    int port = _fs4Server->getListenPort();
    _matchEngine->setOnline();
    _matchEngine->setInService();
    LOG(debug,
        "Started fs4 interface (startOk=%s, port=%d)",
        startOk ? "true" : "false",
        port);
    _flushEngine->start();
    _allowReconfig = true;
    _isInitializing = false;
    _executor.execute(
            vespalib::makeTask(
                    vespalib::makeClosure(
                            this,
                            &proton::Proton::performReconfig)));
    _initComplete = true;
}

bool
Proton::performDataDirectoryUpgrade(const vespalib::string &baseDir)
{
    // TODO: Remove this functionality when going to Vespa 6.
    vespalib::string scanDir = baseDir.substr(0, baseDir.rfind('/'));
    LOG(debug, "About to perform data directory upgrade: scanDir='%s', destDir='%s'",
        scanDir.c_str(), baseDir.c_str());
    DataDirectoryUpgrader upgrader(scanDir, baseDir);
    DataDirectoryUpgrader::ScanResult scanResult = upgrader.scan();
    DataDirectoryUpgrader::UpgradeResult upgradeResult = upgrader.upgrade(scanResult);
    if (upgradeResult.getStatus() == DataDirectoryUpgrader::ERROR) {
        LOG(error, "Data directory upgrade failed: '%s'. Please consult Vespa release notes on how to manually fix this issue. "
            "The search node will not start until this issue has been fixed", upgradeResult.getDesc().c_str());
        return false;
    } else if (upgradeResult.getStatus() == DataDirectoryUpgrader::IGNORE) {
        LOG(debug, "Data directory upgrade ignored: %s", upgradeResult.getDesc().c_str());
    } else if (upgradeResult.getStatus() == DataDirectoryUpgrader::COMPLETE) {
        LOG(info, "Data directory upgrade completed: %s", upgradeResult.getDesc().c_str());
    }
    return true;
}

void
Proton::loadLibrary(const vespalib::string &libName)
{
    searchcorespi::IIndexManagerFactory::SP factory(_libraries.create(libName));
    if (factory.get() != NULL) {
        LOG(info, "Successfully created index manager factory from library '%s'", libName.c_str());
        _indexManagerFactoryRegistry.add(libName, factory);
    } else {
        LOG(error, "Failed creating index manager factory from library '%s'", libName.c_str());
    }
}

searchcorespi::IIndexManagerFactory::SP
Proton::getIndexManagerFactory(const vespalib::stringref & name) const
{
    return _indexManagerFactoryRegistry.get(name);
}

BootstrapConfig::SP
Proton::getActiveConfigSnapshot() const
{
    BootstrapConfig::SP result;
    {
        LockGuard guard(_configLock);
        result = _activeConfigSnapshot;
    }
    return result;
}

storage::spi::PersistenceProvider::UP
Proton::create() const
{
    //TODO : Might be an idea to grab a lock here as this is not
    //controlled by you.  Must lock with add/remove documentdb or
    //reconfig or whatever.
    if (_persistenceEngine.get() == NULL)
        return storage::spi::PersistenceProvider::UP();
    return storage::spi::PersistenceProvider::
        UP(new PersistenceProviderProxy(*_persistenceEngine));
}

void
Proton::reconfigure(const BootstrapConfig::SP & config)
{
    _pendingConfigSnapshot.set(config);
    {
        LockGuard guard(_configLock);
        if (_activeConfigSnapshot.get() == NULL)
            return;
        if (!_allowReconfig)
            return;
        _executor.execute(
                vespalib::makeTask(
                        vespalib::makeClosure(
                                this,
                                &proton::Proton::performReconfig)));
    }
}

void
Proton::performReconfig()
{
    // Called by executor thread
    BootstrapConfig::SP configSnapshot = _pendingConfigSnapshot.get();
    bool generationChanged = false;
    bool snapChanged = false;
    {
        LockGuard guard(_configLock);
        if (_activeConfigSnapshotGeneration != configSnapshot->getGeneration())
            generationChanged = true;
        if (_activeConfigSnapshot.get() != configSnapshot.get()) {
            snapChanged = true;
        } else if (generationChanged) {
            _activeConfigSnapshotGeneration = configSnapshot->getGeneration();
        }
    }
    if (snapChanged) {
        applyConfig(configSnapshot, InitializeThreads());
    }
    _componentConfig.addConfig(vespalib::ComponentConfigProducer::Config("proton.documentdbs",
                                       _activeConfigSnapshotGeneration));
    if (_initialProtonConfig) {
        if (configSnapshot->getProtonConfig() == *_initialProtonConfig) {
            _componentConfig.addConfig(vespalib::ComponentConfigProducer::Config("proton",
                                       configSnapshot->getGeneration(),
                                       "config same as on startup"));
        } else {
            LOG(debug, "cannot apply proton.cfg generation %ld, differs from initial config",
                configSnapshot->getGeneration());
        }
    }
}

void
Proton::applyConfig(const BootstrapConfig::SP & configSnapshot,
                    InitializeThreads initializeThreads)
{
    // Called by executor thread during reconfig.
    const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
    setFS4Compression(protonConfig);

    _queryLimiter.configure(protonConfig.search.memory.limiter.maxthreads,
                            protonConfig.search.memory.limiter.mincoverage,
                            protonConfig.search.memory.limiter.minhits);
    typedef std::set<DocTypeName> DocTypeSet;
    DocTypeSet oldDocTypes;
    {
        RWLockReader guard(_lock);
        for (const auto &kv : _documentDBMap) {
            const DocTypeName &docTypeName = kv.first;
            oldDocTypes.insert(docTypeName);
        }
    }
    DocTypeSet newDocTypes;
    const DocumentTypeRepo::SP repo = configSnapshot->getDocumentTypeRepoSP();
    // XXX: This assumes no feeding during reconfig.  Otherwise queued messages
    // might incorrectly use freed document type repo.
    if (_persistenceProxy.get() != NULL) {
        _persistenceProxy->setRepo(*repo);
    }
    for (const auto &ddbConfig : protonConfig.documentdb) {
        DocTypeName docTypeName(ddbConfig.inputdoctypename);
        newDocTypes.insert(docTypeName);
        DocTypeSet::const_iterator found(oldDocTypes.find(docTypeName));
        if (found == oldDocTypes.end()) {
            addDocumentDB(docTypeName, ddbConfig.configid, configSnapshot,
                          initializeThreads);
        }
    }
    for (const auto &docType : oldDocTypes) {
        DocTypeSet::const_iterator found(newDocTypes.find(docType));
        if (found != newDocTypes.end())
            continue;
        // remove old document type
        DocTypeName docTypeName(docType.getName());
        removeDocumentDB(docTypeName);
    }
    {
        LockGuard guard(_configLock);
        _activeConfigSnapshot = configSnapshot;
        _activeConfigSnapshotGeneration = configSnapshot->getGeneration();
    }
    _componentConfig.addConfig(vespalib::ComponentConfigProducer::Config("proton.documentdbs",
                                       configSnapshot->getGeneration()));
    _diskMemUsageSampler->
        setConfig(diskMemUsageSamplerConfig(protonConfig));
    if (_memoryFlushConfigUpdater) {
        _memoryFlushConfigUpdater->setConfig(protonConfig.flush.memory);
        _flushEngine->kick();
    }
}

void
Proton::addDocumentDB(const DocTypeName & docTypeName,
                      const vespalib::string & configId,
                      const BootstrapConfig::SP & configSnapshot,
                      InitializeThreads initializeThreads)
{
    try {
        const DocumentTypeRepo::SP repo = configSnapshot->getDocumentTypeRepoSP();
        const document::DocumentType *docType = repo->getDocumentType(docTypeName.getName());
        if (docType != NULL) {
            LOG(info,
                "Add document database: "
                "doctypename(%s), configid(%s)",
                docTypeName.toString().c_str(),
                configId.c_str());
            addDocumentDB(*docType, configSnapshot, initializeThreads);
        } else {

            LOG(warning,
                "Did not find document type '%s' in the document manager. "
                "Skipping creating document database for this type",
                docTypeName.toString().c_str());
        }
    } catch (const document::DocumentTypeNotFoundException & e) {
        LOG(warning,
            "Did not find document type '%s' in the document manager. "
            "Skipping creating document database for this type",
            docTypeName.toString().c_str());
    }
}

bool Proton::addExtraConfigs(DocumentDBConfigManager & dbCfgMan)
{
    (void) dbCfgMan;
    return false;
}

Proton::~Proton()
{
    assert(_initStarted);
    if ( ! _initComplete ) {
        LOG(warning, "Initialization of proton was halted. Shutdown sequence has been initiated.");
    }
    _protonConfigurer.close();
    {
        LockGuard guard(_configLock);
        _allowReconfig = false;
    }
    _executor.sync();
    _customComponentRootToken.reset();
    _customComponentBindToken.reset();
    _stateServer.reset();
    if (_metricsEngine.get() != NULL) {
        _metricsEngine->removeMetricsHook(_metricsHook);
        _metricsEngine->stop();
    }
    if (_matchEngine.get() != NULL) {
        _matchEngine->close();
    }
    if (_summaryEngine.get() != NULL) {
        _summaryEngine->close();
    }
    if (_rpcHooks.get() != NULL) {
        _rpcHooks->close();
    }
    if (_memoryFlushConfigUpdater) {
        _diskMemUsageSampler->notifier().removeDiskMemUsageListener(_memoryFlushConfigUpdater.get());
    }
    _executor.shutdown();
    _executor.sync();
    _rpcHooks.reset();
    if (_flushEngine.get() != NULL) {
        _flushEngine->close();
    }
    if (_warmupExecutor.get() != NULL) {
        _warmupExecutor->sync();
    }
    if (_summaryExecutor.get() != NULL) {
        _summaryExecutor->sync();
    }
    LOG(debug, "Shutting down fs4 interface");
    if (_metricsEngine.get() != NULL) {
        _metricsEngine->removeExternalMetrics(_fs4Server->getMetrics());
    }
    if (_fs4Server.get() != NULL) {
        _fs4Server->shutDown();
    }
    _persistenceProxy.reset();
    while (!_documentDBMap.empty()) {
        const DocTypeName docTypeName(_documentDBMap.begin()->first);
        removeDocumentDB(docTypeName);
    }
    _documentDBMap.clear();
    _persistenceEngine.reset();
    _tls.reset();
    _warmupExecutor.reset();
    _summaryExecutor.reset();
    _clock.stop();
    LOG(debug, "Explicit destructor done");
}

size_t Proton::getNumDocs() const
{
    size_t numDocs(0);
    RWLockReader guard(_lock);
    for (const auto &kv : _documentDBMap) {
        numDocs += kv.second->getNumDocs();
    }
    return numDocs;
}

size_t Proton::getNumActiveDocs() const
{
    size_t numDocs(0);
    RWLockReader guard(_lock);
    for (const auto &kv : _documentDBMap) {
        numDocs += kv.second->getNumActiveDocs();
    }
    return numDocs;
}


vespalib::string
Proton::getBadConfigs(void) const
{
    std::ostringstream res;
    bool first = true;
    RWLockReader guard(_lock);
    for (const auto &kv : _documentDBMap) {
        if (kv.second->getRejectedConfig()) {
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
    RWLockReader guard(_lock);
    reports.push_back(StatusReport::SP(_matchEngine->
                                       reportStatus().release()));
    for (const auto &kv : _documentDBMap) {
        reports.push_back(StatusReport::SP(kv.second->
                                  reportStatus().release()));
    }
    return reports;
}


DocumentDB::SP
Proton::getDocumentDB(const document::DocumentType &docType)
{
    RWLockReader guard(_lock);
    DocTypeName docTypeName(docType.getName());
    DocumentDBMap::iterator it = _documentDBMap.find(docTypeName);
    if (it != _documentDBMap.end()) {
        return it->second;
    }
    return DocumentDB::SP();
}

DocumentDB::SP
Proton::addDocumentDB(const document::DocumentType &docType,
                      const BootstrapConfig::SP &configSnapshot,
                      InitializeThreads initializeThreads)
{
    const ProtonConfig &config(*configSnapshot->getProtonConfigSP());

    RWLockWriter guard(_lock);
    DocTypeName docTypeName(docType.getName());
    DocumentDBMap::iterator it = _documentDBMap.find(docTypeName);
    if (it != _documentDBMap.end()) {
        return it->second;
    }

    DocumentDBConfig::SP dbConfig =
        _protonConfigurer.getDocumentDBConfig(docTypeName);
    vespalib::string db_dir = config.basedir + "/documents/" + docTypeName.toString();
    vespalib::mkdir(db_dir, false); // Assume parent is created.
    ConfigStore::UP config_store(
            new FileConfigManager(db_dir + "/config",
                                  dbConfig->getConfigId(),
                                  docTypeName.getName()));
    config_store->setProtonConfig(configSnapshot->getProtonConfigSP());
    if (!initializeThreads) {
        // If configured value for initialize threads was 0, or we
        // are performing a reconfig after startup has completed, then use
        // 1 thread per document type.
        initializeThreads = std::make_shared<vespalib::ThreadStackExecutor>
                            (1, 128 * 1024);
    }
    DocumentDB::SP ret(new DocumentDB(config.basedir + "/documents",
                                      dbConfig,
                                      config.tlsspec,
                                      _queryLimiter,
                                      _clock,
                                      docTypeName,
                                      config,
                                      *this,
                                      *_warmupExecutor,
                                      *_summaryExecutor,
                                      _tls->getTransLogServer().get(),
                                      *_metricsEngine,
                                      _fileHeaderContext,
                                      std::move(config_store),
                                      initializeThreads,
                                      _hwInfo));
    _protonConfigurer.registerDocumentDB(docTypeName, ret.get());
    try {
        ret->start();
    } catch (vespalib::Exception &e) {
        LOG(warning,
            "Failed to start database for document type '%s'; %s",
            docTypeName.toString().c_str(),
            e.what());
        return DocumentDB::SP();
    }
    // Wait for replay done on document dbs added due to reconfigs, since engines are already up and running.
    // Also wait for document db reaching online state if initializing in sequence.
    if (!_isInitializing || _initDocumentDbsInSequence) {
        ret->waitForOnlineState();
    }
    _metricsEngine->addDocumentDBMetrics(ret->getMetricsCollection());
    _metricsEngine->addMetricsHook(ret->getMetricsUpdateHook());
    _documentDBMap[docTypeName] = ret;
    if (_persistenceEngine.get() != NULL) {
        // Not allowed to get to service layer to call pause().
        RWLockWriter persistenceWGuard(_persistenceEngine->getWLock());
        PersistenceHandlerProxy::SP
            persistenceHandler(new PersistenceHandlerProxy(ret));
        if (!_isInitializing) {
            _persistenceEngine->
                propagateSavedClusterState(*persistenceHandler);
            _persistenceEngine->populateInitialBucketDB(*persistenceHandler);
        }
        // TODO: Fix race with new cluster state setting.
        _persistenceEngine->putHandler(docTypeName, persistenceHandler);
    }
    SearchHandlerProxy::SP searchHandler(new SearchHandlerProxy(ret));
    _summaryEngine->putSearchHandler(docTypeName, searchHandler);
    _matchEngine->putSearchHandler(docTypeName, searchHandler);
    FlushHandlerProxy::SP flushHandler(new FlushHandlerProxy(ret));
    _flushEngine->putFlushHandler(docTypeName, flushHandler);
    _diskMemUsageSampler->notifier().addDiskMemUsageListener(ret->diskMemUsageListener());
    return ret;
}


void
Proton::removeDocumentDB(const DocTypeName &docTypeName)
{
    DocumentDB::SP old;
    _protonConfigurer.unregisterDocumentDB(docTypeName);
    {
        RWLockWriter guard(_lock);
        DocumentDBMap::iterator it = _documentDBMap.find(docTypeName);
        if (it == _documentDBMap.end())
            return;
        old = it->second;
        _documentDBMap.erase(it);
    }

    // Remove all entries into document db
    if (_persistenceEngine) {
        {
            // Not allowed to get to service layer to call pause().
            RWLockWriter persistenceWguard(_persistenceEngine->getWLock());
            IPersistenceHandler::SP oldHandler;
            oldHandler = _persistenceEngine->removeHandler(docTypeName);
            if (_initComplete && oldHandler) {
                // TODO: Fix race with bucket db modifying ops.
                _persistenceEngine->grabExtraModifiedBuckets(*oldHandler);
            }
        }
        _persistenceEngine->destroyIterators();
    }
    _matchEngine->removeSearchHandler(docTypeName);
    _summaryEngine->removeSearchHandler(docTypeName);
    _flushEngine->removeFlushHandler(docTypeName);
    _metricsEngine->removeMetricsHook(old->getMetricsUpdateHook());
    _metricsEngine->removeDocumentDBMetrics(old->getMetricsCollection());
    _diskMemUsageSampler->notifier().removeDiskMemUsageListener(old->diskMemUsageListener());
    // Caller should have removed & drained relevant timer tasks
    old->close();
}


Proton::MonitorReply::UP
Proton::ping(MonitorRequest::UP request, MonitorClient & client)
{
    (void) client;
    MonitorReply::UP reply(new MonitorReply());
    MonitorReply &ret = *reply;

    BootstrapConfig::SP configSnapshot = getActiveConfigSnapshot();
    const ProtonConfig &protonConfig = configSnapshot->getProtonConfig();
    ret.partid = protonConfig.partition;
    if (_matchEngine->isOnline())
        ret.timestamp = 42; // change to flush caches on tld/qrs
    else
        ret.timestamp = 0;
    ret.activeDocs = getNumActiveDocs();
    ret.activeDocsRequested = request->reportActiveDocs;
    return reply;
}

bool
Proton::triggerFlush()
{
    if ((_flushEngine.get() == NULL) || ! _flushEngine->HasThread()) {
        return false;
    }
    _flushEngine->triggerFlush();
    return true;
}

namespace {

PrepareRestartFlushStrategy::Config
createPrepareRestartConfig(const ProtonConfig &protonConfig)
{
    return PrepareRestartFlushStrategy::Config(protonConfig.flush.preparerestart.replaycost,
            protonConfig.flush.preparerestart.writecost);
}

}

bool
Proton::prepareRestart()
{
    if ((_flushEngine.get() == NULL) || ! _flushEngine->HasThread()) {
        return false;
    }
    BootstrapConfig::SP configSnapshot = getActiveConfigSnapshot();
    IFlushStrategy::SP strategy =
            std::make_shared<PrepareRestartFlushStrategy>(
                    createPrepareRestartConfig(configSnapshot->getProtonConfig()));
    _flushEngine->setStrategy(strategy);
    return true;
}

void
Proton::wipeHistory()
{
    DocumentDBMap dbs;
    {
        RWLockReader guard(_lock);
        dbs = _documentDBMap;
    }
    for (const auto &kv : dbs) {
        kv.second->wipeHistory();
    }
}


void
Proton::listDocTypes(std::vector<vespalib::string> &documentTypes)
{
    DocumentDBMap dbs;
    {
        RWLockReader guard(_lock);
        dbs = _documentDBMap;
    }
    for (const auto &kv : dbs) {
        vespalib::string documentType;
        const DocTypeName &docTypeName =
            kv.second->getDocTypeName();
        documentTypes.push_back(docTypeName.getName());
    }
}


void
Proton::listSchema(const vespalib::string &documentType,
                   std::vector<vespalib::string> &fieldNames,
                   std::vector<vespalib::string> &fieldDataTypes,
                   std::vector<vespalib::string> &fieldCollectionTypes,
                   std::vector<vespalib::string> &fieldLocations)
{
    DocumentDB::SP ddb;
    DocTypeName docTypeName(documentType);
    {
        RWLockReader guard(_lock);
        DocumentDBMap::const_iterator it = _documentDBMap.find(docTypeName);
        if (it != _documentDBMap.end())
            ddb = it->second;
    }
    if (ddb.get() == NULL)
        return;
    ddb->listSchema(fieldNames, fieldDataTypes, fieldCollectionTypes,
                    fieldLocations);
}


namespace {

int countOpenFiles()
{
    static const char * const fd_dir_name = "/proc/self/fd";
    int count = 0;
    DIR *dp = opendir(fd_dir_name);
    if (dp != NULL) {
        struct dirent entry;
        struct dirent *ptr = &entry;
        while (readdir_r(dp, &entry, &ptr) == 0 && ptr != NULL) {
            if (strcmp(".", entry.d_name) == 0) continue;
            if (strcmp("..", entry.d_name) == 0) continue;
            ++count;
        }
        closedir(dp);
    } else {
        LOG(warning, "could not scan directory %s: %s", fd_dir_name, strerror(errno));
    }
    return count;
}

} // namespace <unnamed>

void
Proton::updateMetrics(const vespalib::MonitorGuard &)
{
    {
        ContentProtonMetrics &metrics = _metricsEngine->root();
        metrics.transactionLog.update(_tls->getTransLogServer()->getDomainStats());
        const DiskMemUsageFilter &usageFilter = _diskMemUsageSampler->writeFilter();
        metrics.resourceUsage.disk.set(usageFilter.getDiskUsedRatio());
        metrics.resourceUsage.memory.set(usageFilter.getMemoryUsedRatio());
        metrics.resourceUsage.memoryMappings.set(usageFilter.getMemoryStats().getMappingsCount());
        metrics.resourceUsage.openFileDescriptors.set(countOpenFiles());
        metrics.resourceUsage.feedingBlocked.set((usageFilter.acceptWriteOperation() ? 0.0 : 1.0));
    }
    {
        LegacyProtonMetrics &metrics = _metricsEngine->legacyRoot();
        metrics.executor.update(_executor.getStats());
        metrics.flushExecutor.update(_flushEngine->getExecutorStats());
        metrics.matchExecutor.update(_matchEngine->getExecutorStats());
        metrics.summaryExecutor.update(_summaryEngine->getExecutorStats());
    }
}

namespace {
const std::string config_id_tag = "CONFIG ID";
}  // namespace

void
Proton::waitForInitDone()
{
    RWLockReader guard(_lock);
    for (const auto &kv : _documentDBMap) {
        kv.second->waitForInitDone();
    }
}

void
Proton::waitForOnlineState()
{
    RWLockReader guard(_lock);
    for (const auto &kv : _documentDBMap) {
        kv.second->waitForOnlineState();
    }
}

void
Proton::getComponentConfig(Consumer &consumer)
{
    _componentConfig.getComponentConfig(consumer);
    std::vector<DocumentDB::SP> dbs;
    {
        RWLockReader guard(_lock);
        for (const auto &kv : _documentDBMap) {
            dbs.push_back(kv.second);
        }
    }
    for (const auto &docDb : dbs) {
        vespalib::string name("proton.documentdb.");
        name.append(docDb->getDocTypeName().getName());
        int64_t gen = docDb->getActiveGeneration();
        if (docDb->getRejectedConfig()) {
            consumer.add(Config(name, gen, "has rejected config"));
        } else {
            consumer.add(Config(name, gen));
        }
    }
}

int64_t
Proton::getConfigGeneration(void)
{
    int64_t g = 0;
    std::vector<DocumentDB::SP> dbs;
    {
        LockGuard guard(_configLock);
        g = _activeConfigSnapshot->getGeneration();
    }
    {
        RWLockReader guard(_lock);
        for (const auto &kv : _documentDBMap) {
            dbs.push_back(kv.second);
        }
    }
    for (const auto &docDb : dbs) {
        int64_t ddbActiveGen = docDb->getActiveGeneration();
        g = std::min(g, ddbActiveGen);
    }
    return g;
}


void
Proton::setClusterState(const storage::spi::ClusterState &calc)
{
    // forward info sent by cluster controller to persistence engine
    // about whether node is supposed to be up or not.  Match engine
    // needs to know this in order to stop serving queries.
    bool nodeUp(calc.nodeUp());
    _matchEngine->setNodeUp(nodeUp);
}

namespace {

const vespalib::string MATCH_ENGINE = "matchengine";
const vespalib::string DOCUMENT_DB = "documentdb";
const vespalib::string FLUSH_ENGINE = "flushengine";
const vespalib::string TLS_NAME = "tls";
const vespalib::string RESOURCE_USAGE = "resourceusage";

struct StateExplorerProxy : vespalib::StateExplorer {
    const StateExplorer &explorer;
    explicit StateExplorerProxy(const StateExplorer &explorer_in) : explorer(explorer_in) {}
    virtual void get_state(const vespalib::slime::Inserter &inserter, bool full) const override { explorer.get_state(inserter, full); }
    virtual std::vector<vespalib::string> get_children_names() const override { return explorer.get_children_names(); }
    virtual std::unique_ptr<vespalib::StateExplorer> get_child(vespalib::stringref name) const override { return explorer.get_child(name); }
};

struct DocumentDBMapExplorer : vespalib::StateExplorer {
    typedef std::map<DocTypeName, DocumentDB::SP> DocumentDBMap;
    const DocumentDBMap &documentDBMap;
    vespalib::RWLock &lock;
    DocumentDBMapExplorer(const DocumentDBMap &documentDBMap_in, vespalib::RWLock &lock_in)
        : documentDBMap(documentDBMap_in), lock(lock_in) {}
    virtual void get_state(const vespalib::slime::Inserter &, bool) const override {}
    virtual std::vector<vespalib::string> get_children_names() const override {
        RWLockReader guard(lock);
        std::vector<vespalib::string> names;
        for (const auto &item: documentDBMap) {
            names.push_back(item.first.getName());
        }
        return names;
    }
    virtual std::unique_ptr<vespalib::StateExplorer> get_child(vespalib::stringref name) const override {
        typedef std::unique_ptr<StateExplorer> Explorer_UP;
        RWLockReader guard(lock);
        auto result = documentDBMap.find(DocTypeName(vespalib::string(name)));
        if (result == documentDBMap.end()) {
            return Explorer_UP(nullptr);
        }
        return Explorer_UP(new DocumentDBExplorer(result->second));
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
    std::vector<vespalib::string> names({DOCUMENT_DB, MATCH_ENGINE, FLUSH_ENGINE, TLS_NAME, RESOURCE_USAGE});
    return names;
}

std::unique_ptr<vespalib::StateExplorer>
Proton::get_child(vespalib::stringref name) const
{
    typedef std::unique_ptr<StateExplorer> Explorer_UP;
    if (name == MATCH_ENGINE && _matchEngine) {
        return std::make_unique<StateExplorerProxy>(*_matchEngine);
    } else if (name == DOCUMENT_DB) {
        return std::make_unique<DocumentDBMapExplorer>(_documentDBMap, _lock);
    } else if (name == FLUSH_ENGINE && _flushEngine) {
        return std::make_unique<FlushEngineExplorer>(*_flushEngine);
    } else if (name == TLS_NAME && _tls) {
        return std::make_unique<search::transactionlog::TransLogServerExplorer>(_tls->getTransLogServer());
    } else if (name == RESOURCE_USAGE && _diskMemUsageSampler) {
        return std::make_unique<ResourceUsageExplorer>(_diskMemUsageSampler->writeFilter());
    }
    return Explorer_UP(nullptr);
}

std::shared_ptr<IDocumentDBReferentRegistry>
Proton::getDocumentDBReferentRegistry() const
{
    return _documentDBReferentRegistry;
}


} // namespace proton
