// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>

#include <tests/proton/common/dummydbowner.h>
#include <vespa/config-imported-fields.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summarymap.h>
#include <vespa/document/base/testdocman.h>
#include <vespa/fastos/file.h>
#include <vespa/persistence/conformancetest/conformancetest.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/searchcommon/common/schemaconfigurer.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include <vespa/searchcore/proton/persistenceengine/ipersistenceengineowner.h>
#include <vespa/searchcore/proton/persistenceengine/persistenceengine.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/document_db_maintenance_config.h>
#include <vespa/searchcore/proton/server/documentdb.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/fileconfigmanager.h>
#include <vespa/searchcore/proton/server/memoryconfigstore.h>
#include <vespa/searchcore/proton/server/persistencehandlerproxy.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/translogserver.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/vespalib/io/fileutil.h>

#include <vespa/log/log.h>
LOG_SETUP("persistenceconformance_test");

using namespace config;
using namespace proton;
using namespace cloud::config::filedistribution;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using vespa::config::content::core::BucketspacesConfig;

using std::shared_ptr;
using document::BucketSpace;
using document::DocumentType;
using document::DocumentTypeRepo;
using document::DocumenttypesConfig;
using document::TestDocMan;
using document::test::makeBucketSpace;
using search::TuneFileDocumentDB;
using search::index::DummyFileHeaderContext;
using search::index::Schema;
using search::index::SchemaBuilder;
using search::transactionlog::TransLogServer;
using storage::spi::ConformanceTest;
using storage::spi::PersistenceProvider;

typedef ConformanceTest::PersistenceFactory         PersistenceFactory;
typedef DocumentDBConfig::DocumenttypesConfigSP     DocumenttypesConfigSP;
typedef std::map<DocTypeName, DocumentDB::SP>       DocumentDBMap;
typedef std::vector<DocTypeName>                    DocTypeVector;

void
storeDocType(DocTypeVector *types, const DocumentType &type)
{
    types->push_back(DocTypeName(type.getName()));
}


struct SchemaConfigFactory {
    typedef DocumentDBConfig CS;
    typedef std::shared_ptr<SchemaConfigFactory> SP;
    virtual ~SchemaConfigFactory() {}
    static SchemaConfigFactory::SP get() { return SchemaConfigFactory::SP(new SchemaConfigFactory()); }
    virtual CS::IndexschemaConfigSP createIndexSchema(const DocumentType &docType) {
        (void) docType;
        return CS::IndexschemaConfigSP(new IndexschemaConfig());
    }
    virtual CS::AttributesConfigSP createAttributes(const DocumentType &docType) {
        (void) docType;
        return CS::AttributesConfigSP(new AttributesConfig());
    }
    virtual CS::SummaryConfigSP createSummary(const DocumentType &docType) {
        (void) docType;
        return CS::SummaryConfigSP(new SummaryConfig());
    }
};

class ConfigFactory {
private:
    DocumentTypeRepo::SP    _repo;
    DocumenttypesConfigSP   _typeCfg;
    SchemaConfigFactory::SP _schemaFactory;

public:
    ConfigFactory(const DocumentTypeRepo::SP &repo,
                  const DocumenttypesConfigSP &typeCfg,
                  const SchemaConfigFactory::SP &schemaFactory);
    ~ConfigFactory();
    const DocumentTypeRepo::SP getTypeRepo() const { return _repo; }
    const DocumenttypesConfigSP getTypeCfg() const { return _typeCfg; }
    DocTypeVector getDocTypes() const {
        DocTypeVector types;
        _repo->forEachDocumentType(*makeClosure(storeDocType, &types));
        return types;
    }
    DocumentDBConfig::SP create(const DocTypeName &docTypeName) const {
        const DocumentType *docType =
            _repo->getDocumentType(docTypeName.getName());
        if (docType == NULL) {
            return DocumentDBConfig::SP();
        }
        typedef DocumentDBConfig CS;
        CS::IndexschemaConfigSP indexschema = _schemaFactory->createIndexSchema(*docType);
        CS::AttributesConfigSP attributes = _schemaFactory->createAttributes(*docType);
        CS::SummaryConfigSP summary = _schemaFactory->createSummary(*docType);
        Schema::SP schema(new Schema());
        SchemaBuilder::build(*indexschema, *schema);
        SchemaBuilder::build(*attributes, *schema);
        SchemaBuilder::build(*summary, *schema);
        return DocumentDBConfig::SP(new DocumentDBConfig(
                        1,
                        std::make_shared<RankProfilesConfig>(),
                        std::make_shared<matching::RankingConstants>(),
                        indexschema,
                        attributes,
                        summary,
                        std::make_shared<SummarymapConfig>(),
                        std::make_shared<JuniperrcConfig>(),
                        _typeCfg,
                        _repo,
                        std::make_shared<ImportedFieldsConfig>(),
                        std::make_shared<TuneFileDocumentDB>(),
                        schema,
                        std::make_shared<DocumentDBMaintenanceConfig>(),
                        search::LogDocumentStore::Config(),
                        "client",
                        docTypeName.getName()));
    }
};


