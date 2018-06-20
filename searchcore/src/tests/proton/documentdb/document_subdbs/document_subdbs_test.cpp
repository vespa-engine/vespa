// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-bucketspaces.h>
#include <vespa/document/test/make_bucket_space.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/bucketdb/bucketdbhandler.h>
#include <vespa/searchcore/proton/common/hw_info.h>
#include <vespa/searchcore/proton/initializer/task_runner.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics.h>
#include <vespa/searchcore/proton/metrics/attribute_metrics_collection.h>
#include <vespa/searchcore/proton/metrics/documentdb_metrics_collection.h>
#include <vespa/searchcore/proton/metrics/legacy_attribute_metrics.h>
#include <vespa/searchcore/proton/metrics/metricswireservice.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_task.h>
#include <vespa/searchcore/proton/reprocessing/reprocessingrunner.h>
#include <vespa/searchcore/proton/feedoperation/operations.h>
#include <vespa/searchcore/proton/server/bootstrapconfig.h>
#include <vespa/searchcore/proton/server/document_subdb_explorer.h>
#include <vespa/searchcore/proton/server/emptysearchview.h>
#include <vespa/searchcore/proton/server/fast_access_document_retriever.h>
#include <vespa/searchcore/proton/server/i_document_subdb_owner.h>
#include <vespa/searchcore/proton/server/minimal_document_retriever.h>
#include <vespa/searchcore/proton/server/searchabledocsubdb.h>
#include <vespa/searchcore/proton/test/test.h>
#include <vespa/searchcore/proton/test/thread_utils.h>
#include <vespa/searchcorespi/plugin/iindexmanagerfactory.h>
#include <vespa/searchlib/common/idestructorcallback.h>
#include <vespa/searchlib/index/docbuilder.h>
#include <vespa/searchlib/test/directory_handler.h>
#include <vespa/vespalib/io/fileutil.h>
#include <vespa/vespalib/test/insertion_operators.h>
#include <vespa/vespalib/testkit/test_kit.h>
#include <vespa/vespalib/util/lambdatask.h>

#include <iostream>

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

using document::test::makeBucketSpace;
using proton::bucketdb::BucketDBHandler;
using proton::bucketdb::IBucketDBHandler;
using proton::bucketdb::IBucketDBHandlerInitializer;
using search::IDestructorCallback;
using search::test::DirectoryHandler;
using searchcorespi::IFlushTarget;
using searchcorespi::index::IThreadingService;
using storage::spi::Timestamp;
using vespa::config::search::core::ProtonConfig;
using vespa::config::content::core::BucketspacesConfig;
using vespalib::mkdir;

typedef StoreOnlyDocSubDB::Config StoreOnlyConfig;
typedef StoreOnlyDocSubDB::Context StoreOnlyContext;
typedef FastAccessDocSubDB::Config FastAccessConfig;
typedef FastAccessDocSubDB::Context FastAccessContext;
typedef SearchableDocSubDB::Config SearchableConfig;
typedef SearchableDocSubDB::Context SearchableContext;
typedef std::vector<AttributeGuard> AttributeGuardList;

const std::string DOCTYPE_NAME = "searchdocument";
const std::string SUB_NAME = "subdb";
const std::string BASE_DIR = "basedir";
const SerialNum CFG_SERIAL = 5;

struct ConfigDir1 { static vespalib::string dir() { return TEST_PATH("cfg1"); } };
struct ConfigDir2 { static vespalib::string dir() { return TEST_PATH("cfg2"); } };
struct ConfigDir3 { static vespalib::string dir() { return TEST_PATH("cfg3"); } };
struct ConfigDir4 { static vespalib::string dir() { return TEST_PATH("cfg4")
                ; } };

struct MySubDBOwner : public IDocumentSubDBOwner
{
    uint32_t _syncCnt;
    MySubDBOwner() : _syncCnt(0) {}
    void syncFeedView() override { ++_syncCnt; }
    document::BucketSpace getBucketSpace() const override { return makeBucketSpace(); }
    vespalib::string getName() const override { return "owner"; }
    uint32_t getDistributionKey() const override { return -1; }
};

