// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/searchcore/proton/test/dummydbowner.h>
#include <vespa/searchcore/proton/common/alloc_config.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/dummy_wire_service.h>
#include <vespa/searchcore/proton/persistenceengine/ipersistenceengineowner.h>
#include <vespa/searchcore/proton/persistenceengine/persistenceengine.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/document_db_maintenance_config.h>
#include <vespa/searchcore/proton/server/documentdb.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/fileconfigmanager.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/server/persistencehandlerproxy.h>
#include <vespa/searchcore/proton/server/threading_service_config.h>
#include <vespa/searchcore/proton/test/disk_mem_usage_notifier.h>
#include <vespa/searchcore/proton/test/mock_shared_threading_service.h>
#include <vespa/searchcore/proton/test/port_numbers.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/config-attributes.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/document/config/documenttypes_config_fwd.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/stringfmt.h>
#include <cstdlib>
#include <filesystem>

#include <vespa/log/log.h>
LOG_SETUP("persistenceconformance_test");

using namespace config;
using namespace proton;
using namespace cloud::config::filedistribution;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using namespace std::chrono_literals;
using vespa::config::content::core::BucketspacesConfig;

using std::shared_ptr;
using document::BucketSpace;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::TestDocMan;
using document::test::makeBucketSpace;
using search::TuneFileDocumentDB;
using search::index::DummyFileHeaderContext;
using search::index::Schema;
using search::transactionlog::TransLogServer;
using storage::spi::ConformanceTest;
using storage::spi::PersistenceProvider;
using vespalib::HwInfo;

using PersistenceFactory = ConformanceTest::PersistenceFactory;
using DocumenttypesConfigSP = DocumentDBConfig::DocumenttypesConfigSP;
using DocumentDBMap = std::map<DocTypeName, DocumentDB::SP>;
using DocTypeVector = std::vector<DocTypeName>;

namespace {

constexpr int tls_port_base = proton::test::port_numbers::persistenceconformance_tls_port_base;
constexpr int tls_port_max_bias = proton::test::port_numbers::persistenceconformance_tls_port_max_bias;

int shard_index = 0;

void
calc_shard_index()
{
    const auto* shard = std::getenv("GTEST_SHARD_INDEX");
    if (shard != nullptr) {
        shard_index = std::atoi(shard);
        assert(shard_index >= 0 && shard_index <= tls_port_max_bias);
    }
}

}

void
storeDocType(DocTypeVector *types, const DocumentType &type)
{
    types->push_back(DocTypeName(type.getName()));
}


struct SchemaConfigFactory {
    using CS = DocumentDBConfig;
    using SP = std::shared_ptr<SchemaConfigFactory>;
    virtual ~SchemaConfigFactory() = default;
    static SchemaConfigFactory::SP get() { return std::make_shared<SchemaConfigFactory>(); }
    virtual CS::IndexschemaConfigSP createIndexSchema(const DocumentType &docType) {
        (void) docType;
        return std::make_shared<IndexschemaConfig>();
    }
    virtual CS::AttributesConfigSP createAttributes(const DocumentType &docType) {
        (void) docType;
        return std::make_shared<AttributesConfig>();
    }
    virtual CS::SummaryConfigSP createSummary(const DocumentType &docType) {
        (void) docType;
        return std::make_shared<SummaryConfig>();
    }
};