ConfigFactory::ConfigFactory(const DocumentTypeRepo::SP &repo, const DocumenttypesConfigSP &typeCfg,
                             const SchemaConfigFactory::SP &schemaFactory)
    : _repo(repo),
      _typeCfg(typeCfg),
      _schemaFactory(schemaFactory)
{}
ConfigFactory::~ConfigFactory() {}

class DocumentDBFactory : public DummyDBOwner {
private:
    vespalib::string          _baseDir;
    DummyFileHeaderContext    _fileHeaderContext;
    TransLogServer            _tls;
    vespalib::string          _tlsSpec;
    matching::QueryLimiter    _queryLimiter;
    vespalib::Clock           _clock;
    mutable DummyWireService  _metricsWireService;
    mutable MemoryConfigStores _config_stores;
    vespalib::ThreadStackExecutor _summaryExecutor;

public:
    DocumentDBFactory(const vespalib::string &baseDir, int tlsListenPort);
    ~DocumentDBFactory();
    DocumentDB::SP create(BucketSpace bucketSpace,
                          const DocTypeName &docType,
                          const ConfigFactory &factory) {
        DocumentDBConfig::SP snapshot = factory.create(docType);
        vespalib::mkdir(_baseDir, false);
        vespalib::mkdir(_baseDir + "/" + docType.toString(), false);
        vespalib::string inputCfg = _baseDir + "/" + docType.toString() + "/baseconfig";
        {
            FileConfigManager fileCfg(inputCfg, "", docType.getName());
            fileCfg.saveConfig(*snapshot, 1);
        }
        config::DirSpec spec(inputCfg + "/config-1");
        TuneFileDocumentDB::SP tuneFileDocDB(new TuneFileDocumentDB());
        DocumentDBConfigHelper mgr(spec, docType.getName());
        BootstrapConfig::SP b(new BootstrapConfig(1,
                                                  factory.getTypeCfg(),
                                                  factory.getTypeRepo(),
                                                  std::make_shared<ProtonConfig>(),
                                                  std::make_shared<FiledistributorrpcConfig>(),
                                                  std::make_shared<BucketspacesConfig>(),
                                                  tuneFileDocDB, HwInfo()));
        mgr.forwardConfig(b);
        mgr.nextGeneration(0);
        return DocumentDB::SP(
                new DocumentDB(_baseDir,
                               mgr.getConfig(),
                               _tlsSpec,
                               _queryLimiter,
                               _clock,
                               docType,
                               bucketSpace,
                               *b->getProtonConfigSP(),
                               const_cast<DocumentDBFactory &>(*this),
                               _summaryExecutor,
                               _summaryExecutor,
                               _tls,
                               _metricsWireService,
                               _fileHeaderContext,
                               _config_stores.getConfigStore(docType.toString()),
                               std::make_shared<vespalib::ThreadStackExecutor>
                               (16, 128 * 1024),
                               HwInfo()));
    }
};


