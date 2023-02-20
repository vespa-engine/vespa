// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/initializer/task_runner.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics.h>
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reprocessing/reprocessingrunner.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/document_subdb_explorer.h>
#include <vespa/searchcore/proton/server/document_subdb_initializer.h>
#include <vespa/searchcore/proton/server/document_subdb_reconfig.h>
#include <vespa/searchcore/proton/server/emptysearchview.h>
#include <vespa/searchcore/proton/server/fast_access_document_retriever.h>
#include <vespa/searchcore/proton/server/i_document_subdb_owner.h>
#include <vespa/searchcore/proton/server/igetserialnum.h>
#include <vespa/searchcore/proton/server/minimal_document_retriever.h>
#include <vespa/searchcore/proton/server/reconfig_params.h>
#include <vespa/searchcore/proton/server/searchabledocsubdb.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/searchcore/proton/test/thread_utils.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/searchlib/test/doc_builder.h>
#include <vespa/searchlib/test/schema_builder.h>
#include <vespa/searchcommon/attribute/config.h>
#include <vespa/config-bucketspaces.h>
#include <vespa/config/subscription/sourcespec.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/testclock.h>
#include <vespa/vespalib/util/threadstackexecutor.h>

using namespace cloud::config::filedistribution;
using namespace document;
using namespace proton::matching;
using namespace proton;
using namespace search::common;
using namespace search::index;
using namespace search::transactionlog;
using namespace search;
using namespace searchcorespi;
using namespace vespalib;
using namespace std::chrono_literals;

using document::test::makeBucketSpace;
using proton::bucketdb::BucketDBHandler;
using proton::bucketdb::IBucketDBHandler;
using proton::bucketdb::IBucketDBHandlerInitializer;
using vespalib::IDestructorCallback;
using search::test::DirectoryHandler;
using search::test::DocBuilder;
using search::test::SchemaBuilder;
using searchcorespi::IFlushTarget;
using searchcorespi::index::IThreadingService;
using storage::spi::Timestamp;
using vespa::config::search::core::ProtonConfig;
using vespa::config::content::core::BucketspacesConfig;
using vespalib::datastore::CompactionStrategy;
using proton::index::IndexConfig;

using StoreOnlyConfig = StoreOnlyDocSubDB::Config;
using StoreOnlyContext = StoreOnlyDocSubDB::Context;
using FastAccessConfig = FastAccessDocSubDB::Config;
using FastAccessContext = FastAccessDocSubDB::Context;
using SearchableContext = SearchableDocSubDB::Context;
using AttributeGuardList = std::vector<AttributeGuard>;

const std::string DOCTYPE_NAME = "searchdocument";
const std::string SUB_NAME = "subdb";
const std::string BASE_DIR = "basedir";
const SerialNum CFG_SERIAL = 5;

struct ConfigDir1 { static vespalib::string dir() { return TEST_PATH("cfg1"); } };
struct ConfigDir2 { static vespalib::string dir() { return TEST_PATH("cfg2"); } };
struct ConfigDir3 { static vespalib::string dir() { return TEST_PATH("cfg3"); } };
struct ConfigDir4 { static vespalib::string dir() { return TEST_PATH("cfg4"); } };

struct MySubDBOwner : public IDocumentSubDBOwner
{
    SessionManager _sessionMgr;
    MySubDBOwner();
    ~MySubDBOwner() override;
    document::BucketSpace getBucketSpace() const override { return makeBucketSpace(); }
    vespalib::string getName() const override { return "owner"; }
    uint32_t getDistributionKey() const override { return -1; }
    SessionManager & session_manager() override { return _sessionMgr; }
};

MySubDBOwner::MySubDBOwner() : _sessionMgr(1) {}
MySubDBOwner::~MySubDBOwner() = default;

struct MySyncProxy : public SyncProxy
{
    void sync(SerialNum) override {}
};


struct MyGetSerialNum : public IGetSerialNum
{
    SerialNum getSerialNum() const override { return 0u; }
};

struct MyFileHeaderContext : public FileHeaderContext
{
    void addTags(vespalib::GenericHeader &, const vespalib::string &) const override {}
};

struct MyMetricsWireService : public DummyWireService
{
    std::set<vespalib::string> _attributes;
    MyMetricsWireService() : _attributes() {}
    void addAttribute(AttributeMetrics &, const std::string &name) override {
        _attributes.insert(name);
    }
};

struct MyDocumentDBReferenceResolver : public IDocumentDBReferenceResolver {
    std::unique_ptr<ImportedAttributesRepo> resolve(const search::IAttributeManager &,
                                                    const search::IAttributeManager &,
                                                    const std::shared_ptr<search::IDocumentMetaStoreContext> &,
                                                    vespalib::duration) override {
        return std::make_unique<ImportedAttributesRepo>();
    }
    void teardown(const search::IAttributeManager &) override { }
};

struct MyStoreOnlyConfig
{
    StoreOnlyConfig _cfg;
    MyStoreOnlyConfig()
        : _cfg(DocTypeName(DOCTYPE_NAME),
              SUB_NAME,
              BASE_DIR,
              0, SubDbType::READY)
    {
    }
};

struct MyStoreOnlyContext
{
    MySubDBOwner _owner;
    MySyncProxy _syncProxy;
    MyGetSerialNum _getSerialNum;
    MyFileHeaderContext _fileHeader;
    DocumentDBTaggedMetrics _metrics;
    std::mutex       _configMutex;
    HwInfo           _hwInfo;
    StoreOnlyContext _ctx;
    MyStoreOnlyContext(IThreadingService &writeService,
                       std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                       IBucketDBHandlerInitializer & bucketDBHandlerInitializer);
    ~MyStoreOnlyContext();
    const MySubDBOwner &getOwner() const {
        return _owner;
    }
};