struct MySyncProxy : public SyncProxy
{
    virtual void sync(SerialNum) override {}
};


struct MyGetSerialNum : public IGetSerialNum
{
    virtual SerialNum getSerialNum() const override { return 0u; }
};

struct MyFileHeaderContext : public FileHeaderContext
{
    virtual void addTags(vespalib::GenericHeader &, const vespalib::string &) const override {}
};

struct MyMetricsWireService : public DummyWireService
{
    std::set<vespalib::string> _attributes;
    MyMetricsWireService() : _attributes() {}
    virtual void addAttribute(const AttributeMetricsCollection &, LegacyAttributeMetrics *, const std::string &name) override {
        _attributes.insert(name);
    }
};

struct MyDocumentDBReferenceResolver : public IDocumentDBReferenceResolver {
    std::unique_ptr<ImportedAttributesRepo> resolve(const search::IAttributeManager &,
                                                    const search::IAttributeManager &,
                                                    const std::shared_ptr<search::IDocumentMetaStoreContext> &,
                                                    fastos::TimeStamp) override {
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
              GrowStrategy(),
                   0, 0, SubDbType::READY)
    {
    }
};

struct MyStoreOnlyContext
{
    MySubDBOwner _owner;
    MySyncProxy _syncProxy;
    MyGetSerialNum _getSerialNum;
    MyFileHeaderContext _fileHeader;
    DocumentDBMetricsCollection _metrics;
    std::mutex       _configMutex;
    HwInfo           _hwInfo;
    StoreOnlyContext _ctx;
    MyStoreOnlyContext(IThreadingService &writeService,
                       ThreadStackExecutorBase &summaryExecutor,
                       std::shared_ptr<BucketDBOwner> bucketDB,
                       IBucketDBHandlerInitializer &
                       bucketDBHandlerInitializer);
    ~MyStoreOnlyContext();
    const MySubDBOwner &getOwner() const {
        return _owner;
    }
};

MyStoreOnlyContext::MyStoreOnlyContext(IThreadingService &writeService, ThreadStackExecutorBase &summaryExecutor,
                                       std::shared_ptr<BucketDBOwner> bucketDB,
                                       IBucketDBHandlerInitializer &bucketDBHandlerInitializer)
    : _owner(), _syncProxy(), _getSerialNum(), _fileHeader(),
      _metrics(DOCTYPE_NAME, 1), _configMutex(), _hwInfo(),
      _ctx(_owner, _syncProxy, _getSerialNum, _fileHeader, writeService, summaryExecutor, bucketDB,
           bucketDBHandlerInitializer, _metrics, _configMutex, _hwInfo)
{
}
MyStoreOnlyContext::~MyStoreOnlyContext() {}

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
    LegacyAttributeMetrics _legacyAttributeMetrics;
    AttributeMetricsCollection _attributeMetricsCollection;
    MyMetricsWireService _wireService;
    FastAccessContext _ctx;
    MyFastAccessContext(IThreadingService &writeService,
                        ThreadStackExecutorBase &summaryExecutor,
                        std::shared_ptr<BucketDBOwner> bucketDB,
                        IBucketDBHandlerInitializer & bucketDBHandlerInitializer);
    ~MyFastAccessContext();
    const MyMetricsWireService &getWireService() const {
        return _wireService;
    }
    const MySubDBOwner &getOwner() const {
        return _storeOnlyCtx.getOwner();
    }
};

MyFastAccessContext::MyFastAccessContext(IThreadingService &writeService, ThreadStackExecutorBase &summaryExecutor,
                                         std::shared_ptr<BucketDBOwner> bucketDB,
                                         IBucketDBHandlerInitializer & bucketDBHandlerInitializer)
    : _storeOnlyCtx(writeService, summaryExecutor, bucketDB, bucketDBHandlerInitializer),
      _attributeMetrics(NULL), _legacyAttributeMetrics(NULL),
      _attributeMetricsCollection(_attributeMetrics, _legacyAttributeMetrics),
      _wireService(),
      _ctx(_storeOnlyCtx._ctx, _attributeMetricsCollection, NULL, _wireService)
{}
MyFastAccessContext::~MyFastAccessContext() {}

