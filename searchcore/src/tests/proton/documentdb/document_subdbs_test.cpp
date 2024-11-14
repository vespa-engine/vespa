// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/document/datatype/datatype.h>
#include <vespa/document/fieldvalue/intfieldvalue.h>
#include <vespa/document/repo/configbuilder.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/initializer/task_runner.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics.h>
#include <vespa/searchcore/proton/metrics/documentdb_tagged_metrics.h>
#include <vespa/searchcore/proton/metrics/dummy_wire_service.h>
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
#include <vespa/vespalib/util/destructor_callbacks.h>
#include <vespa/vespalib/util/idestructorcallback.h>
#include <vespa/vespalib/util/hw_info.h>
#include <vespa/vespalib/util/lambdatask.h>
#include <vespa/vespalib/util/size_literals.h>
#include <vespa/vespalib/util/testclock.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/testkit/test_path.h>

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
using vespalib::HwInfo;
using proton::index::IndexConfig;

using StoreOnlyConfig = StoreOnlyDocSubDB::Config;
using StoreOnlyContext = StoreOnlyDocSubDB::Context;
using FastAccessConfig = FastAccessDocSubDB::Config;
using FastAccessContext = FastAccessDocSubDB::Context;
using SearchableContext = SearchableDocSubDB::Context;
using AttributeGuardList = std::vector<AttributeGuard>;