MyStoreOnlyContext::MyStoreOnlyContext(IThreadingService &writeService,
                                       std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                                       IBucketDBHandlerInitializer &bucketDBHandlerInitializer)
    : _owner(), _syncProxy(), _getSerialNum(), _fileHeader(),
      _metrics(DOCTYPE_NAME, 1), _configMutex(), _hwInfo(),
      _ctx(_owner, _syncProxy, _getSerialNum, _fileHeader, writeService, bucketDB,
           bucketDBHandlerInitializer, _metrics, _configMutex, _hwInfo)
{
}
MyStoreOnlyContext::~MyStoreOnlyContext() = default;

template <bool FastAccessAttributesOnly>
struct MyFastAccessConfig
{
    FastAccessConfig _cfg;
    MyFastAccessConfig()
        : _cfg(MyStoreOnlyConfig()._cfg, true, true, FastAccessAttributesOnly)
    {
    }
};

struct MyFastAccessContext
{
    MyStoreOnlyContext _storeOnlyCtx;
    AttributeMetrics _attributeMetrics;
    MyMetricsWireService _wireService;
    FastAccessContext _ctx;
    MyFastAccessContext(IThreadingService &writeService,
                        std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                        IBucketDBHandlerInitializer & bucketDBHandlerInitializer);
    ~MyFastAccessContext();
    const MyMetricsWireService &getWireService() const {
        return _wireService;
    }
    const MySubDBOwner &getOwner() const {
        return _storeOnlyCtx.getOwner();
    }
};

MyFastAccessContext::MyFastAccessContext(IThreadingService &writeService,
                                         std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                                         IBucketDBHandlerInitializer & bucketDBHandlerInitializer)
    : _storeOnlyCtx(writeService, bucketDB, bucketDBHandlerInitializer),
      _attributeMetrics(nullptr),
      _wireService(),
      _ctx(_storeOnlyCtx._ctx, _attributeMetrics, _wireService, std::make_shared<search::attribute::Interlock>())
{}
MyFastAccessContext::~MyFastAccessContext() = default;

struct MySearchableConfig
{
    FastAccessConfig _cfg;
    MySearchableConfig()
        : _cfg(MyFastAccessConfig<false>()._cfg)
    {
    }
};

struct MySearchableContext
{
    MyFastAccessContext _fastUpdCtx;
    QueryLimiter        _queryLimiter;
    vespalib::TestClock _clock;
    SearchableContext   _ctx;
    MySearchableContext(IThreadingService &writeService,
                        std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                        IBucketDBHandlerInitializer & bucketDBHandlerInitializer);
    ~MySearchableContext();
    const MyMetricsWireService &getWireService() const {
        return _fastUpdCtx.getWireService();
    }
    const MySubDBOwner &getOwner() const {
        return _fastUpdCtx.getOwner();
    }
};


MySearchableContext::MySearchableContext(IThreadingService &writeService,
                                         std::shared_ptr<bucketdb::BucketDBOwner> bucketDB,
                                         IBucketDBHandlerInitializer & bucketDBHandlerInitializer)
    : _fastUpdCtx(writeService, bucketDB, bucketDBHandlerInitializer),
      _queryLimiter(), _clock(),
      _ctx(_fastUpdCtx._ctx, _queryLimiter, _clock.clock(), writeService.shared())
{}
MySearchableContext::~MySearchableContext() = default;

static inline constexpr bool one_attr_schema = false;

static inline constexpr bool two_attr_schema = true;

DocBuilder::AddFieldsType
get_add_fields(bool has_attr2)
{
    return [has_attr2](auto& header) {
               header.addField("attr1", DataType::T_INT);
               if (has_attr2) {
                   header.addField("attr2", DataType::T_INT);
               }
           };
}

Schema
make_all_attr_schema(bool has_attr2)
{
    DocBuilder db(get_add_fields(has_attr2));
    return SchemaBuilder(db).add_all_attributes().build();
}

struct MyConfigSnapshot
{
    using UP = std::unique_ptr<MyConfigSnapshot>;
    Schema _schema;
    DocBuilder _builder;
    DocumentDBConfig::SP _cfg;
    BootstrapConfig::SP  _bootstrap;
    MyConfigSnapshot(FNET_Transport & transport, const Schema &schema, const vespalib::string &cfgDir)
        : _schema(schema),
          _builder(get_add_fields(_schema.getNumAttributeFields() > 1)),
          _cfg(),
          _bootstrap()
    {
        auto documenttypesConfig = std::make_shared<DocumenttypesConfig>(_builder.get_documenttypes_config());
        auto tuneFileDocumentDB = std::make_shared<TuneFileDocumentDB>();
        _bootstrap = std::make_shared<BootstrapConfig>(1,
                                 documenttypesConfig,
                                 _builder.get_repo_sp(),
                                 std::make_shared<ProtonConfig>(),
                                 std::make_shared<FiledistributorrpcConfig>(),
                                 std::make_shared<BucketspacesConfig>(),
                                 tuneFileDocumentDB, HwInfo());
        ::config::DirSpec spec(cfgDir);
        DocumentDBConfigHelper mgr(spec, "searchdocument");
        mgr.forwardConfig(_bootstrap);
        mgr.nextGeneration(transport, 1ms);
        _cfg = mgr.getConfig();
    }
};