struct MySearchableConfig
{
    SearchableConfig _cfg;
    MySearchableConfig()
        : _cfg(MyFastAccessConfig<false>()._cfg, 1)
    {
    }
};

struct MySearchableContext
{
    MyFastAccessContext _fastUpdCtx;
    QueryLimiter _queryLimiter;
    vespalib::Clock _clock;
    SearchableContext _ctx;
    MySearchableContext(IThreadingService &writeService,
                        ThreadStackExecutorBase &executor,
                        std::shared_ptr<BucketDBOwner> bucketDB,
                        IBucketDBHandlerInitializer & bucketDBHandlerInitializer);
    ~MySearchableContext();
    const MyMetricsWireService &getWireService() const {
        return _fastUpdCtx.getWireService();
    }
    const MySubDBOwner &getOwner() const {
        return _fastUpdCtx.getOwner();
    }
};


MySearchableContext::MySearchableContext(IThreadingService &writeService, ThreadStackExecutorBase &executor,
                                         std::shared_ptr<BucketDBOwner> bucketDB,
                                         IBucketDBHandlerInitializer & bucketDBHandlerInitializer)
    : _fastUpdCtx(writeService, executor, bucketDB, bucketDBHandlerInitializer),
      _queryLimiter(), _clock(),
      _ctx(_fastUpdCtx._ctx, _queryLimiter, _clock, executor)
{}
MySearchableContext::~MySearchableContext() {}

struct OneAttrSchema : public Schema
{
    OneAttrSchema() {
        addAttributeField(Schema::AttributeField("attr1", Schema::DataType::INT32));
    }
};

struct TwoAttrSchema : public OneAttrSchema
{
    TwoAttrSchema() {
        addAttributeField(Schema::AttributeField("attr2", Schema::DataType::INT32));
    }
};

struct MyConfigSnapshot
{
    typedef std::unique_ptr<MyConfigSnapshot> UP;
    Schema _schema;
    DocBuilder _builder;
    DocumentDBConfig::SP _cfg;
    BootstrapConfig::SP  _bootstrap;
    MyConfigSnapshot(const Schema &schema,
                     const vespalib::string &cfgDir)
        : _schema(schema),
          _builder(_schema),
          _cfg(),
          _bootstrap()
    {
        DocumentDBConfig::DocumenttypesConfigSP documenttypesConfig
            (new DocumenttypesConfig(_builder.getDocumenttypesConfig()));
        TuneFileDocumentDB::SP tuneFileDocumentDB(new TuneFileDocumentDB());
        _bootstrap = std::make_shared<BootstrapConfig>(1,
                                 documenttypesConfig,
                                 _builder.getDocumentTypeRepo(),
                                 std::make_shared<ProtonConfig>(),
                                 std::make_shared<FiledistributorrpcConfig>(),
                                 std::make_shared<BucketspacesConfig>(),
                                 tuneFileDocumentDB, HwInfo());
        config::DirSpec spec(cfgDir);
        DocumentDBConfigHelper mgr(spec, "searchdocument");
        mgr.forwardConfig(_bootstrap);
        mgr.nextGeneration(1);
        _cfg = mgr.getConfig();
    }
};