namespace {
const std::string DOCTYPE_NAME = "searchdocument";
const std::string SUB_NAME = "subdb";
const std::string BASE_DIR = "basedir";
const SerialNum CFG_SERIAL = 5;

struct ConfigDir1 { static std::string dir() { return TEST_PATH("document_subdbs/cfg1"); } };
struct ConfigDir2 { static std::string dir() { return TEST_PATH("document_subdbs/cfg2"); } };
struct ConfigDir3 { static std::string dir() { return TEST_PATH("document_subdbs/cfg3"); } };
struct ConfigDir4 { static std::string dir() { return TEST_PATH("document_subdbs/cfg4"); } };

struct MySubDBOwner : public IDocumentSubDBOwner
{
    SessionManager _sessionMgr;
    MySubDBOwner();
    ~MySubDBOwner() override;
    document::BucketSpace getBucketSpace() const override { return makeBucketSpace(); }
    std::string getName() const override { return "owner"; }
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
    void addTags(vespalib::GenericHeader &, const std::string &) const override {}
};

struct MyMetricsWireService : public DummyWireService
{
    std::set<std::string> _attributes;
    MyMetricsWireService() : _attributes() {}
    void set_attributes(AttributeMetrics &, std::vector<std::string> field_names) override {
        for (auto &field_name: field_names) {
            _attributes.insert(field_name);
        }
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
    explicit MyStoreOnlyConfig(SubDbType subDbType)
        : _cfg(DocTypeName(DOCTYPE_NAME),
              SUB_NAME,
              BASE_DIR,
              0, subDbType)
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
      _ctx(_owner, _syncProxy, _getSerialNum, _fileHeader, writeService, std::move(bucketDB),
           bucketDBHandlerInitializer, _metrics, _configMutex, _hwInfo)
{
}
MyStoreOnlyContext::~MyStoreOnlyContext() = default;

template <bool FastAccessAttributesOnly>
struct MyFastAccessConfig
{
    FastAccessConfig _cfg;
    explicit MyFastAccessConfig(SubDbType subDbType)
        : _cfg(MyStoreOnlyConfig(subDbType)._cfg, FastAccessAttributesOnly)
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
    : _storeOnlyCtx(writeService, std::move(bucketDB), bucketDBHandlerInitializer),
      _attributeMetrics(nullptr),
      _wireService(),
      _ctx(_storeOnlyCtx._ctx, _attributeMetrics, _wireService, std::make_shared<search::attribute::Interlock>())
{}
MyFastAccessContext::~MyFastAccessContext() = default;

struct MySearchableConfig
{
    FastAccessConfig _cfg;
    explicit MySearchableConfig(SubDbType subDbType)
        : _cfg(MyFastAccessConfig<false>(subDbType)._cfg)
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
    : _fastUpdCtx(writeService, std::move(bucketDB), bucketDBHandlerInitializer),
      _queryLimiter(), _clock(),
      _ctx(_fastUpdCtx._ctx, _queryLimiter, _clock.nowRef(), writeService.shared(), {})
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
    MyConfigSnapshot(FNET_Transport & transport, Schema schema, const std::string &cfgDir);
    MyConfigSnapshot(const MyConfigSnapshot &) = delete;
    MyConfigSnapshot & operator = (const MyConfigSnapshot &) = delete;
    ~MyConfigSnapshot();
};

MyConfigSnapshot::~MyConfigSnapshot() = default;

MyConfigSnapshot::MyConfigSnapshot(FNET_Transport & transport, Schema schema, const std::string &cfgDir)
    : _schema(std::move(schema)),
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
                             tuneFileDocumentDB, HwInfo(HwInfo::Disk(128_Gi,false,false), HwInfo::Memory(16_Gi), HwInfo::Cpu(8)));
    ::config::DirSpec spec(cfgDir);
    DocumentDBConfigHelper mgr(spec, "searchdocument");
    mgr.forwardConfig(_bootstrap);
    mgr.nextGeneration(transport, 1ms);
    _cfg = mgr.getConfig();
}

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
          _cfg(Traits::subDbType),
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
    void reconfig(SerialNum serialNum, Schema reconfigSchema, const std::string &reconfigConfigDir) {
        runInMasterAndSync([&]() { performReconfig(serialNum, std::move(reconfigSchema), reconfigConfigDir); });
    }
    void performReconfig(SerialNum serialNum, Schema reconfigSchema, const std::string &reconfigConfigDir) {
        auto newCfg = std::make_unique<MyConfigSnapshot>(_service.transport(), std::move(reconfigSchema), reconfigConfigDir);
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
        auto& retval = dynamic_cast<const typename Traits::FeedView &>(*_tmpFeedView.get());
        return &retval;
    }
    const MyMetricsWireService &getWireService() const {
        return _ctx.getWireService();
    }
    const MySubDBOwner &getOwner() const {
        return _ctx.getOwner();
    }
};

template <bool has_attr2_in, typename ConfigDirT,
        uint32_t ConfigSerial = CFG_SERIAL, SubDbType subDbType_in = SubDbType::READY>
struct BaseTraitsT
{
    static constexpr bool has_attr2 = has_attr2_in;
    using ConfigDir = ConfigDirT;
    static uint32_t configSerial() { return ConfigSerial; }
    static constexpr SubDbType subDbType = subDbType_in;
};

using BaseTraits = BaseTraitsT<one_attr_schema, ConfigDir1>;

template <SubDbType subDbType>
struct StoreOnlyTraits : public BaseTraitsT<one_attr_schema, ConfigDir1, CFG_SERIAL, subDbType>
{
    using Config = MyStoreOnlyConfig;
    using Context = MyStoreOnlyContext;
    using SubDB = StoreOnlyDocSubDB;
    using FeedView = StoreOnlyFeedView;
};

using StoreOnlyFixture = FixtureBase<StoreOnlyTraits<SubDbType::READY>>;
using StoreOnlyFixtureRemoved = FixtureBase<StoreOnlyTraits<SubDbType::REMOVED>>;

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
    EXPECT_EQ(1u, attributes.size());
    EXPECT_EQ("attr1", attributes[0]->getName());
}

void
assertAttributes1(const std::vector<search::AttributeVector *> &attributes)
{
    EXPECT_EQ(1u, attributes.size());
    EXPECT_EQ("attr1", attributes[0]->getName());
}