template <typename Traits>
struct FixtureBase
{
    TransportAndExecutorService _service;
    static constexpr bool has_attr2 = Traits::has_attr2;

    typename Traits::Config  _cfg;
    std::shared_ptr<bucketdb::BucketDBOwner> _bucketDB;
    BucketDBHandler          _bucketDBHandler;
    typename Traits::Context _ctx;
    Schema                   _baseSchema;
    MyConfigSnapshot::UP     _snapshot;
    DirectoryHandler         _baseDir;
    typename Traits::SubDB   _subDb;
    IFeedView::SP            _tmpFeedView;
    FixtureBase()
        : _service(1),
          _cfg(),
          _bucketDB(std::make_shared<bucketdb::BucketDBOwner>()),
          _bucketDBHandler(*_bucketDB),
          _ctx(_service.write(), _bucketDB, _bucketDBHandler),
          _baseSchema(make_all_attr_schema(has_attr2)),
          _snapshot(std::make_unique<MyConfigSnapshot>(_service.transport(), _baseSchema, Traits::ConfigDir::dir())),
          _baseDir(BASE_DIR + "/" + SUB_NAME, BASE_DIR),
          _subDb(_cfg._cfg, _ctx._ctx),
          _tmpFeedView()
    {
        init();
    }
    ~FixtureBase() {
        _service.write().master().execute(makeLambdaTask([this]() { _subDb.close(); }));
        _service.shutdown();
    }
    void setBucketStateCalculator(const std::shared_ptr<IBucketStateCalculator> & calc) {
        vespalib::Gate gate;
        _subDb.setBucketStateCalculator(calc, std::make_shared<vespalib::GateCallback>(gate));
        gate.await();
    }
    template <typename FunctionType>
    void runInMasterAndSync(FunctionType func) {
        proton::test::runInMasterAndSync(_service.write(), func);
    }
    template <typename FunctionType>
    void runInMaster(FunctionType func) {
        proton::test::runInMaster(_service.write(), func);
    }
    void init() {
        DocumentSubDbInitializer::SP task =
            _subDb.createInitializer(*_snapshot->_cfg, Traits::configSerial(), IndexConfig());
        vespalib::ThreadStackExecutor executor(1);
        initializer::TaskRunner taskRunner(executor);
        taskRunner.runTask(task);
        runInMasterAndSync([&]() { _subDb.initViews(*_snapshot->_cfg); });
    }
    void basicReconfig(SerialNum serialNum) {
        runInMasterAndSync([&]() { performReconfig(serialNum, make_all_attr_schema(two_attr_schema), ConfigDir2::dir()); });
    }
    void reconfig(SerialNum serialNum, const Schema &reconfigSchema, const vespalib::string &reconfigConfigDir) {
        runInMasterAndSync([&]() { performReconfig(serialNum, reconfigSchema, reconfigConfigDir); });
    }
    void performReconfig(SerialNum serialNum, const Schema &reconfigSchema, const vespalib::string &reconfigConfigDir) {
        auto newCfg = std::make_unique<MyConfigSnapshot>(_service.transport(), reconfigSchema, reconfigConfigDir);
        DocumentDBConfig::ComparisonResult cmpResult;
        cmpResult.attributesChanged = true;
        cmpResult.documenttypesChanged = true;
        cmpResult.documentTypeRepoChanged = true;
        MyDocumentDBReferenceResolver resolver;
        ReconfigParams reconfig_params(cmpResult);
        auto prepared_reconfig = _subDb.prepare_reconfig(*newCfg->_cfg, reconfig_params, serialNum);
        _subDb.complete_prepare_reconfig(*prepared_reconfig, serialNum);
        auto tasks = _subDb.applyConfig(*newCfg->_cfg, *_snapshot->_cfg,
                                        serialNum, reconfig_params, resolver, *prepared_reconfig);
        prepared_reconfig.reset();
        _snapshot = std::move(newCfg);
        if (!tasks.empty()) {
            ReprocessingRunner runner;
            runner.addTasks(tasks);
            runner.run();
        }
        _subDb.onReprocessDone(serialNum);
    }

    proton::IAttributeManager::SP getAttributeManager() {
        return _subDb.getAttributeManager();
    }
    const typename Traits::FeedView *getFeedView() {
        _tmpFeedView = _subDb.getFeedView();
        const typename Traits::FeedView *retval =
                dynamic_cast<typename Traits::FeedView *>(_tmpFeedView.get());
        ASSERT_TRUE(retval != nullptr);
        return retval;
    }
    const MyMetricsWireService &getWireService() const {
        return _ctx.getWireService();
    }
    const MySubDBOwner &getOwner() const {
        return _ctx.getOwner();
    }
};

template <bool has_attr2_in, typename ConfigDirT, uint32_t ConfigSerial = CFG_SERIAL>
struct BaseTraitsT
{
    static constexpr bool has_attr2 = has_attr2_in;
    using ConfigDir = ConfigDirT;
    static uint32_t configSerial() { return ConfigSerial; }
};

using BaseTraits = BaseTraitsT<one_attr_schema, ConfigDir1>;

struct StoreOnlyTraits : public BaseTraits
{
    using Config = MyStoreOnlyConfig;
    using Context = MyStoreOnlyContext;
    using SubDB = StoreOnlyDocSubDB;
    using FeedView = StoreOnlyFeedView;
};

using StoreOnlyFixture = FixtureBase<StoreOnlyTraits>;

struct FastAccessTraits : public BaseTraits
{
    using Config = MyFastAccessConfig<false>;
    using Context = MyFastAccessContext;
    using SubDB = FastAccessDocSubDB;
    using FeedView = FastAccessFeedView;
};