class ConfigFactory {
private:
    std::shared_ptr<const DocumentTypeRepo>    _repo;
    DocumenttypesConfigSP   _typeCfg;
    SchemaConfigFactory::SP _schemaFactory;

public:
    ConfigFactory(std::shared_ptr<const DocumentTypeRepo> repo,
                  DocumenttypesConfigSP typeCfg,
                  SchemaConfigFactory::SP schemaFactory);
    ~ConfigFactory();
    std::shared_ptr<const DocumentTypeRepo> getTypeRepo() const { return _repo; }
    DocumenttypesConfigSP getTypeCfg() const { return _typeCfg; }
    DocTypeVector getDocTypes() const {
        DocTypeVector types;
        _repo->forEachDocumentType([&types](const DocumentType &type) {
            types.push_back(DocTypeName(type.getName()));
        });
        return types;
    }
    DocumentDBConfig::SP create(const DocTypeName &docTypeName) const {
        const DocumentType *docType = _repo->getDocumentType(docTypeName.getName());
        if (docType == nullptr) {
            return DocumentDBConfig::SP();
        }
        using CS = DocumentDBConfig;
        CS::IndexschemaConfigSP indexschema = _schemaFactory->createIndexSchema(*docType);
        CS::AttributesConfigSP attributes = _schemaFactory->createAttributes(*docType);
        CS::SummaryConfigSP summary = _schemaFactory->createSummary(*docType);
        auto schema = DocumentDBConfig::build_schema(*attributes, *indexschema);
        return std::make_shared<DocumentDBConfig>(
                        1,
                        std::make_shared<RankProfilesConfig>(),
                        std::make_shared<search::fef::RankingConstants>(),
                        std::make_shared<search::fef::RankingExpressions>(),
                        std::make_shared<search::fef::OnnxModels>(),
                        indexschema,
                        attributes,
                        summary,
                        std::make_shared<JuniperrcConfig>(),
                        _typeCfg,
                        _repo,
                        std::make_shared<ImportedFieldsConfig>(),
                        std::make_shared<TuneFileDocumentDB>(),
                        schema,
                        std::make_shared<DocumentDBMaintenanceConfig>(),
                        search::LogDocumentStore::Config(),
                        ThreadingServiceConfig::make(),
                        AllocConfig::makeDefault(),
                        "client",
                        docTypeName.getName());
    }
};


ConfigFactory::ConfigFactory(std::shared_ptr<const DocumentTypeRepo> repo, DocumenttypesConfigSP typeCfg,
                             SchemaConfigFactory::SP schemaFactory)
    : _repo(std::move(repo)),
      _typeCfg(std::move(typeCfg)),
      _schemaFactory(std::move(schemaFactory))
{}
ConfigFactory::~ConfigFactory() = default;

class DocumentDBFactory : public DummyDBOwner {
private:
    std::string          _baseDir;
    DummyFileHeaderContext    _fileHeaderContext;
    std::string          _tlsSpec;
    matching::QueryLimiter    _queryLimiter;
    mutable DummyWireService      _metricsWireService;
    mutable MemoryConfigStores    _config_stores;
    vespalib::ThreadStackExecutor _summaryExecutor;
    MockSharedThreadingService    _shared_service;
    TransLogServer                _tls;

    static std::shared_ptr<ProtonConfig> make_proton_config() {
        ProtonConfigBuilder proton_config;
        proton_config.indexing.optimize = ProtonConfigBuilder::Indexing::Optimize::LATENCY;
        return std::make_shared<ProtonConfig>(proton_config);
    }

public:
    DocumentDBFactory(const std::string &baseDir, int tlsListenPort);
    ~DocumentDBFactory() override;
    DocumentDB::SP create(BucketSpace bucketSpace,
                          const DocTypeName &docType,
                          const ConfigFactory &factory) {
        DocumentDBConfig::SP snapshot = factory.create(docType);
        std::filesystem::create_directory(std::filesystem::path(_baseDir));
        std::filesystem::create_directory(std::filesystem::path(_baseDir + "/" + docType.toString()));
        std::string inputCfg = _baseDir + "/" + docType.toString() + "/baseconfig";
        {
            FileConfigManager fileCfg(_shared_service.transport(), inputCfg, "", docType.getName());
            fileCfg.saveConfig(*snapshot, 1);
        }
        config::DirSpec spec(inputCfg + "/config-1");
        auto tuneFileDocDB = std::make_shared<TuneFileDocumentDB>();
        DocumentDBConfigHelper mgr(spec, docType.getName());
        auto b = std::make_shared<BootstrapConfig>(1, factory.getTypeCfg(), factory.getTypeRepo(),
                                                  make_proton_config(),
                                                  std::make_shared<FiledistributorrpcConfig>(),
                                                  std::make_shared<BucketspacesConfig>(),
                                                  tuneFileDocDB, HwInfo());
        mgr.forwardConfig(b);
        mgr.nextGeneration(_shared_service.transport(), 0ms);
        return DocumentDB::create(_baseDir, mgr.getConfig(), _tlsSpec, _queryLimiter, docType, bucketSpace,
                                  *b->getProtonConfigSP(), const_cast<DocumentDBFactory &>(*this),
                                  _shared_service, _tls, _metricsWireService,
                                  _fileHeaderContext, std::make_shared<search::attribute::Interlock>(),
                                  _config_stores.getConfigStore(docType.toString()),
                                  std::make_shared<vespalib::ThreadStackExecutor>(16), HwInfo(),
                                  std::shared_ptr<search::diskindex::IPostingListCache>());
    }
};