void
assertAttributes2(const AttributeGuardList &attributes)
{
    EXPECT_EQ(2u, attributes.size());
    EXPECT_EQ("attr1", attributes[0]->getName());
    EXPECT_EQ("attr2", attributes[1]->getName());
}

void
assertAttributes2(const std::vector<search::AttributeVector *> &attributes)
{
    EXPECT_EQ(2u, attributes.size());
    EXPECT_EQ("attr1", attributes[0]->getName());
    EXPECT_EQ("attr2", attributes[1]->getName());
}

void
assertCacheCapacity(const StoreOnlyDocSubDB & db, size_t expected_cache_capacity) {
    const auto & summaryManager = db.getSummaryManager();
    EXPECT_TRUE(dynamic_cast<SummaryManager *>(summaryManager.get()) != nullptr);
    search::IDocumentStore & store = summaryManager->getBackingStore();
    auto & docStore = dynamic_cast<search::DocumentStore &>(store);
    EXPECT_EQ(expected_cache_capacity, docStore.getCacheCapacity());
}

void
assertStoreOnly(StoreOnlyDocSubDB & db) {
    EXPECT_TRUE(db.getSummaryManager());
    EXPECT_TRUE(db.getSummaryAdapter());
    EXPECT_TRUE( ! db.getAttributeManager());
    EXPECT_TRUE( ! db.getIndexManager());
    EXPECT_TRUE( ! db.getIndexWriter());
    EXPECT_TRUE(db.getFeedView());
    EXPECT_TRUE(db.getSearchView());
    EXPECT_TRUE(dynamic_cast<StoreOnlyFeedView *>(db.getFeedView().get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<EmptySearchView *>(db.getSearchView().get()) != nullptr);
    EXPECT_TRUE(dynamic_cast<MinimalDocumentRetriever *>(db.getDocumentRetriever().get()) != nullptr);
}

}

TEST(DocumentSubDBsTest, require_that_managers_and_components_are_instantiated_in_storeonly_document_subdb)
{
    StoreOnlyFixture f;
    assertStoreOnly(f._subDb);
    assertCacheCapacity(f._subDb, 687194767);
}

TEST(DocumentSubDBsTest, require_that_managers_and_components_are_instantiated_in_removed_document_subdb)
{
    StoreOnlyFixtureRemoved f;
    assertStoreOnly(f._subDb);
    assertCacheCapacity(f._subDb, 0);
}