using FastAccessFixture = FixtureBase<FastAccessTraits>;

template <typename ConfigDirT>
struct FastAccessOnlyTraitsBase : public BaseTraitsT<two_attr_schema, ConfigDirT>
{
    using Config = MyFastAccessConfig<true>;
    using Context = MyFastAccessContext;
    using SubDB = FastAccessDocSubDB;
    using FeedView = FastAccessFeedView;
};

// Setup with 1 fast-access attribute
using FastAccessOnlyTraits = FastAccessOnlyTraitsBase<ConfigDir3>;
using FastAccessOnlyFixture = FixtureBase<FastAccessOnlyTraits>;

template <bool has_attr2_in, typename ConfigDirT>
struct SearchableTraitsBase : public BaseTraitsT<has_attr2_in, ConfigDirT>
{
    using Config = MySearchableConfig;
    using Context = MySearchableContext;
    using SubDB = SearchableDocSubDB;
    using FeedView = proton::SearchableFeedView;
};

using SearchableTraits = SearchableTraitsBase<one_attr_schema, ConfigDir1>;
using SearchableFixture = FixtureBase<SearchableTraits>;

void
assertAttributes1(const AttributeGuardList &attributes)
{
    EXPECT_EQUAL(1u, attributes.size());
    EXPECT_EQUAL("attr1", attributes[0]->getName());
}

void
assertAttributes1(const std::vector<search::AttributeVector *> &attributes)
{
    EXPECT_EQUAL(1u, attributes.size());
    EXPECT_EQUAL("attr1", attributes[0]->getName());
}

void
assertAttributes2(const AttributeGuardList &attributes)
{
    EXPECT_EQUAL(2u, attributes.size());
    EXPECT_EQUAL("attr1", attributes[0]->getName());
    EXPECT_EQUAL("attr2", attributes[1]->getName());
}

void
assertAttributes2(const std::vector<search::AttributeVector *> &attributes)
{
    EXPECT_EQUAL(2u, attributes.size());
    EXPECT_EQUAL("attr1", attributes[0]->getName());
    EXPECT_EQUAL("attr2", attributes[1]->getName());
}