DocumentDBFactory::DocumentDBFactory(const std::string &baseDir, int tlsListenPort)
    : _baseDir(baseDir),
      _fileHeaderContext(),
      _tlsSpec(vespalib::make_string("tcp/localhost:%d", tlsListenPort)),
      _queryLimiter(),
      _metricsWireService(),
      _summaryExecutor(8),
      _shared_service(_summaryExecutor),
      _tls(_shared_service.transport(), "tls", tlsListenPort, baseDir, _fileHeaderContext)
{}
DocumentDBFactory::~DocumentDBFactory()  = default;

class DocumentDBRepo {
private:
    DocumentDBMap _docDbs;
public:
    using UP = std::unique_ptr<DocumentDBRepo>;
    DocumentDBRepo(const ConfigFactory &cfgFactory, DocumentDBFactory &docDbFactory)
        : _docDbs()
    {
        DocTypeVector types = cfgFactory.getDocTypes();
        for (const auto & type : types) {
            BucketSpace bucketSpace(makeBucketSpace(type.getName()));
            DocumentDB::SP docDb = docDbFactory.create(bucketSpace, type, cfgFactory);
            docDb->start();
            docDb->waitForOnlineState();
            _docDbs[type] = docDb;
        }
    }

    void close() {
        for (auto & dbEntry : _docDbs) {
            dbEntry.second->close();
        }
    }

    ~DocumentDBRepo() {
        close();
    }
    const DocumentDBMap &getDocDbs() const { return _docDbs; }
};


class DocDBRepoHolder
{
protected:
    DocumentDBRepo::UP      _docDbRepo;

    explicit DocDBRepoHolder(DocumentDBRepo::UP docDbRepo)
        : _docDbRepo(std::move(docDbRepo))
    {
    }

    virtual ~DocDBRepoHolder() = default;

    void close() {
        if (_docDbRepo)
            _docDbRepo->close();
    }
};


class MyPersistenceEngineOwner : public IPersistenceEngineOwner
{
    void setClusterState(BucketSpace, const storage::spi::ClusterState &) override { }
};

struct MyResourceWriteFilter : public IResourceWriteFilter
{
    bool acceptWriteOperation() const override { return true; }
    State getAcceptState() const override { return IResourceWriteFilter::State(); }
};