template <typename Traits>
struct FixtureBase
{
    ExecutorThreadingService _writeService;
    ThreadStackExecutor _summaryExecutor;
    typename Traits::Config _cfg;
    std::shared_ptr<BucketDBOwner> _bucketDB;
    BucketDBHandler _bucketDBHandler;
    typename Traits::Context _ctx;
    typename Traits::Schema _baseSchema;
    MyConfigSnapshot::UP _snapshot;
    DirectoryHandler _baseDir;
    typename Traits::SubDB _subDb;
    IFeedView::SP _tmpFeedView;
    FixtureBase()
        : _writeService(),
          _summaryExecutor(1, 64 * 1024),
          _cfg(),
              _bucketDB(std::make_shared<BucketDBOwner>()),
              _bucketDBHandler(*_bucketDB),
          _ctx(_writeService, _summaryExecutor, _bucketDB,
                   _bucketDBHandler),
          _baseSchema(),
          _snapshot(new MyConfigSnapshot(_baseSchema, Traits::ConfigDir::dir())),
          _baseDir(BASE_DIR + "/" + SUB_NAME, BASE_DIR),
          _subDb(_cfg._cfg, _ctx._ctx),
              _tmpFeedView()
    {
        init();
    }
    ~FixtureBase() {
        _writeService.sync();
            _writeService.master().execute(makeLambdaTask([this]() { _subDb.close(); }));
        _writeService.sync();
    }
    template <typename FunctionType>
    void runInMaster(FunctionType func) {
        proton::test::runInMaster(_writeService, func);
    }
    void init() {
                DocumentSubDbInitializer::SP task =
                    _subDb.createInitializer(*_snapshot->_cfg,
                                             Traits::configSerial(),
                                             ProtonConfig::Index());
                vespalib::ThreadStackExecutor executor(1, 1024 * 1024);
                initializer::TaskRunner taskRunner(executor);
                taskRunner.runTask(task);
        SessionManager::SP sessionMgr(new SessionManager(1));
        runInMaster([&] () { _subDb.initViews(*_snapshot->_cfg, sessionMgr); });
    }
    void basicReconfig(SerialNum serialNum) {
        runInMaster([&] () { performReconfig(serialNum, TwoAttrSchema(), ConfigDir2::dir()); });
    }
    void reconfig(SerialNum serialNum, const Schema &reconfigSchema, const vespalib::string &reconfigConfigDir) {
        runInMaster([&] () { performReconfig(serialNum, reconfigSchema, reconfigConfigDir); });
    }
    void performReconfig(SerialNum serialNum, const Schema &reconfigSchema, const vespalib::string &reconfigConfigDir) {
        MyConfigSnapshot::UP newCfg(new MyConfigSnapshot(reconfigSchema, reconfigConfigDir));
        DocumentDBConfig::ComparisonResult cmpResult;
        cmpResult.attributesChanged = true;
        cmpResult.documenttypesChanged = true;
        cmpResult.documentTypeRepoChanged = true;
        MyDocumentDBReferenceResolver resolver;
        auto tasks = _subDb.applyConfig(*newCfg->_cfg, *_snapshot->_cfg,
                                        serialNum, ReconfigParams(cmpResult), resolver);
        _snapshot = std::move(newCfg);
        if (!tasks.empty()) {
            ReprocessingRunner runner;
            runner.addTasks(tasks);
            runner.run();
        }
        _subDb.onReprocessDone(serialNum);
    }
    void sync() {
        _writeService.master().sync();
    }
    proton::IAttributeManager::SP getAttributeManager() {
        return _subDb.getAttributeManager();
    }
    const typename Traits::FeedView *getFeedView() {
        _tmpFeedView = _subDb.getFeedView();
        const typename Traits::FeedView *retval =
                dynamic_cast<typename Traits::FeedView *>(_tmpFeedView.get());
        ASSERT_TRUE(retval != NULL);
        return retval;
    }
    const MyMetricsWireService &getWireService() const {
        return _ctx.getWireService();
    }
    const MySubDBOwner &getOwner() const {
        return _ctx.getOwner();
    }
};

template <typename SchemaT, typename ConfigDirT, uint32_t ConfigSerial = CFG_SERIAL>
struct BaseTraitsT
{
    typedef SchemaT Schema;
    typedef ConfigDirT ConfigDir;
    static uint32_t configSerial() { return ConfigSerial; }
};

typedef BaseTraitsT<OneAttrSchema, ConfigDir1> BaseTraits;

struct StoreOnlyTraits : public BaseTraits
{
    typedef MyStoreOnlyConfig Config;
    typedef MyStoreOnlyContext Context;
    typedef StoreOnlyDocSubDB SubDB;
    typedef StoreOnlyFeedView FeedView;
};

typedef FixtureBase<StoreOnlyTraits> StoreOnlyFixture;