TEST_F("require that managers and components are instantiated", StoreOnlyFixture)
{
    EXPECT_TRUE(f._subDb.getSummaryManager());
    EXPECT_TRUE(f._subDb.getSummaryAdapter());
    EXPECT_TRUE( ! f._subDb.getAttributeManager());
    EXPECT_TRUE( ! f._subDb.getIndexManager());
    EXPECT_TRUE( ! f._subDb.getIndexWriter());
    EXPECT_TRUE(f._subDb.getFeedView());
    EXPECT_TRUE(f._subDb.getSearchView());
    EXPECT_TRUE(dynamic_cast<StoreOnlyFeedView *>(f._subDb.getFeedView().get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<EmptySearchView *>(f._subDb.getSearchView().get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<MinimalDocumentRetriever *>(f._subDb.getDocumentRetriever().get()) != nullptr);
}

TEST_F("require that managers and components are instantiated", FastAccessFixture)
{
    EXPECT_TRUE(f._subDb.getSummaryManager());
    EXPECT_TRUE(f._subDb.getSummaryAdapter());
    EXPECT_TRUE(f._subDb.getAttributeManager());
    EXPECT_TRUE( ! f._subDb.getIndexManager());
    EXPECT_TRUE( ! f._subDb.getIndexWriter());
    EXPECT_TRUE(f._subDb.getFeedView());
    EXPECT_TRUE(f._subDb.getSearchView());
    EXPECT_TRUE(dynamic_cast<FastAccessFeedView *>(f._subDb.getFeedView().get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<EmptySearchView *>(f._subDb.getSearchView().get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<FastAccessDocumentRetriever *>(f._subDb.getDocumentRetriever().get()) != nullptr);
}

TEST_F("require that managers and components are instantiated", SearchableFixture)
{
    EXPECT_TRUE(f._subDb.getSummaryManager());
    EXPECT_TRUE(f._subDb.getSummaryAdapter());
    EXPECT_TRUE(f._subDb.getAttributeManager());
    EXPECT_TRUE(f._subDb.getIndexManager());
    EXPECT_TRUE(f._subDb.getIndexWriter());
    EXPECT_TRUE(f._subDb.getFeedView());
    EXPECT_TRUE(f._subDb.getSearchView());
    EXPECT_TRUE(dynamic_cast<SearchableFeedView *>(f._subDb.getFeedView().get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<SearchView *>(f._subDb.getSearchView().get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<FastAccessDocumentRetriever *>(f._subDb.getDocumentRetriever().get()) != nullptr);
}

template<typename Fixture>
void
requireThatAttributeManagerIsInstantiated(Fixture &f)
{
    std::vector<AttributeGuard> attributes;
    f.getAttributeManager()->getAttributeList(attributes);
    assertAttributes1(attributes);
}

TEST_F("require that attribute manager is instantiated", FastAccessFixture)
{
    requireThatAttributeManagerIsInstantiated(f);
}

TEST_F("require that attribute manager is instantiated", SearchableFixture)
{
    requireThatAttributeManagerIsInstantiated(f);
}

template <typename Fixture>
void
requireThatAttributesAreAccessibleViaFeedView(Fixture &f)
{
    assertAttributes1(f.getFeedView()->getAttributeWriter()->getWritableAttributes());
}

TEST_F("require that attributes are accessible via feed view", FastAccessFixture)
{
    requireThatAttributesAreAccessibleViaFeedView(f);
}

TEST_F("require that attributes are accessible via feed view", SearchableFixture)
{
    requireThatAttributesAreAccessibleViaFeedView(f);
}

template <typename Fixture>
void
requireThatAttributeManagerCanBeReconfigured(Fixture &f)
{
    f.basicReconfig(10);
    std::vector<AttributeGuard> attributes;
    f.getAttributeManager()->getAttributeList(attributes);
    TEST_DO(assertAttributes2(attributes));
}

TEST_F("require that attribute manager can be reconfigured", FastAccessFixture)
{
    requireThatAttributeManagerCanBeReconfigured(f);
}

TEST_F("require that attribute manager can be reconfigured", SearchableFixture)
{
    requireThatAttributeManagerCanBeReconfigured(f);
}

TEST_F("require that subdb reflect retirement", FastAccessFixture)
{
    CompactionStrategy cfg(0.1, 0.3);

    EXPECT_FALSE(f._subDb.isNodeRetired());
    auto unretired_cfg = f._subDb.computeCompactionStrategy(cfg);
    EXPECT_TRUE(cfg == unretired_cfg);

    auto calc = std::make_shared<proton::test::BucketStateCalculator>();
    calc->setNodeRetired(true);
    f.setBucketStateCalculator(calc);
    EXPECT_TRUE(f._subDb.isNodeRetired());
    auto retired_cfg = f._subDb.computeCompactionStrategy(cfg);
    EXPECT_TRUE(cfg != retired_cfg);
    EXPECT_TRUE(CompactionStrategy(0.5, 0.5) == retired_cfg);

    calc->setNodeRetired(false);
    f.setBucketStateCalculator(calc);
    EXPECT_FALSE(f._subDb.isNodeRetired());
    unretired_cfg = f._subDb.computeCompactionStrategy(cfg);
    EXPECT_TRUE(cfg == unretired_cfg);
}

TEST_F("require that attribute compaction config reflect retirement", FastAccessFixture) {
    CompactionStrategy default_cfg(0.05, 0.2);
    CompactionStrategy retired_cfg(0.5, 0.5);

    auto guard = f._subDb.getAttributeManager()->getAttribute("attr1");
    EXPECT_EQUAL(default_cfg, (*guard)->getConfig().getCompactionStrategy());
    EXPECT_EQUAL(default_cfg, dynamic_cast<const proton::DocumentMetaStore &>(f._subDb.getDocumentMetaStoreContext().get()).getConfig().getCompactionStrategy());

    auto calc = std::make_shared<proton::test::BucketStateCalculator>();
    calc->setNodeRetired(true);
    f.setBucketStateCalculator(calc);
    guard = f._subDb.getAttributeManager()->getAttribute("attr1");
    EXPECT_EQUAL(retired_cfg, (*guard)->getConfig().getCompactionStrategy());
    EXPECT_EQUAL(retired_cfg, dynamic_cast<const proton::DocumentMetaStore &>(f._subDb.getDocumentMetaStoreContext().get()).getConfig().getCompactionStrategy());

    f.basicReconfig(10);
    guard = f._subDb.getAttributeManager()->getAttribute("attr1");
    EXPECT_EQUAL(retired_cfg, (*guard)->getConfig().getCompactionStrategy());
    EXPECT_EQUAL(retired_cfg, dynamic_cast<const proton::DocumentMetaStore &>(f._subDb.getDocumentMetaStoreContext().get()).getConfig().getCompactionStrategy());

    calc->setNodeRetired(false);
    f.setBucketStateCalculator(calc);
    guard = f._subDb.getAttributeManager()->getAttribute("attr1");
    EXPECT_EQUAL(default_cfg, (*guard)->getConfig().getCompactionStrategy());
    EXPECT_EQUAL(default_cfg, dynamic_cast<const proton::DocumentMetaStore &>(f._subDb.getDocumentMetaStoreContext().get()).getConfig().getCompactionStrategy());

}

template <typename Fixture>
void
requireThatReconfiguredAttributesAreAccessibleViaFeedView(Fixture &f)
{
    f.basicReconfig(10);
    assertAttributes2(f.getFeedView()->getAttributeWriter()->getWritableAttributes());
}

TEST_F("require that reconfigured attributes are accessible via feed view", FastAccessFixture)
{
    requireThatReconfiguredAttributesAreAccessibleViaFeedView(f);
}

TEST_F("require that reconfigured attributes are accessible via feed view", SearchableFixture)
{
    requireThatReconfiguredAttributesAreAccessibleViaFeedView(f);
}

template <typename Fixture>
void
requireThatAttributeMetricsAreRegistered(Fixture &f)
{
    EXPECT_EQUAL(2u, f.getWireService()._attributes.size());
    auto itr = f.getWireService()._attributes.begin();
    EXPECT_EQUAL("[documentmetastore]", *itr++);
    EXPECT_EQUAL("attr1", *itr);
}

TEST_F("require that attribute metrics are registered", FastAccessFixture)
{
    requireThatAttributeMetricsAreRegistered(f);
}

TEST_F("require that attribute metrics are registered", SearchableFixture)
{
    requireThatAttributeMetricsAreRegistered(f);
}

template <typename Fixture>
void
requireThatAttributeMetricsCanBeReconfigured(Fixture &f)
{
    f.basicReconfig(10);
    EXPECT_EQUAL(3u, f.getWireService()._attributes.size());
    auto itr = f.getWireService()._attributes.begin();
    EXPECT_EQUAL("[documentmetastore]", *itr++);
    EXPECT_EQUAL("attr1", *itr++);
    EXPECT_EQUAL("attr2", *itr);
}

TEST_F("require that attribute metrics can be reconfigured", FastAccessFixture)
{
    requireThatAttributeMetricsCanBeReconfigured(f);
}

TEST_F("require that attribute metrics can be reconfigured", SearchableFixture)
{
    requireThatAttributeMetricsCanBeReconfigured(f);
}

template <typename Fixture>
IFlushTarget::List
getFlushTargets(Fixture &f)
{
    IFlushTarget::List targets = (static_cast<IDocumentSubDB &>(f._subDb)).getFlushTargets();
    std::sort(targets.begin(), targets.end(),
            [](const IFlushTarget::SP &lhs, const IFlushTarget::SP &rhs) {
        return lhs->getName() < rhs->getName(); });
    return targets;
}

using FType = IFlushTarget::Type;
using FComponent = IFlushTarget::Component;

bool
assertTarget(const vespalib::string &name,
             FType type,
             FComponent component,
             const IFlushTarget &target)
{
    if (!EXPECT_EQUAL(name, target.getName())) return false;
    if (!EXPECT_TRUE(type == target.getType())) return false;
    if (!EXPECT_TRUE(component == target.getComponent())) return false;
    return true;
}

TEST_F("require that flush targets can be retrieved", FastAccessFixture)
{
    IFlushTarget::List targets = getFlushTargets(f);
    EXPECT_EQUAL(8u, targets.size());
    EXPECT_EQUAL("subdb.attribute.flush.attr1", targets[0]->getName());
    EXPECT_EQUAL("subdb.attribute.shrink.attr1", targets[1]->getName());
    EXPECT_EQUAL("subdb.documentmetastore.flush", targets[2]->getName());
    EXPECT_EQUAL("subdb.documentmetastore.shrink", targets[3]->getName());
    EXPECT_EQUAL("subdb.summary.compact_bloat", targets[4]->getName());
    EXPECT_EQUAL("subdb.summary.compact_spread", targets[5]->getName());
    EXPECT_EQUAL("subdb.summary.flush", targets[6]->getName());
    EXPECT_EQUAL("subdb.summary.shrink", targets[7]->getName());
}

TEST_F("require that flush targets can be retrieved", SearchableFixture)
{
    IFlushTarget::List targets = getFlushTargets(f);
    EXPECT_EQUAL(10u, targets.size());
    EXPECT_TRUE(assertTarget("subdb.attribute.flush.attr1", FType::SYNC, FComponent::ATTRIBUTE, *targets[0]));
    EXPECT_TRUE(assertTarget("subdb.attribute.shrink.attr1", FType::GC, FComponent::ATTRIBUTE, *targets[1]));
    EXPECT_TRUE(assertTarget("subdb.documentmetastore.flush", FType::SYNC, FComponent::ATTRIBUTE, *targets[2]));
    EXPECT_TRUE(assertTarget("subdb.documentmetastore.shrink", FType::GC, FComponent::ATTRIBUTE, *targets[3]));
    EXPECT_TRUE(assertTarget("subdb.memoryindex.flush", FType::FLUSH, FComponent::INDEX, *targets[4]));
    EXPECT_TRUE(assertTarget("subdb.memoryindex.fusion", FType::GC, FComponent::INDEX, *targets[5]));
    EXPECT_TRUE(assertTarget("subdb.summary.compact_bloat", FType::GC, FComponent::DOCUMENT_STORE, *targets[6]));
    EXPECT_TRUE(assertTarget("subdb.summary.compact_spread", FType::GC, FComponent::DOCUMENT_STORE, *targets[7]));
    EXPECT_TRUE(assertTarget("subdb.summary.flush", FType::SYNC, FComponent::DOCUMENT_STORE, *targets[8]));
    EXPECT_TRUE(assertTarget("subdb.summary.shrink", FType::GC, FComponent::DOCUMENT_STORE, *targets[9]));
}

TEST_F("transient resource usage is zero in steady state", SearchableFixture)
{
    auto usage = f._subDb.get_transient_resource_usage();
    EXPECT_EQUAL(0u, usage.disk());
    EXPECT_EQUAL(0u, usage.memory());
}

TEST_F("require that only fast-access attributes are instantiated", FastAccessOnlyFixture)
{
    std::vector<AttributeGuard> attrs;
    f.getAttributeManager()->getAttributeList(attrs);
    EXPECT_EQUAL(1u, attrs.size());
    EXPECT_EQUAL("attr1", attrs[0]->getName());
}

template <typename FixtureType>
struct DocumentHandler
{
    FixtureType &_f;
    DocBuilder _builder;
    DocumentHandler(FixtureType &f) : _f(f), _builder(get_add_fields(f.has_attr2)) {}
    static constexpr uint32_t BUCKET_USED_BITS = 8;
    static DocumentId createDocId(uint32_t docId)
    {
        return DocumentId(vespalib::make_string("id:searchdocument:"
                                                "searchdocument::%u", docId));
    }
    Document::UP createEmptyDoc(uint32_t docId) {
        auto id = vespalib::make_string("id:searchdocument:searchdocument::%u",
                                        docId);
        return _builder.make_document(id);
    }
    Document::UP createDoc(uint32_t docId, int64_t attr1Value, int64_t attr2Value) {
        auto id = vespalib::make_string("id:searchdocument:searchdocument::%u", docId);
        auto doc = _builder.make_document(id);
        doc->setValue("attr1", IntFieldValue(attr1Value));
        doc->setValue("attr2", IntFieldValue(attr2Value));
        return doc;
    }
    PutOperation createPut(Document::UP doc, Timestamp timestamp, SerialNum serialNum) {
        proton::test::Document testDoc(Document::SP(doc.release()), 0, timestamp);
        PutOperation op(testDoc.getBucket(), testDoc.getTimestamp(), testDoc.getDoc());
        op.setSerialNum(serialNum);
        return op;
    }
    MoveOperation createMove(Document::UP doc, Timestamp timestamp, DbDocumentId sourceDbdId,
                             uint32_t targetSubDbId, SerialNum serialNum)
    {
        proton::test::Document testDoc(Document::SP(doc.release()), 0, timestamp);
        MoveOperation op(testDoc.getBucket(), testDoc.getTimestamp(), testDoc.getDoc(), sourceDbdId, targetSubDbId);
        op.setSerialNum(serialNum);
        return op;
    }
    RemoveOperationWithDocId createRemove(const DocumentId &docId, Timestamp timestamp, SerialNum serialNum)
    {
        const document::GlobalId &gid = docId.getGlobalId();
        BucketId bucket = gid.convertToBucketId();
        bucket.setUsedBits(BUCKET_USED_BITS);
        bucket = bucket.stripUnused();
        RemoveOperationWithDocId op(bucket, timestamp, docId);
        op.setSerialNum(serialNum);
        return op;
    }
    void putDoc(PutOperation &op) {
        IFeedView::SP feedView = _f._subDb.getFeedView();
        vespalib::Gate gate;
        _f.runInMaster([&]() {
            feedView->preparePut(op);
            feedView->handlePut(FeedToken(), op);
            feedView->forceCommit(CommitParam(op.getSerialNum()), std::make_shared<vespalib::GateCallback>(gate));
        });
        gate.await();
    }
    void moveDoc(MoveOperation &op) {
        IFeedView::SP feedView = _f._subDb.getFeedView();
        vespalib::Gate gate;
        _f.runInMaster([&]() {
            auto onDone = std::make_shared<vespalib::GateCallback>(gate);
            feedView->handleMove(op, onDone);
            feedView->forceCommit(CommitParam(op.getSerialNum()), onDone);
        });
        gate.await();
    }
    void removeDoc(RemoveOperation &op)
    {
        IFeedView::SP feedView = _f._subDb.getFeedView();
        vespalib::Gate gate;
        _f.runInMaster([&]() {
            feedView->prepareRemove(op);
            feedView->handleRemove(FeedToken(), op);
            feedView->forceCommit(CommitParam(op.getSerialNum()), std::make_shared<vespalib::GateCallback>(gate));
        });
        gate.await();
    }
    void putDocs() {
        PutOperation putOp = createPut(createDoc(1, 22, 33), Timestamp(10), 10);
        putDoc(putOp);
        putOp = createPut(createDoc(2, 44, 55), Timestamp(20), 20);
        putDoc(putOp);
    }
};

void
assertAttribute(const AttributeGuard &attr, const vespalib::string &name, uint32_t numDocs,
                int64_t doc1Value, int64_t doc2Value, SerialNum createSerialNum, SerialNum lastSerialNum)
{
    EXPECT_EQUAL(name, attr->getName());
    EXPECT_EQUAL(numDocs, attr->getNumDocs());
    EXPECT_EQUAL(doc1Value, attr->getInt(1));
    EXPECT_EQUAL(doc2Value, attr->getInt(2));
    EXPECT_EQUAL(createSerialNum, attr->getCreateSerialNum());
    EXPECT_EQUAL(lastSerialNum, attr->getStatus().getLastSyncToken());
}

void
assertAttribute1(const AttributeGuard &attr, SerialNum createSerialNum, SerialNum lastSerialNum)
{
    TEST_DO(assertAttribute(attr, "attr1", 3, 22, 44, createSerialNum, lastSerialNum));
}

void
assertAttribute2(const AttributeGuard &attr, SerialNum createSerialNum, SerialNum lastSerialNum)
{
    TEST_DO(assertAttribute(attr, "attr2", 3, 33, 55, createSerialNum, lastSerialNum));
}

TEST_F("require that fast-access attributes are populated during feed", FastAccessOnlyFixture)
{
    f._subDb.onReplayDone();
    DocumentHandler<FastAccessOnlyFixture> handler(f);
    handler.putDocs();

    std::vector<AttributeGuard> attrs;
    f.getAttributeManager()->getAttributeList(attrs);
    EXPECT_EQUAL(1u, attrs.size());
    assertAttribute1(attrs[0], CFG_SERIAL, 20);
}

template <typename FixtureType, typename ConfigDirT>
void
requireThatAttributesArePopulatedDuringReprocessing(FixtureType &f)
{
    f._subDb.onReplayDone();
    DocumentHandler<FixtureType> handler(f);
    handler.putDocs();

    {
        std::vector<AttributeGuard> attrs;
        f.getAttributeManager()->getAttributeList(attrs);
        EXPECT_EQUAL(1u, attrs.size());
    }

    // Reconfig to 2 attribute fields
    f.reconfig(40u, make_all_attr_schema(two_attr_schema), ConfigDirT::dir());

    {
        std::vector<AttributeGuard> attrs;
        f.getAttributeManager()->getAttributeList(attrs);
        EXPECT_EQUAL(2u, attrs.size());
        TEST_DO(assertAttribute1(attrs[0], CFG_SERIAL, 40));
        TEST_DO(assertAttribute2(attrs[1], 40, 40));
    }
}

TEST_F("require that fast-access attributes are populated during reprocessing",
        FastAccessOnlyFixture)
{
    requireThatAttributesArePopulatedDuringReprocessing<FastAccessOnlyFixture, ConfigDir4>(f);
}

// Setup with 2 fields (1 attribute according to config in dir)
using SearchableTraitsTwoField = SearchableTraitsBase<two_attr_schema, ConfigDir1>;
using SearchableFixtureTwoField = FixtureBase<SearchableTraitsTwoField>;

TEST_F("require that regular attributes are populated during reprocessing",
        SearchableFixtureTwoField)
{
    requireThatAttributesArePopulatedDuringReprocessing<SearchableFixtureTwoField, ConfigDir2>(f);
}

namespace {

bool
assertOperation(DocumentOperation &op, uint32_t expPrevSubDbId, uint32_t expPrevLid,
                uint32_t expSubDbId, uint32_t expLid)
{
    if (!EXPECT_EQUAL(expPrevSubDbId, op.getPrevSubDbId())) {
        return false;
    }
    if (!EXPECT_EQUAL(expPrevLid, op.getPrevLid())) {
        return false;
    }
    if (!EXPECT_EQUAL(expSubDbId, op.getSubDbId())) {
        return false;
    }
    if (!EXPECT_EQUAL(expLid, op.getLid())) {
        return false;
    }
    return true;
}

}

TEST_F("require that lid allocation uses lowest free lid", StoreOnlyFixture)
{
    f._subDb.onReplayDone();
    DocumentHandler<StoreOnlyFixture> handler(f);
    Document::UP doc;
    PutOperation putOp;
    RemoveOperationWithDocId rmOp;
    MoveOperation moveOp;

    doc = handler.createEmptyDoc(1);
    putOp = handler.createPut(std::move(doc), Timestamp(10), 10);
    handler.putDoc(putOp);
    EXPECT_TRUE(assertOperation(putOp, 0, 0, 0, 1));
    doc = handler.createEmptyDoc(2);
    putOp = handler.createPut(std::move(doc), Timestamp(20), 20);
    handler.putDoc(putOp);
    EXPECT_TRUE(assertOperation(putOp, 0, 0, 0, 2));
    rmOp = handler.createRemove(handler.createDocId(1), Timestamp(30), 30);
    handler.removeDoc(rmOp);
    EXPECT_TRUE(assertOperation(rmOp, 0, 1, 0, 0));
    doc = handler.createEmptyDoc(3);
    putOp = handler.createPut(std::move(doc), Timestamp(40), 40);
    handler.putDoc(putOp);
    EXPECT_TRUE(assertOperation(putOp, 0, 0, 0, 1));
    rmOp = handler.createRemove(handler.createDocId(3), Timestamp(50), 50);
    handler.removeDoc(rmOp);
    EXPECT_TRUE(assertOperation(rmOp, 0, 1, 0, 0));
    doc = handler.createEmptyDoc(2);
    moveOp = handler.createMove(std::move(doc), Timestamp(20),
                                DbDocumentId(0, 2), 0, 60);
    moveOp.setTargetLid(1);
    handler.moveDoc(moveOp);
    EXPECT_TRUE(assertOperation(moveOp, 0, 2, 0, 1));
    doc = handler.createEmptyDoc(3);
    putOp = handler.createPut(std::move(doc), Timestamp(70), 70);
    handler.putDoc(putOp);
    EXPECT_TRUE(assertOperation(putOp, 0, 0, 0, 2));
}

template <typename FixtureType>
struct ExplorerFixture : public FixtureType
{
    DocumentSubDBExplorer _explorer;
    ExplorerFixture()
        : FixtureType(),
          _explorer(this->_subDb)
    {
    }
};

using StoreOnlyExplorerFixture = ExplorerFixture<StoreOnlyFixture>;
using FastAccessExplorerFixture = ExplorerFixture<FastAccessFixture>;
using SearchableExplorerFixture = ExplorerFixture<SearchableFixture>;
using StringVector = std::vector<vespalib::string>;

void
assertExplorer(const StringVector &extraNames, const vespalib::StateExplorer &explorer)
{
    StringVector allNames = {"documentmetastore", "documentstore"};
    allNames.insert(allNames.end(), extraNames.begin(), extraNames.end());
    EXPECT_EQUAL(allNames, explorer.get_children_names());
    EXPECT_TRUE(explorer.get_child("documentmetastore").get() != nullptr);
    EXPECT_TRUE(explorer.get_child("documentstore").get() != nullptr);
}

TEST_F("require that underlying components are explorable", StoreOnlyExplorerFixture)
{
    assertExplorer({}, f._explorer);
    EXPECT_TRUE(f._explorer.get_child("attribute").get() == nullptr);
    EXPECT_TRUE(f._explorer.get_child("index").get() == nullptr);
}

TEST_F("require that underlying components are explorable", FastAccessExplorerFixture)
{
    assertExplorer({"attribute"}, f._explorer);
    EXPECT_TRUE(f._explorer.get_child("attribute").get() != nullptr);
    EXPECT_TRUE(f._explorer.get_child("index").get() == nullptr);
}

TEST_F("require that underlying components are explorable", SearchableExplorerFixture)
{
    assertExplorer({"attribute", "index"}, f._explorer);
    EXPECT_TRUE(f._explorer.get_child("attribute").get() != nullptr);
    EXPECT_TRUE(f._explorer.get_child("index").get() != nullptr);
}

TEST_MAIN()
{
    TEST_RUN_ALL();
}