DocumentDBFactory::DocumentDBFactory(const vespalib::string &baseDir, int tlsListenPort)
    : _baseDir(baseDir),
      _fileHeaderContext(),
      _tls("tls", tlsListenPort, baseDir, _fileHeaderContext),
      _tlsSpec(vespalib::make_string("tcp/localhost:%d", tlsListenPort)),
      _queryLimiter(),
      _clock(),
      _metricsWireService(),
      _summaryExecutor(8, 128 * 1024)
{}
DocumentDBFactory::~DocumentDBFactory() {}

class DocumentDBRepo {
private:
    DocumentDBMap _docDbs;
public:
    typedef std::unique_ptr<DocumentDBRepo> UP;
    DocumentDBRepo(const ConfigFactory &cfgFactory,
                   DocumentDBFactory &docDbFactory) :
        _docDbs()
    {
        DocTypeVector types = cfgFactory.getDocTypes();
        for (size_t i = 0; i < types.size(); ++i) {
            BucketSpace bucketSpace(makeBucketSpace(types[i].getName()));
            DocumentDB::SP docDb = docDbFactory.create(bucketSpace,
                                                       types[i],
                                                       cfgFactory);
            docDb->start();
            docDb->waitForOnlineState();
            _docDbs[types[i]] = docDb;
        }
    }

    void
    close()
    {
        for (DocumentDBMap::iterator itr = _docDbs.begin();
             itr != _docDbs.end(); ++itr) {
            itr->second->close();
        }
    }

    ~DocumentDBRepo()
    {
        close();
    }
    const DocumentDBMap &getDocDbs() const { return _docDbs; }
};


class DocDBRepoHolder
{
protected:
    DocumentDBRepo::UP      _docDbRepo;

    DocDBRepoHolder(DocumentDBRepo::UP docDbRepo)
        : _docDbRepo(std::move(docDbRepo))
    {
    }

    virtual
    ~DocDBRepoHolder()
    {
    }

    void
    close()
    {
        if (_docDbRepo.get() != NULL)
            _docDbRepo->close();
    }
};


class MyPersistenceEngineOwner : public IPersistenceEngineOwner
{
    virtual void
    setClusterState(BucketSpace, const storage::spi::ClusterState &calc) override
    {
        (void) calc;
    }
};

struct MyResourceWriteFilter : public IResourceWriteFilter
{
    virtual bool acceptWriteOperation() const override { return true; }
    virtual State getAcceptState() const override { return IResourceWriteFilter::State(); }
};

class MyPersistenceEngine : public DocDBRepoHolder,
                            public PersistenceEngine
{
public:
    MyPersistenceEngine(MyPersistenceEngineOwner &owner,
                        MyResourceWriteFilter &writeFilter,
                        DocumentDBRepo::UP docDbRepo,
                        const vespalib::string &docType = "")
        : DocDBRepoHolder(std::move(docDbRepo)),
          PersistenceEngine(owner, writeFilter, -1, false)
    {
        addHandlers(docType);
    }

    void
    addHandlers(const vespalib::string &docType)
    {
        if (_docDbRepo.get() == NULL)
            return;
        const DocumentDBMap &docDbs = _docDbRepo->getDocDbs();
        for (DocumentDBMap::const_iterator itr = docDbs.begin();
             itr != docDbs.end(); ++itr) {
            if (!docType.empty() && docType != itr->first.getName()) {
                continue;
            }
            LOG(info, "putHandler(%s)", itr->first.toString().c_str());
            IPersistenceHandler::SP proxy(
                    new PersistenceHandlerProxy(itr->second));
            putHandler(itr->second->getBucketSpace(), itr->first, proxy);
        }
    }

    void
    removeHandlers()
    {
        if (_docDbRepo.get() == NULL)
            return;
        const DocumentDBMap &docDbs = _docDbRepo->getDocDbs();
        for (DocumentDBMap::const_iterator itr = docDbs.begin();
             itr != docDbs.end(); ++itr) {
            IPersistenceHandler::SP proxy(removeHandler(itr->second->getBucketSpace(), itr->first));
        }
    }

    virtual
    ~MyPersistenceEngine()
    {
        destroyIterators();
        removeHandlers(); // Block calls to document db from engine
        close();      // Block upcalls to engine from document db
    }
};