class MyPersistenceEngine : public DocDBRepoHolder,
                            public PersistenceEngine
{
public:
    MyPersistenceEngine(MyPersistenceEngineOwner &owner,
                        MyResourceWriteFilter &writeFilter,
                        IDiskMemUsageNotifier& disk_mem_usage_notifier,
                        DocumentDBRepo::UP docDbRepo,
                        const std::string &docType = "")
        : DocDBRepoHolder(std::move(docDbRepo)),
          PersistenceEngine(owner, writeFilter, disk_mem_usage_notifier, -1, false)
    {
        addHandlers(docType);
    }

    void
    addHandlers(const std::string &docType)
    {
        if (!_docDbRepo)
            return;
        const DocumentDBMap &docDbs = _docDbRepo->getDocDbs();
        for (const auto & dbEntry : docDbs) {
            if (!docType.empty() && docType != dbEntry.first.getName()) {
                continue;
            }
            LOG(info, "putHandler(%s)", dbEntry.first.toString().c_str());
            auto proxy = std::make_shared<PersistenceHandlerProxy>(dbEntry.second);
            putHandler(getWLock(), dbEntry.second->getBucketSpace(), dbEntry.first, proxy);
        }
    }

    void
    removeHandlers()
    {
        if ( ! _docDbRepo)
            return;
        const DocumentDBMap &docDbs = _docDbRepo->getDocDbs();
        for (const auto & dbEntry : docDbs) {
            IPersistenceHandler::SP proxy(removeHandler(getWLock(), dbEntry.second->getBucketSpace(), dbEntry.first));
            (void) proxy;
        }
    }

    ~MyPersistenceEngine() override
    {
        destroyIterators();
        removeHandlers(); // Block calls to document db from engine
        close();      // Block upcalls to engine from document db
    }
};

class MyPersistenceFactory : public PersistenceFactory {
private:
    std::string          _baseDir;
    DocumentDBFactory       _docDbFactory;
    SchemaConfigFactory::SP _schemaFactory;
    DocumentDBRepo::UP      _docDbRepo;
    std::string        _docType;
    MyPersistenceEngineOwner _engineOwner;
    MyResourceWriteFilter    _writeFilter;
    test::DiskMemUsageNotifier   _disk_mem_usage_notifier;
public:
    MyPersistenceFactory(const std::string &baseDir, int tlsListenPort,
                         SchemaConfigFactory::SP schemaFactory,
                         const std::string & docType = "")
        : _baseDir(baseDir),
          _docDbFactory(baseDir, tlsListenPort),
          _schemaFactory(std::move(schemaFactory)),
          _docDbRepo(),
          _docType(docType),
          _engineOwner(),
          _writeFilter(),
          _disk_mem_usage_notifier(DiskMemUsageState({ 0.8, 0.5 }, { 0.8, 0.4 }))
    {
        clear();
    }
    ~MyPersistenceFactory() override {
        clear();
    }
    std::unique_ptr<PersistenceProvider> getPersistenceImplementation(const std::shared_ptr<const DocumentTypeRepo> &repo,
                                                         const DocumenttypesConfig &typesCfg) override {
        ConfigFactory cfgFactory(repo, std::make_shared<DocumenttypesConfig>(typesCfg), _schemaFactory);
        _docDbRepo = std::make_unique<DocumentDBRepo>(cfgFactory, _docDbFactory);
        auto engine = std::make_unique<MyPersistenceEngine>(_engineOwner,_writeFilter, _disk_mem_usage_notifier, std::move(_docDbRepo), _docType);
        assert( ! _docDbRepo); // Repo should be handed over
        return engine;
    }

    void clear() override {
        std::filesystem::remove_all(std::filesystem::path(_baseDir));
    }

    bool hasPersistence() const override { return true; }
    bool supportsActiveState() const override { return true; }
    bool supportsBucketSpaces() const override { return true; }
};


std::unique_ptr<PersistenceFactory>
makeMyPersistenceFactory(const std::string &docType)
{
    auto base_dir = vespalib::make_string("testdb%03d", shard_index);
    return std::make_unique<MyPersistenceFactory>(base_dir, tls_port_base + shard_index, SchemaConfigFactory::get(), docType);
}

int
main(int argc, char* argv[])
{
    calc_shard_index();
    ::testing::InitGoogleTest(&argc, argv);
    DummyFileHeaderContext::setCreator("persistenceconformance_test");
    ConformanceTest::_factoryFactory = &makeMyPersistenceFactory;
    return RUN_ALL_TESTS();
}