struct FastAccessTraits : public BaseTraits
{
    typedef MyFastAccessConfig<false> Config;
    typedef MyFastAccessContext Context;
    typedef FastAccessDocSubDB SubDB;
    typedef FastAccessFeedView FeedView;
};

typedef FixtureBase<FastAccessTraits> FastAccessFixture;

template <typename ConfigDirT>
struct FastAccessOnlyTraitsBase : public BaseTraitsT<TwoAttrSchema, ConfigDirT>
{
    typedef MyFastAccessConfig<true> Config;
    typedef MyFastAccessContext Context;
    typedef FastAccessDocSubDB SubDB;
    typedef FastAccessFeedView FeedView;
};

// Setup with 1 fast-access attribute
typedef FastAccessOnlyTraitsBase<ConfigDir3> FastAccessOnlyTraits;
typedef FixtureBase<FastAccessOnlyTraits> FastAccessOnlyFixture;

template <typename SchemaT, typename ConfigDirT>
struct SearchableTraitsBase : public BaseTraitsT<SchemaT, ConfigDirT>
{
    typedef MySearchableConfig Config;
    typedef MySearchableContext Context;
    typedef SearchableDocSubDB SubDB;
    typedef proton::SearchableFeedView FeedView;
};

typedef SearchableTraitsBase<OneAttrSchema, ConfigDir1> SearchableTraits;
typedef FixtureBase<SearchableTraits> SearchableFixture;

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
    EXPECT_TRUE(f._subDb.getSummaryManager().get() != NULL);
    EXPECT_TRUE(f._subDb.getSummaryAdapter().get() != NULL);
    EXPECT_TRUE(f._subDb.getAttributeManager().get() == NULL);
    EXPECT_TRUE(f._subDb.getIndexManager().get() == NULL);
    EXPECT_TRUE(f._subDb.getIndexWriter().get() == NULL);
    EXPECT_TRUE(f._subDb.getFeedView().get() != NULL);
    EXPECT_TRUE(f._subDb.getSearchView().get() != NULL);
    EXPECT_TRUE(dynamic_cast<StoreOnlyFeedView *>(f._subDb.getFeedView().get()) != NULL);
    EXPECT_TRUE(dynamic_cast<EmptySearchView *>(f._subDb.getSearchView().get()) != NULL);
    EXPECT_TRUE(dynamic_cast<MinimalDocumentRetriever *>(f._subDb.getDocumentRetriever().get()) != NULL);
}

TEST_F("require that managers and components are instantiated", FastAccessFixture)
{
    EXPECT_TRUE(f._subDb.getSummaryManager().get() != NULL);
    EXPECT_TRUE(f._subDb.getSummaryAdapter().get() != NULL);
    EXPECT_TRUE(f._subDb.getAttributeManager().get() != NULL);
    EXPECT_TRUE(f._subDb.getIndexManager().get() == NULL);
    EXPECT_TRUE(f._subDb.getIndexWriter().get() == NULL);
    EXPECT_TRUE(f._subDb.getFeedView().get() != NULL);
    EXPECT_TRUE(f._subDb.getSearchView().get() != NULL);
    EXPECT_TRUE(dynamic_cast<FastAccessFeedView *>(f._subDb.getFeedView().get()) != NULL);
    EXPECT_TRUE(dynamic_cast<EmptySearchView *>(f._subDb.getSearchView().get()) != NULL);
    EXPECT_TRUE(dynamic_cast<FastAccessDocumentRetriever *>(f._subDb.getDocumentRetriever().get()) != NULL);
}