class MyPersistenceFactory : public PersistenceFactory {
private:
    vespalib::string          _baseDir;
    DocumentDBFactory       _docDbFactory;
    SchemaConfigFactory::SP _schemaFactory;
    DocumentDBRepo::UP      _docDbRepo;
    vespalib::string        _docType;
    MyPersistenceEngineOwner _engineOwner;
    MyResourceWriteFilter    _writeFilter;
public:
    MyPersistenceFactory(const vespalib::string &baseDir, int tlsListenPort,
                         const SchemaConfigFactory::SP &schemaFactory,
                         const vespalib::string &docType = "")
        : _baseDir(baseDir),
          _docDbFactory(baseDir, tlsListenPort),
          _schemaFactory(schemaFactory),
          _docDbRepo(),
          _docType(docType),
          _engineOwner(),
          _writeFilter()
    {
    }
    virtual PersistenceProvider::UP getPersistenceImplementation(const DocumentTypeRepo::SP &repo,
                                                                 const DocumenttypesConfig &typesCfg) override {
        ConfigFactory cfgFactory(repo, DocumenttypesConfigSP(new DocumenttypesConfig(typesCfg)), _schemaFactory);
        _docDbRepo.reset(new DocumentDBRepo(cfgFactory, _docDbFactory));
        PersistenceEngine::UP engine(new MyPersistenceEngine(_engineOwner,
                                                             _writeFilter,
                                                             std::move(_docDbRepo),
                                                             _docType));
        assert(_docDbRepo.get() == NULL); // Repo should be handed over
        return PersistenceProvider::UP(engine.release());
    }

    virtual void clear() override {
        FastOS_FileInterface::EmptyAndRemoveDirectory(_baseDir.c_str());
    }

    virtual bool hasPersistence() const override { return true; }
    virtual bool supportsActiveState() const override { return true; }
    virtual bool supportsBucketSpaces() const override { return true; }
};


struct FixtureBaseBase
{
    FixtureBaseBase()
    {
        FastOS_FileInterface::EmptyAndRemoveDirectory("testdb");
    }

    ~FixtureBaseBase()
    {
        FastOS_FileInterface::EmptyAndRemoveDirectory("testdb");
    }
};


struct FixtureBase : public FixtureBaseBase
{
    ConformanceTest test;
    FixtureBase(const SchemaConfigFactory::SP &schemaFactory,
                const vespalib::string &docType = "")
        : FixtureBaseBase(),
          test(PersistenceFactory::UP(new MyPersistenceFactory("testdb", 9017,
                                                               schemaFactory,
                                                               docType)))
    {
    }
    ~FixtureBase()
    {
    }
};


struct TestFixture : public FixtureBase {
    TestFixture() : FixtureBase(SchemaConfigFactory::get()) {}
};


struct SingleDocTypeFixture : public FixtureBase {
    SingleDocTypeFixture() : FixtureBase(SchemaConfigFactory::get(), "testdoctype1") {}
};

TEST_F("require that testBucketActivation() works", TestFixture)
{
    f.test.testBucketActivation();
}

TEST_F("require that testListBuckets() works", TestFixture)
{
    f.test.testListBuckets();
}

TEST_F("require that testBucketInfo() works", TestFixture)
{
    f.test.testBucketInfo();
}

TEST_F("require that testPut() works", TestFixture)
{
    f.test.testPut();
}

TEST_F("require that testRemove() works", TestFixture)
{
    f.test.testRemove();
}

TEST_F("require that testRemoveMerge() works", TestFixture)
{
    f.test.testRemoveMerge();
}

TEST_F("require that testUpdate() works", TestFixture)
{
    f.test.testUpdate();
}