TEST(DocumentSubDBsTest, require_that_managers_and_components_are_instantiated_in_fast_access_document_subdb)
{
    FastAccessFixture f;
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

TEST(DocumentSubDBsTest, require_that_managers_and_components_are_instantiated_in_searchable_document_subdb)
{
    SearchableFixture f;
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

TEST(DocumentSubDBsTest, require_that_attribute_manager_is_instantiated_in_fast_access_document_subdb)
{
    FastAccessFixture f;
    requireThatAttributeManagerIsInstantiated(f);
}

TEST(DocumentSubDBsTest, require_that_attribute_manager_is_instantiated_searchable_document_subdb)
{
    SearchableFixture f;
    requireThatAttributeManagerIsInstantiated(f);
}

template <typename Fixture>
void
requireThatAttributesAreAccessibleViaFeedView(Fixture &f)
{
    assertAttributes1(f.getFeedView()->getAttributeWriter()->getWritableAttributes());
}

TEST(DocumentSubDBsTest, require_that_attributes_are_accessible_via_fast_access_feed_view)
{
    FastAccessFixture f;
    requireThatAttributesAreAccessibleViaFeedView(f);
}

TEST(DocumentSubDBsTest, require_that_attributes_are_accessible_via_searchable_feed_view)
{
    SearchableFixture f;
    requireThatAttributesAreAccessibleViaFeedView(f);
}

template <typename Fixture>
void
requireThatAttributeManagerCanBeReconfigured(Fixture &f)
{
    f.basicReconfig(10);
    std::vector<AttributeGuard> attributes;
    f.getAttributeManager()->getAttributeList(attributes);
    assertAttributes2(attributes);
}

TEST(DocumentSubDBsTest, require_that_attribute_manager_in_fast_access_document_subdb_can_be_reconfigured)
{
    FastAccessFixture f;
    requireThatAttributeManagerCanBeReconfigured(f);
}

TEST(DocumentSubDBsTest, require_that_attribute_manager_in_searchable_document_subdb_can_be_reconfigured)
{
    SearchableFixture f;
    requireThatAttributeManagerCanBeReconfigured(f);
}

TEST(DocumentSubDBsTest, require_that_subdb_reflect_retirement_or_maintenance)
{
    FastAccessFixture f;
    CompactionStrategy cfg(0.1, 0.3);

    EXPECT_FALSE(f._subDb.is_node_retired_or_maintenance());
    auto unretired_cfg = f._subDb.computeCompactionStrategy(cfg);
    EXPECT_TRUE(cfg == unretired_cfg);

    auto calc = std::make_shared<proton::test::BucketStateCalculator>();
    calc->setNodeRetired(true);
    f.setBucketStateCalculator(calc);
    EXPECT_TRUE(f._subDb.is_node_retired_or_maintenance());
    auto retired_cfg = f._subDb.computeCompactionStrategy(cfg);
    EXPECT_TRUE(cfg != retired_cfg);
    EXPECT_TRUE(CompactionStrategy(0.5, 0.5) == retired_cfg);

    calc->setNodeRetired(false);
    calc->setNodeMaintenance(true);
    f.setBucketStateCalculator(calc);
    EXPECT_TRUE(f._subDb.is_node_retired_or_maintenance());

    calc->setNodeMaintenance(false);
    f.setBucketStateCalculator(calc);
    EXPECT_FALSE(f._subDb.is_node_retired_or_maintenance());
    unretired_cfg = f._subDb.computeCompactionStrategy(cfg);
    EXPECT_TRUE(cfg == unretired_cfg);
}

TEST(DocumentSubDBsTest, require_that_attribute_compaction_config_reflect_retirement_or_maintenance)
{
    FastAccessFixture f;
    CompactionStrategy default_cfg(0.05, 0.2);
    CompactionStrategy retired_cfg(0.5, 0.5);

    auto guard = f._subDb.getAttributeManager()->getAttribute("attr1");
    EXPECT_EQ(default_cfg, (*guard)->getConfig().getCompactionStrategy());
    EXPECT_EQ(default_cfg, dynamic_cast<const proton::DocumentMetaStore &>(f._subDb.getDocumentMetaStoreContext().get()).getConfig().getCompactionStrategy());

    auto calc = std::make_shared<proton::test::BucketStateCalculator>();
    calc->setNodeRetired(true);
    f.setBucketStateCalculator(calc);
    guard = f._subDb.getAttributeManager()->getAttribute("attr1");
    EXPECT_EQ(retired_cfg, (*guard)->getConfig().getCompactionStrategy());
    EXPECT_EQ(retired_cfg, dynamic_cast<const proton::DocumentMetaStore &>(f._subDb.getDocumentMetaStoreContext().get()).getConfig().getCompactionStrategy());

    f.basicReconfig(10);
    guard = f._subDb.getAttributeManager()->getAttribute("attr1");
    EXPECT_EQ(retired_cfg, (*guard)->getConfig().getCompactionStrategy());
    EXPECT_EQ(retired_cfg, dynamic_cast<const proton::DocumentMetaStore &>(f._subDb.getDocumentMetaStoreContext().get()).getConfig().getCompactionStrategy());

    calc->setNodeRetired(false);
    f.setBucketStateCalculator(calc);
    guard = f._subDb.getAttributeManager()->getAttribute("attr1");
    EXPECT_EQ(default_cfg, (*guard)->getConfig().getCompactionStrategy());
    EXPECT_EQ(default_cfg, dynamic_cast<const proton::DocumentMetaStore &>(f._subDb.getDocumentMetaStoreContext().get()).getConfig().getCompactionStrategy());

}

template <typename Fixture>
void
requireThatReconfiguredAttributesAreAccessibleViaFeedView(Fixture &f)
{
    f.basicReconfig(10);
    assertAttributes2(f.getFeedView()->getAttributeWriter()->getWritableAttributes());
}

TEST(DocumentSubDBsTest, require_that_reconfigured_attributes_are_accessible_via_fast_access_feed_view)
{
    FastAccessFixture f;
    requireThatReconfiguredAttributesAreAccessibleViaFeedView(f);
}

TEST(DocumentSubDBsTest, require_that_reconfigured_attributes_are_accessible_via_searchable_feed_view)
{
    SearchableFixture f;
    requireThatReconfiguredAttributesAreAccessibleViaFeedView(f);
}

template <typename Fixture>
void
requireThatAttributeMetricsAreRegistered(Fixture &f)
{
    EXPECT_EQ(2u, f.getWireService()._attributes.size());
    auto itr = f.getWireService()._attributes.begin();
    EXPECT_EQ("[documentmetastore]", *itr++);
    EXPECT_EQ("attr1", *itr);
}

TEST(DocumentSubDBsTest, require_that_attribute_metrics_are_registered_in_fast_access_document_subdb)
{
    FastAccessFixture f;
    requireThatAttributeMetricsAreRegistered(f);
}

TEST(DocumentSubDBsTest, require_that_attribute_metrics_are_registered_in_searchable_document_subdb)
{
    SearchableFixture f;
    requireThatAttributeMetricsAreRegistered(f);
}

template <typename Fixture>
void
requireThatAttributeMetricsCanBeReconfigured(Fixture &f)
{
    f.basicReconfig(10);
    EXPECT_EQ(3u, f.getWireService()._attributes.size());
    auto itr = f.getWireService()._attributes.begin();
    EXPECT_EQ("[documentmetastore]", *itr++);
    EXPECT_EQ("attr1", *itr++);
    EXPECT_EQ("attr2", *itr);
}

TEST(DocumentSubDBsTest, require_that_attribute_metrics_can_be_reconfigured_in_fast_access_document_subdb)
{
    FastAccessFixture f;
    requireThatAttributeMetricsCanBeReconfigured(f);
}

TEST(DocumentSubDBsTest, require_that_attribute_metrics_can_be_reconfigured_in_searchable_document_subdb)
{
    SearchableFixture f;
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
assertTarget(const std::string &name,
             FType type,
             FComponent component,
             const IFlushTarget &target)
{
    bool failed = false;
    EXPECT_EQ(name, target.getName()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_TRUE(type == target.getType()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_TRUE(component == target.getComponent()) << (failed = true, "");
    return !failed;
}

TEST(DocumentSubDBsTest, require_that_flush_targets_can_be_retrieved_from_fast_access_document_subdb)
{
    FastAccessFixture f;
    IFlushTarget::List targets = getFlushTargets(f);
    EXPECT_EQ(8u, targets.size());
    EXPECT_EQ("subdb.attribute.flush.attr1", targets[0]->getName());
    EXPECT_EQ("subdb.attribute.shrink.attr1", targets[1]->getName());
    EXPECT_EQ("subdb.documentmetastore.flush", targets[2]->getName());
    EXPECT_EQ("subdb.documentmetastore.shrink", targets[3]->getName());
    EXPECT_EQ("subdb.summary.compact_bloat", targets[4]->getName());
    EXPECT_EQ("subdb.summary.compact_spread", targets[5]->getName());
    EXPECT_EQ("subdb.summary.flush", targets[6]->getName());
    EXPECT_EQ("subdb.summary.shrink", targets[7]->getName());
}

TEST(DocumentSubDBsTest, require_that_flush_targets_can_be_retrieved_from_searchable_document_subdb)
{
    SearchableFixture f;
    IFlushTarget::List targets = getFlushTargets(f);
    EXPECT_EQ(10u, targets.size());
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

TEST(DocumentSubDBsTest, transient_resource_usage_is_zero_in_steady_state)
{
    SearchableFixture f;
    auto usage = f._subDb.get_transient_resource_usage();
    EXPECT_EQ(0u, usage.disk());
    EXPECT_EQ(0u, usage.memory());
}

TEST(DocumentSubDBsTest, require_that_only_fast_access_attributes_are_instantiated_in_fast_access_document_subdb)
{
    FastAccessOnlyFixture f;
    std::vector<AttributeGuard> attrs;
    f.getAttributeManager()->getAttributeList(attrs);
    EXPECT_EQ(1u, attrs.size());
    EXPECT_EQ("attr1", attrs[0]->getName());
}

template <typename FixtureType>
struct DocumentHandler
{
    FixtureType &_f;
    DocBuilder _builder;
    explicit DocumentHandler(FixtureType &f) : _f(f), _builder(get_add_fields(f.has_attr2)) {}
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
assertAttribute(const AttributeGuard &attr, const std::string &name, uint32_t numDocs,
                int64_t doc1Value, int64_t doc2Value, SerialNum createSerialNum, SerialNum lastSerialNum, std::string_view label)
{
    SCOPED_TRACE(label);
    EXPECT_EQ(name, attr->getName());
    EXPECT_EQ(numDocs, attr->getNumDocs());
    EXPECT_EQ(doc1Value, attr->getInt(1));
    EXPECT_EQ(doc2Value, attr->getInt(2));
    EXPECT_EQ(createSerialNum, attr->getCreateSerialNum());
    EXPECT_EQ(lastSerialNum, attr->getStatus().getLastSyncToken());
}

void
assertAttribute1(const AttributeGuard &attr, SerialNum createSerialNum, SerialNum lastSerialNum, std::string_view label)
{
    assertAttribute(attr, "attr1", 3, 22, 44, createSerialNum, lastSerialNum, label);
}

void
assertAttribute2(const AttributeGuard &attr, SerialNum createSerialNum, SerialNum lastSerialNum, std::string_view label)
{
    assertAttribute(attr, "attr2", 3, 33, 55, createSerialNum, lastSerialNum, label);
}

TEST(DocumentSubDBsTest, require_that_fast_access_attributes_are_populated_during_feed)
{
    FastAccessOnlyFixture f;
    f._subDb.onReplayDone();
    DocumentHandler<FastAccessOnlyFixture> handler(f);
    handler.putDocs();

    std::vector<AttributeGuard> attrs;
    f.getAttributeManager()->getAttributeList(attrs);
    EXPECT_EQ(1u, attrs.size());
    assertAttribute1(attrs[0], CFG_SERIAL, 20, "attr1");
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
        EXPECT_EQ(1u, attrs.size());
    }

    // Reconfig to 2 attribute fields
    f.reconfig(40u, make_all_attr_schema(two_attr_schema), ConfigDirT::dir());

    {
        std::vector<AttributeGuard> attrs;
        f.getAttributeManager()->getAttributeList(attrs);
        EXPECT_EQ(2u, attrs.size());
        assertAttribute1(attrs[0], CFG_SERIAL, 40, "attr1");
        assertAttribute2(attrs[1], 40, 40, "attr2");
    }
}

TEST(DocumentSubDBsTest, require_that_fast_access_attributes_are_populated_during_reprocessing)
{
    FastAccessOnlyFixture f;
    requireThatAttributesArePopulatedDuringReprocessing<FastAccessOnlyFixture, ConfigDir4>(f);
}

// Setup with 2 fields (1 attribute according to config in dir)
using SearchableTraitsTwoField = SearchableTraitsBase<two_attr_schema, ConfigDir1>;
using SearchableFixtureTwoField = FixtureBase<SearchableTraitsTwoField>;

TEST(DocumentSubDBsTest, require_that_regular_attributes_are_populated_during_reprocessing)
{
    SearchableFixtureTwoField f;
    requireThatAttributesArePopulatedDuringReprocessing<SearchableFixtureTwoField, ConfigDir2>(f);
}

namespace {

bool
assertOperation(DocumentOperation &op, uint32_t expPrevSubDbId, uint32_t expPrevLid,
                uint32_t expSubDbId, uint32_t expLid)
{
    bool failed = false;
    EXPECT_EQ(expPrevSubDbId, op.getPrevSubDbId()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(expPrevLid, op.getPrevLid()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(expSubDbId, op.getSubDbId()) << (failed = true, "");
    if (failed) {
        return false;
    }
    EXPECT_EQ(expLid, op.getLid()) << (failed = true, "");
    return !failed;
}

}

TEST(DocumentSubDBsTest, require_that_lid_allocation_uses_lowest_free_lid)
{
    StoreOnlyFixture f;
    using Handler = DocumentHandler<StoreOnlyFixture>;
    f._subDb.onReplayDone();
    Handler handler(f);
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
    rmOp = handler.createRemove(Handler::createDocId(1), Timestamp(30), 30);
    handler.removeDoc(rmOp);
    EXPECT_TRUE(assertOperation(rmOp, 0, 1, 0, 0));
    doc = handler.createEmptyDoc(3);
    putOp = handler.createPut(std::move(doc), Timestamp(40), 40);
    handler.putDoc(putOp);
    EXPECT_TRUE(assertOperation(putOp, 0, 0, 0, 1));
    rmOp = handler.createRemove(Handler::createDocId(3), Timestamp(50), 50);
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
using StringVector = std::vector<std::string>;

void
assertExplorer(const StringVector &extraNames, const vespalib::StateExplorer &explorer)
{
    StringVector allNames = {"documentmetastore", "documentstore"};
    allNames.insert(allNames.end(), extraNames.begin(), extraNames.end());
    EXPECT_EQ(allNames, explorer.get_children_names());
    EXPECT_TRUE(explorer.get_child("documentmetastore").get() != nullptr);
    EXPECT_TRUE(explorer.get_child("documentstore").get() != nullptr);
}

TEST(DocumentSubDBsTest, require_that_underlying_components_are_explorable_in_store_only_document_subdb)
{
    StoreOnlyExplorerFixture f;
    assertExplorer({}, f._explorer);
    EXPECT_TRUE(f._explorer.get_child("attribute").get() == nullptr);
    EXPECT_TRUE(f._explorer.get_child("attributewriter").get() == nullptr);
    EXPECT_TRUE(f._explorer.get_child("index").get() == nullptr);
}

TEST(DocumentSubDBsTest, require_that_underlying_components_are_explorable_in_fast_access_document_subdb)
{
    FastAccessExplorerFixture f;
    assertExplorer({"attribute", "attributewriter"}, f._explorer);
    EXPECT_TRUE(f._explorer.get_child("attribute").get() != nullptr);
    EXPECT_TRUE(f._explorer.get_child("attributewriter").get() != nullptr);
    EXPECT_TRUE(f._explorer.get_child("index").get() == nullptr);
}

TEST(DocumentSubDBsTest, require_that_underlying_components_are_explorable_in_searchable_document_subdb)
{
    SearchableExplorerFixture f;
    assertExplorer({"attribute", "attributewriter", "index"}, f._explorer);
    EXPECT_TRUE(f._explorer.get_child("attribute").get() != nullptr);
    EXPECT_TRUE(f._explorer.get_child("attributewriter").get() != nullptr);
    EXPECT_TRUE(f._explorer.get_child("index").get() != nullptr);
}