TEST_F("require that managers and components are instantiated", SearchableFixture)
{
    EXPECT_TRUE(f._subDb.getSummaryManager().get() != NULL);
    EXPECT_TRUE(f._subDb.getSummaryAdapter().get() != NULL);
    EXPECT_TRUE(f._subDb.getAttributeManager().get() != NULL);
    EXPECT_TRUE(f._subDb.getIndexManager().get() != NULL);
    EXPECT_TRUE(f._subDb.getIndexWriter().get() != NULL);
    EXPECT_TRUE(f._subDb.getFeedView().get() != NULL);
    EXPECT_TRUE(f._subDb.getSearchView().get() != NULL);
    EXPECT_TRUE(dynamic_cast<SearchableFeedView *>(f._subDb.getFeedView().get()) != NULL);
    EXPECT_TRUE(dynamic_cast<SearchView *>(f._subDb.getSearchView().get()) != NULL);
    EXPECT_TRUE(dynamic_cast<FastAccessDocumentRetriever *>(f._subDb.getDocumentRetriever().get()) != NULL);
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
    assertAttributes2(attributes);
}

TEST_F("require that attribute manager can be reconfigured", FastAccessFixture)
{
    requireThatAttributeManagerCanBeReconfigured(f);
}

TEST_F("require that attribute manager can be reconfigured", SearchableFixture)
{
    requireThatAttributeManagerCanBeReconfigured(f);
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
requireThatOwnerIsNotifiedWhenFeedViewChanges(Fixture &f)
{
    EXPECT_EQUAL(0u, f.getOwner()._syncCnt);
    f.basicReconfig(10);
    EXPECT_EQUAL(1u, f.getOwner()._syncCnt);
}

TEST_F("require that owner is noticed when feed view changes", StoreOnlyFixture)
{
    requireThatOwnerIsNotifiedWhenFeedViewChanges(f);
}

TEST_F("require that owner is noticed when feed view changes", FastAccessFixture)
{
    requireThatOwnerIsNotifiedWhenFeedViewChanges(f);
}

TEST_F("require that owner is noticed when feed view changes", SearchableFixture)
{
    EXPECT_EQUAL(1u, f.getOwner()._syncCnt); // NOTE: init also notifies owner
    f.basicReconfig(10);
    EXPECT_EQUAL(2u, f.getOwner()._syncCnt);
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

typedef IFlushTarget::Type FType;
typedef IFlushTarget::Component FComponent;

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
    EXPECT_EQUAL(7u, targets.size());
    EXPECT_EQUAL("subdb.attribute.flush.attr1", targets[0]->getName());
    EXPECT_EQUAL("subdb.attribute.shrink.attr1", targets[1]->getName());
    EXPECT_EQUAL("subdb.documentmetastore.flush", targets[2]->getName());
    EXPECT_EQUAL("subdb.documentmetastore.shrink", targets[3]->getName());
    EXPECT_EQUAL("subdb.summary.compact", targets[4]->getName());
    EXPECT_EQUAL("subdb.summary.flush", targets[5]->getName());
    EXPECT_EQUAL("subdb.summary.shrink", targets[6]->getName());
}

TEST_F("require that flush targets can be retrieved", SearchableFixture)
{
    IFlushTarget::List targets = getFlushTargets(f);
    EXPECT_EQUAL(9u, targets.size());
    EXPECT_TRUE(assertTarget("subdb.attribute.flush.attr1", FType::SYNC, FComponent::ATTRIBUTE, *targets[0]));
    EXPECT_TRUE(assertTarget("subdb.attribute.shrink.attr1", FType::GC, FComponent::ATTRIBUTE, *targets[1]));
    EXPECT_TRUE(assertTarget("subdb.documentmetastore.flush", FType::SYNC, FComponent::ATTRIBUTE, *targets[2]));
    EXPECT_TRUE(assertTarget("subdb.documentmetastore.shrink", FType::GC, FComponent::ATTRIBUTE, *targets[3]));
    EXPECT_TRUE(assertTarget("subdb.memoryindex.flush", FType::FLUSH, FComponent::INDEX, *targets[4]));
    EXPECT_TRUE(assertTarget("subdb.memoryindex.fusion", FType::GC, FComponent::INDEX, *targets[5]));
    EXPECT_TRUE(assertTarget("subdb.summary.compact", FType::GC, FComponent::DOCUMENT_STORE, *targets[6]));
    EXPECT_TRUE(assertTarget("subdb.summary.flush", FType::SYNC, FComponent::DOCUMENT_STORE, *targets[7]));
    EXPECT_TRUE(assertTarget("subdb.summary.shrink", FType::GC, FComponent::DOCUMENT_STORE, *targets[8]));
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
    DocumentHandler(FixtureType &f) : _f(f), _builder(f._baseSchema) {}
    static constexpr uint32_t BUCKET_USED_BITS = 8;
    static DocumentId createDocId(uint32_t docId)
    {
        return DocumentId(vespalib::make_string("id:searchdocument:"
                                                "searchdocument::%u", docId));
    }
    Document::UP createEmptyDoc(uint32_t docId) {
        return _builder.startDocument
            (vespalib::make_string("id:searchdocument:searchdocument::%u",
                                   docId)).
            endDocument();
    }
    Document::UP createDoc(uint32_t docId, int64_t attr1Value, int64_t attr2Value) {
        return _builder.startDocument
                (vespalib::make_string("id:searchdocument:searchdocument::%u", docId)).
                startAttributeField("attr1").addInt(attr1Value).endField().
                startAttributeField("attr2").addInt(attr2Value).endField().endDocument();
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
    RemoveOperation createRemove(const DocumentId &docId, Timestamp timestamp, SerialNum serialNum)
    {
        const document::GlobalId &gid = docId.getGlobalId();
        BucketId bucket = gid.convertToBucketId();
        bucket.setUsedBits(BUCKET_USED_BITS);
        bucket = bucket.stripUnused();
        RemoveOperation op(bucket, timestamp, docId);
        op.setSerialNum(serialNum);
        return op;
    }
    void putDoc(PutOperation &op) {
        IFeedView::SP feedView = _f._subDb.getFeedView();
        _f.runInMaster([&]() {    feedView->preparePut(op);
                                  feedView->handlePut(FeedToken(), op); } );
    }
    void moveDoc(MoveOperation &op) {
        IFeedView::SP feedView = _f._subDb.getFeedView();
        _f.runInMaster([&]() {    feedView->handleMove(op, IDestructorCallback::SP()); } );
    }
    void removeDoc(RemoveOperation &op)
    {
        IFeedView::SP feedView = _f._subDb.getFeedView();
        _f.runInMaster([&]() {    feedView->prepareRemove(op);
                                  feedView->handleRemove(FeedToken(), op); } );
    }
    void putDocs() {
        PutOperation putOp = createPut(std::move(createDoc(1, 22, 33)), Timestamp(10), 10);
        putDoc(putOp);
        putOp = createPut(std::move(createDoc(2, 44, 55)), Timestamp(20), 20);
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
    assertAttribute(attr, "attr1", 3, 22, 44, createSerialNum, lastSerialNum);
}

void
assertAttribute2(const AttributeGuard &attr, SerialNum createSerialNum, SerialNum lastSerialNum)
{
    assertAttribute(attr, "attr2", 3, 33, 55, createSerialNum, lastSerialNum);
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
    f.reconfig(40u, TwoAttrSchema(), ConfigDirT::dir());

    {
        std::vector<AttributeGuard> attrs;
        f.getAttributeManager()->getAttributeList(attrs);
        EXPECT_EQUAL(2u, attrs.size());
        assertAttribute1(attrs[0], CFG_SERIAL, 40);
        assertAttribute2(attrs[1], 40, 40);
    }
}

TEST_F("require that fast-access attributes are populated during reprocessing",
        FastAccessOnlyFixture)
{
    requireThatAttributesArePopulatedDuringReprocessing<FastAccessOnlyFixture, ConfigDir4>(f);
}

// Setup with 2 fields (1 attribute according to config in dir)
typedef SearchableTraitsBase<TwoAttrSchema, ConfigDir1> SearchableTraitsTwoField;
typedef FixtureBase<SearchableTraitsTwoField> SearchableFixtureTwoField;

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
    RemoveOperation rmOp;
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

typedef ExplorerFixture<StoreOnlyFixture> StoreOnlyExplorerFixture;
typedef ExplorerFixture<FastAccessFixture> FastAccessExplorerFixture;
typedef ExplorerFixture<SearchableFixture> SearchableExplorerFixture;
typedef std::vector<vespalib::string> StringVector;

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