TEST_F("require that testGet() works", TestFixture)
{
    f.test.testGet();
}

TEST_F("require that testIterateCreateIterator() works", TestFixture)
{
    f.test.testIterateCreateIterator();
}

TEST_F("require that testIterateWithUnknownId() works", TestFixture)
{
    f.test.testIterateWithUnknownId();
}

TEST_F("require that testIterateDestroyIterator() works", TestFixture)
{
    f.test.testIterateDestroyIterator();
}

TEST_F("require that testIterateAllDocs() works", TestFixture)
{
    f.test.testIterateAllDocs();
}

TEST_F("require that testIterateChunked() works", TestFixture)
{
    f.test.testIterateChunked();
}

TEST_F("require that testMaxByteSize() works", TestFixture)
{
    f.test.testMaxByteSize();
}

TEST_F("require that testIterateMatchTimestampRange() works", TestFixture)
{
    f.test.testIterateMatchTimestampRange();
}

TEST_F("require that testIterateExplicitTimestampSubset() works", TestFixture)
{
    f.test.testIterateExplicitTimestampSubset();
}

TEST_F("require that testIterateRemoves() works", TestFixture)
{
    f.test.testIterateRemoves();
}

TEST_F("require that testIterateMatchSelection() works", TestFixture)
{
    f.test.testIterateMatchSelection();
}

TEST_F(
  "require that testIterationRequiringDocumentIdOnlyMatching() works",
  TestFixture)
{
    f.test.testIterationRequiringDocumentIdOnlyMatching();
}

TEST_F("require that testIterateBadDocumentSelection() works", TestFixture)
{
    f.test.testIterateBadDocumentSelection();
}

TEST_F("require that testIterateAlreadyCompleted() works", TestFixture)
{
    f.test.testIterateAlreadyCompleted();
}

TEST_F("require that testIterateEmptyBucket() works", TestFixture)
{
    f.test.testIterateEmptyBucket();
}

TEST_F("require that testDeleteBucket() works", TestFixture)
{
    f.test.testDeleteBucket();
}

TEST_F("require that testSplitNormalCase() works", TestFixture)
{
    f.test.testSplitNormalCase();
}

TEST_F("require that testSplitTargetExists() works", TestFixture)
{
    f.test.testSplitTargetExists();
}

TEST_F("require that testJoinNormalCase() works", TestFixture)
{
    f.test.testJoinNormalCase();
}

TEST_F("require that testJoinOneBucket() works", TestFixture)
{
    f.test.testJoinOneBucket();
}

TEST_F("require that testJoinTargetExists() works", TestFixture)
{
    f.test.testJoinTargetExists();
}

TEST_F("require that testBucketActivationSplitAndJoin() works", SingleDocTypeFixture)
{
    f.test.testBucketActivationSplitAndJoin();
}

TEST_F("require thant testJoinSameSourceBuckets() works", TestFixture)
{
    f.test.testJoinSameSourceBuckets();
}

TEST_F("require thant testJoinSameSourceBucketsTargetExists() works",
       TestFixture)
{
    f.test.testJoinSameSourceBucketsTargetExists();
}

TEST_F("require that multiple bucket spaces works", TestFixture)
{
    f.test.testBucketSpaces();
}

// *** Run all conformance tests, but ignore the results BEGIN ***

#define CONVERT_TEST(testFunction)                                    \
namespace ns_ ## testFunction {                                       \
IGNORE_TEST_F(TEST_STR(testFunction) " (generated)", TestFixture()) { \
    f.test.testFunction();                                            \
}                                                                     \
} // namespace testFunction

#undef CPPUNIT_TEST
#define CPPUNIT_TEST(testFunction) CONVERT_TEST(testFunction)
#if 0
DEFINE_CONFORMANCE_TESTS();
#endif

// *** Run all conformance tests, but ignore the results END ***

TEST_MAIN()
{
    DummyFileHeaderContext::setCreator("persistenceconformance_test");
    TEST_RUN_ALL();
}
