// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/testkit/testapp.h>

#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastore.h>
#include <vespa/searchcore/proton/documentmetastore/lidreusedelayer.h>
#include <vespa/searchcore/proton/matching/error_constant_value.h>
#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/searchcore/proton/index/indexmanager.h>
#include <vespa/searchcore/proton/reprocessing/attribute_reprocessing_initializer.h>
#include <vespa/searchcore/proton/server/attribute_writer_factory.h>
#include <vespa/searchcore/proton/server/documentdbconfigmanager.h>
#include <vespa/searchcore/proton/server/searchable_doc_subdb_configurer.h>
#include <vespa/searchcore/proton/server/executorthreadingservice.h>
#include <vespa/searchcore/proton/server/fast_access_doc_subdb_configurer.h>
#include <vespa/searchcore/proton/server/summaryadapter.h>
#include <vespa/searchcore/proton/server/reconfig_params.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/searchcore/proton/test/mock_summary_adapter.h>
#include <vespa/searchcore/proton/test/mock_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/reference/dummy_gid_to_lid_change_handler.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/vespalib/io/fileutil.h>

using namespace config;
using namespace document;
using namespace proton;
using namespace proton::matching;
using namespace search::grouping;
using namespace search::index;
using namespace search::queryeval;
using namespace search;
using namespace vespa::config::search::core;
using namespace vespa::config::search::summary;
using namespace vespa::config::search;
using namespace vespalib;

using fastos::TimeStamp;
using proton::matching::SessionManager;
using searchcorespi::IndexSearchable;
using searchcorespi::index::IThreadingService;
using proton::test::MockGidToLidChangeHandler;
using std::make_shared;

using CCR = DocumentDBConfig::ComparisonResult;
using Configurer = SearchableDocSubDBConfigurer;
using ConfigurerUP = std::unique_ptr<SearchableDocSubDBConfigurer>;
using SummarySetup = SummaryManager::SummarySetup;
using DocumenttypesConfigSP = proton::DocumentDBConfig::DocumenttypesConfigSP;

const vespalib::string BASE_DIR("baseDir");
const vespalib::string DOC_TYPE("invalid");

class IndexManagerDummyReconfigurer : public searchcorespi::IIndexManager::Reconfigurer
{
    virtual bool reconfigure(vespalib::Closure0<bool>::UP closure) override {
        bool ret = true;
        if (closure.get() != NULL)
            ret = closure->call(); // Perform index manager reconfiguration now
        return ret;
    }
};

std::shared_ptr<const DocumentTypeRepo>
createRepo()
{
    DocumentType docType(DOC_TYPE, 0);
    return std::shared_ptr<const DocumentTypeRepo>(new DocumentTypeRepo(docType));
}

struct ViewPtrs
{
    SearchView::SP sv;
    SearchableFeedView::SP fv;
    ~ViewPtrs();
};

ViewPtrs::~ViewPtrs() {}

struct ViewSet
{
    IndexManagerDummyReconfigurer _reconfigurer;
    DummyFileHeaderContext _fileHeaderContext;
    ExecutorThreadingService _writeService;
    SearchableFeedView::SerialNum serialNum;
    std::shared_ptr<const DocumentTypeRepo> repo;
    DocTypeName _docTypeName;
    DocIdLimit _docIdLimit;
    search::transactionlog::NoSyncProxy _noTlSyncer;
    ISummaryManager::SP _summaryMgr;
    proton::IDocumentMetaStoreContext::SP _dmsc;
    std::shared_ptr<IGidToLidChangeHandler> _gidToLidChangeHandler;
    std::unique_ptr<documentmetastore::ILidReuseDelayer> _lidReuseDelayer;
    CommitTimeTracker _commitTimeTracker;
    VarHolder<SearchView::SP> searchView;
    VarHolder<SearchableFeedView::SP> feedView;
    HwInfo _hwInfo;
    ViewSet();
    ~ViewSet();

    ViewPtrs getViewPtrs() {
        ViewPtrs ptrs;
        ptrs.sv = searchView.get();
        ptrs.fv = feedView.get();
        return ptrs;
    }
};


ViewSet::ViewSet()
    : _reconfigurer(),
      _fileHeaderContext(),
      _writeService(),
      serialNum(1),
      repo(createRepo()),
      _docTypeName(DOC_TYPE),
      _docIdLimit(0u),
      _noTlSyncer(),
      _summaryMgr(),
      _dmsc(),
      _gidToLidChangeHandler(),
      _lidReuseDelayer(),
      _commitTimeTracker(TimeStamp()),
      searchView(),
      feedView(),
      _hwInfo()
{ }
ViewSet::~ViewSet() {}

struct EmptyConstantValueFactory : public vespalib::eval::ConstantValueFactory {
    virtual vespalib::eval::ConstantValue::UP create(const vespalib::string &, const vespalib::string &) const override {
        return std::make_unique<ErrorConstantValue>();
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

struct Fixture
{
    vespalib::Clock _clock;
    matching::QueryLimiter _queryLimiter;
    EmptyConstantValueFactory _constantValueFactory;
    ConstantValueRepo _constantValueRepo;
    vespalib::ThreadStackExecutor _summaryExecutor;
    ViewSet _views;
    MyDocumentDBReferenceResolver _resolver;
    ConfigurerUP _configurer;
    Fixture();
    ~Fixture();
    void initViewSet(ViewSet &views);
};

Fixture::Fixture()
    : _clock(),
      _queryLimiter(),
      _constantValueFactory(),
      _constantValueRepo(_constantValueFactory),
      _summaryExecutor(8, 128*1024),
      _views(),
      _resolver(),
      _configurer()
{
    vespalib::rmdir(BASE_DIR, true);
    vespalib::mkdir(BASE_DIR);
    initViewSet(_views);
    _configurer.reset(new Configurer(_views._summaryMgr, _views.searchView, _views.feedView, _queryLimiter,
                                     _constantValueRepo, _clock, "test", 0));
}
Fixture::~Fixture() {}

void
Fixture::initViewSet(ViewSet &views)
{
    Matchers::SP matchers(new Matchers(_clock, _queryLimiter, _constantValueRepo));
    auto indexMgr = make_shared<IndexManager>(BASE_DIR, searchcorespi::index::WarmupConfig(), 2, 0, Schema(), 1,
                                              views._reconfigurer, views._writeService, _summaryExecutor,
                                              TuneFileIndexManager(), TuneFileAttributes(), views._fileHeaderContext);
    auto attrMgr = make_shared<AttributeManager>(BASE_DIR, "test.subdb", TuneFileAttributes(), views._fileHeaderContext,
                                                 views._writeService.attributeFieldWriter(),views._hwInfo);
    ProtonConfig protonCfg;
    auto summaryMgr = make_shared<SummaryManager>
            (_summaryExecutor, search::LogDocumentStore::Config(), GrowStrategy(), BASE_DIR, views._docTypeName,
             TuneFileSummary(), views._fileHeaderContext,views._noTlSyncer, search::IBucketizer::SP());
    auto sesMgr = make_shared<SessionManager>(protonCfg.grouping.sessionmanager.maxentries);
    auto metaStore = make_shared<DocumentMetaStoreContext>(make_shared<BucketDBOwner>());
    IIndexWriter::SP indexWriter(new IndexWriter(indexMgr));
    AttributeWriter::SP attrWriter(new AttributeWriter(attrMgr));
    ISummaryAdapter::SP summaryAdapter(new SummaryAdapter(summaryMgr));
    views._gidToLidChangeHandler = make_shared<MockGidToLidChangeHandler>();
    Schema::SP schema(new Schema());
    views._summaryMgr = summaryMgr;
    views._dmsc = metaStore;
    views._lidReuseDelayer.reset(new documentmetastore::LidReuseDelayer(views._writeService, metaStore->get()));
    IndexSearchable::SP indexSearchable;
    MatchView::SP matchView(new MatchView(matchers, indexSearchable, attrMgr, sesMgr, metaStore, views._docIdLimit));
    views.searchView.set(make_shared<SearchView>
                                 (summaryMgr->createSummarySetup(SummaryConfig(), SummarymapConfig(),
                                                                 JuniperrcConfig(), views.repo, attrMgr),
                                  matchView));
    views.feedView.set(
            make_shared<SearchableFeedView>(StoreOnlyFeedView::Context(summaryAdapter,
                            schema,
                            views.searchView.get()->getDocumentMetaStore(),
                            *views._gidToLidChangeHandler,
                            views.repo,
                            views._writeService,
                            *views._lidReuseDelayer,
                            views._commitTimeTracker),
                            SearchableFeedView::PersistentParams(
                                    views.serialNum,
                                    views.serialNum,
                                    views._docTypeName,
                                    0u /* subDbId */,
                                    SubDbType::READY),
                            FastAccessFeedView::Context(attrWriter, views._docIdLimit),
                            SearchableFeedView::Context(indexWriter)));
}


using MySummaryAdapter = test::MockSummaryAdapter;

struct MyFastAccessFeedView
{
    DummyFileHeaderContext _fileHeaderContext;
    DocIdLimit _docIdLimit;
    IThreadingService &_writeService;
    HwInfo _hwInfo;

    proton::IDocumentMetaStoreContext::SP _dmsc;
    std::shared_ptr<IGidToLidChangeHandler> _gidToLidChangeHandler;
    std::unique_ptr<documentmetastore::ILidReuseDelayer> _lidReuseDelayer;
    CommitTimeTracker                 _commitTimeTracker;
    VarHolder<FastAccessFeedView::SP> _feedView;

    MyFastAccessFeedView(IThreadingService &writeService)
        : _fileHeaderContext(),
          _docIdLimit(0),
          _writeService(writeService),
          _hwInfo(),
          _dmsc(),
          _gidToLidChangeHandler(make_shared<DummyGidToLidChangeHandler>()),
          _lidReuseDelayer(),
          _commitTimeTracker(TimeStamp()),
          _feedView()
    {
        init();
    }

    ~MyFastAccessFeedView();

    void init() {
        ISummaryAdapter::SP summaryAdapter(new MySummaryAdapter());
        Schema::SP schema(new Schema());
        _dmsc = make_shared<DocumentMetaStoreContext>(std::make_shared<BucketDBOwner>());
        _lidReuseDelayer.reset(new documentmetastore::LidReuseDelayer(_writeService, _dmsc->get()));
        std::shared_ptr<const DocumentTypeRepo> repo = createRepo();
        StoreOnlyFeedView::Context storeOnlyCtx(summaryAdapter, schema, _dmsc, *_gidToLidChangeHandler, repo,
                                                _writeService, *_lidReuseDelayer, _commitTimeTracker);
        StoreOnlyFeedView::PersistentParams params(1, 1, DocTypeName(DOC_TYPE), 0, SubDbType::NOTREADY);
        auto mgr = make_shared<AttributeManager>(BASE_DIR, "test.subdb", TuneFileAttributes(), _fileHeaderContext,
                                                 _writeService.attributeFieldWriter(), _hwInfo);
        IAttributeWriter::SP writer(new AttributeWriter(mgr));
        FastAccessFeedView::Context fastUpdateCtx(writer, _docIdLimit);
        _feedView.set(FastAccessFeedView::SP(new FastAccessFeedView(storeOnlyCtx, params, fastUpdateCtx)));;
    }
};

MyFastAccessFeedView::~MyFastAccessFeedView() = default;

struct FastAccessFixture
{
    ExecutorThreadingService _writeService;
    MyFastAccessFeedView _view;
    FastAccessDocSubDBConfigurer _configurer;
    FastAccessFixture()
        : _writeService(),
          _view(_writeService),
          _configurer(_view._feedView, IAttributeWriterFactory::UP(new AttributeWriterFactory), "test")
    {
        vespalib::rmdir(BASE_DIR, true);
        vespalib::mkdir(BASE_DIR);
    }
    ~FastAccessFixture() {
        _writeService.sync();
    }
};

DocumentDBConfig::SP
createConfig()
{
    return test::DocumentDBConfigBuilder(0, make_shared<Schema>(), "client", DOC_TYPE).
            repo(createRepo()).build();
}

DocumentDBConfig::SP
createConfig(const Schema::SP &schema)
{
    return test::DocumentDBConfigBuilder(0, schema, "client", DOC_TYPE).
            repo(createRepo()).build();
}

struct SearchViewComparer
{
    SearchView::SP _old;
    SearchView::SP _new;
    SearchViewComparer(SearchView::SP old, SearchView::SP new_);
    ~SearchViewComparer();
    void expect_equal() {
        EXPECT_EQUAL(_old.get(), _new.get());
    }
    void expect_not_equal() {
        EXPECT_NOT_EQUAL(_old.get(), _new.get());
    }
    void expect_equal_summary_setup() {
        EXPECT_EQUAL(_old->getSummarySetup().get(), _new->getSummarySetup().get());
    }
    void expect_not_equal_summary_setup() {
        EXPECT_NOT_EQUAL(_old->getSummarySetup().get(), _new->getSummarySetup().get());
    }
    void expect_equal_match_view() {
        EXPECT_EQUAL(_old->getMatchView().get(), _new->getMatchView().get());
    }
    void expect_not_equal_match_view() {
        EXPECT_NOT_EQUAL(_old->getMatchView().get(), _new->getMatchView().get());
    }
    void expect_equal_matchers() {
        EXPECT_EQUAL(_old->getMatchers().get(), _new->getMatchers().get());
    }
    void expect_not_equal_matchers() {
        EXPECT_NOT_EQUAL(_old->getMatchers().get(), _new->getMatchers().get());
    }
    void expect_equal_index_searchable() {
        EXPECT_EQUAL(_old->getIndexSearchable().get(), _new->getIndexSearchable().get());
    }
    void expect_not_equal_index_searchable() {
        EXPECT_NOT_EQUAL(_old->getIndexSearchable().get(), _new->getIndexSearchable().get());
    }
    void expect_equal_attribute_manager() {
        EXPECT_EQUAL(_old->getAttributeManager().get(), _new->getAttributeManager().get());
    }
    void expect_not_equal_attribute_manager() {
        EXPECT_NOT_EQUAL(_old->getAttributeManager().get(), _new->getAttributeManager().get());
    }
    void expect_equal_session_manager() {
        EXPECT_EQUAL(_old->getSessionManager().get(), _new->getSessionManager().get());
    }
    void expect_equal_document_meta_store() {
        EXPECT_EQUAL(_old->getDocumentMetaStore().get(), _new->getDocumentMetaStore().get());
    }
};

SearchViewComparer::SearchViewComparer(SearchView::SP old, SearchView::SP new_)
    : _old(std::move(old)),
      _new(std::move(new_))
{}
SearchViewComparer::~SearchViewComparer() {}


struct FeedViewComparer
{
    SearchableFeedView::SP _old;
    SearchableFeedView::SP _new;
    FeedViewComparer(SearchableFeedView::SP old, SearchableFeedView::SP new_);
    ~FeedViewComparer();
    void expect_equal() {
        EXPECT_EQUAL(_old.get(), _new.get());
    }
    void expect_not_equal() {
        EXPECT_NOT_EQUAL(_old.get(), _new.get());
    }
    void expect_equal_index_adapter() {
        EXPECT_EQUAL(_old->getIndexWriter().get(), _new->getIndexWriter().get());
    }
    void expect_equal_attribute_writer() {
        EXPECT_EQUAL(_old->getAttributeWriter().get(), _new->getAttributeWriter().get());
    }
    void expect_not_equal_attribute_writer() {
        EXPECT_NOT_EQUAL(_old->getAttributeWriter().get(), _new->getAttributeWriter().get());
    }
    void expect_equal_summary_adapter() {
        EXPECT_EQUAL(_old->getSummaryAdapter().get(), _new->getSummaryAdapter().get());
    }
    void expect_equal_schema() {
        EXPECT_EQUAL(_old->getSchema().get(), _new->getSchema().get());
    }
    void expect_not_equal_schema() {
        EXPECT_NOT_EQUAL(_old->getSchema().get(), _new->getSchema().get());
    }
};

FeedViewComparer::FeedViewComparer(SearchableFeedView::SP old, SearchableFeedView::SP new_)
    : _old(std::move(old)),
      _new(std::move(new_))
{}
FeedViewComparer::~FeedViewComparer() {}

struct FastAccessFeedViewComparer
{
    FastAccessFeedView::SP _old;
    FastAccessFeedView::SP _new;
    FastAccessFeedViewComparer(FastAccessFeedView::SP old, FastAccessFeedView::SP new_);
    ~FastAccessFeedViewComparer();
    void expect_not_equal() {
        EXPECT_NOT_EQUAL(_old.get(), _new.get());
    }
    void expect_not_equal_attribute_writer() {
        EXPECT_NOT_EQUAL(_old->getAttributeWriter().get(), _new->getAttributeWriter().get());
    }
    void expect_equal_summary_adapter() {
        EXPECT_EQUAL(_old->getSummaryAdapter().get(), _new->getSummaryAdapter().get());
    }
    void expect_not_equal_schema() {
        EXPECT_NOT_EQUAL(_old->getSchema().get(), _new->getSchema().get());
    }
};

FastAccessFeedViewComparer::FastAccessFeedViewComparer(FastAccessFeedView::SP old, FastAccessFeedView::SP new_)
    : _old(std::move(old)),
      _new(std::move(new_))
{}
FastAccessFeedViewComparer::~FastAccessFeedViewComparer() {}

TEST_F("require that we can reconfigure index searchable", Fixture)
{
    ViewPtrs o = f._views.getViewPtrs();
    f._configurer->reconfigureIndexSearchable();

    ViewPtrs n = f._views.getViewPtrs();
    { // verify search view
        SearchViewComparer cmp(o.sv, n.sv);
        cmp.expect_not_equal();
        cmp.expect_equal_summary_setup();
        cmp.expect_not_equal_match_view();
        cmp.expect_equal_matchers();
        cmp.expect_not_equal_index_searchable();
        cmp.expect_equal_attribute_manager();
        cmp.expect_equal_session_manager();
        cmp.expect_equal_document_meta_store();
    }
    { // verify feed view
        FeedViewComparer cmp(o.fv, n.fv);
        cmp.expect_not_equal();
        cmp.expect_equal_index_adapter();
        cmp.expect_equal_attribute_writer();
        cmp.expect_equal_summary_adapter();
        cmp.expect_equal_schema();
    }
}

const AttributeManager *
asAttributeManager(const proton::IAttributeManager::SP &attrMgr)
{
    const AttributeManager *result = dynamic_cast<const AttributeManager *>(attrMgr.get());
    ASSERT_TRUE(result != nullptr);
    return result;
}

TEST_F("require that we can reconfigure attribute manager", Fixture)
{
    ViewPtrs o = f._views.getViewPtrs();
    AttributeCollectionSpec::AttributeList specList;
    AttributeCollectionSpec spec(specList, 1, 0);
    ReconfigParams params(CCR().setAttributesChanged(true).setSchemaChanged(true));
    // Use new config snapshot == old config snapshot (only relevant for reprocessing)
    f._configurer->reconfigure(*createConfig(), *createConfig(), spec, params, f._resolver);

    ViewPtrs n = f._views.getViewPtrs();
    { // verify search view
        SearchViewComparer cmp(o.sv, n.sv);
        cmp.expect_not_equal();
        cmp.expect_not_equal_summary_setup();
        cmp.expect_not_equal_match_view();
        cmp.expect_not_equal_matchers();
        cmp.expect_equal_index_searchable();
        cmp.expect_not_equal_attribute_manager();
        cmp.expect_equal_session_manager();
        cmp.expect_equal_document_meta_store();
    }
    { // verify feed view
        FeedViewComparer cmp(o.fv, n.fv);
        cmp.expect_not_equal();
        cmp.expect_equal_index_adapter();
        cmp.expect_not_equal_attribute_writer();
        cmp.expect_equal_summary_adapter();
        cmp.expect_not_equal_schema();
    }
    EXPECT_TRUE(asAttributeManager(f._views.getViewPtrs().fv.get()->getAttributeWriter()->getAttributeManager())->getImportedAttributes() != nullptr);
}

AttributeWriter::SP
getAttributeWriter(Fixture &f)
{
    return f._views.feedView.get()->getAttributeWriter();
}

void
checkAttributeWriterChangeOnRepoChange(Fixture &f, bool docTypeRepoChanged)
{
    auto oldAttributeWriter = getAttributeWriter(f);
    AttributeCollectionSpec::AttributeList specList;
    AttributeCollectionSpec spec(specList, 1, 0);
    ReconfigParams params(CCR().setDocumentTypeRepoChanged(docTypeRepoChanged));
    // Use new config snapshot == old config snapshot (only relevant for reprocessing)
    f._configurer->reconfigure(*createConfig(), *createConfig(), spec, params, f._resolver);
    auto newAttributeWriter = getAttributeWriter(f);
    if (docTypeRepoChanged) {
        EXPECT_NOT_EQUAL(oldAttributeWriter, newAttributeWriter);
    } else {
        EXPECT_EQUAL(oldAttributeWriter, newAttributeWriter);
    }
}

TEST_F("require that we get new attribute writer if document type repo changes", Fixture)
{
    checkAttributeWriterChangeOnRepoChange(f, false);
    checkAttributeWriterChangeOnRepoChange(f, true);
}

TEST_F("require that reconfigure returns reprocessing initializer when changing attributes", Fixture)
{
    AttributeCollectionSpec::AttributeList specList;
    AttributeCollectionSpec spec(specList, 1, 0);
    ReconfigParams params(CCR().setAttributesChanged(true).setSchemaChanged(true));
    IReprocessingInitializer::UP init =
            f._configurer->reconfigure(*createConfig(), *createConfig(), spec, params, f._resolver);

    EXPECT_TRUE(init.get() != nullptr);
    EXPECT_TRUE((dynamic_cast<AttributeReprocessingInitializer *>(init.get())) != nullptr);
    EXPECT_FALSE(init->hasReprocessors());
}

TEST_F("require that we can reconfigure attribute writer", FastAccessFixture)
{
    AttributeCollectionSpec::AttributeList specList;
    AttributeCollectionSpec spec(specList, 1, 0);
    FastAccessFeedView::SP o = f._view._feedView.get();
    f._configurer.reconfigure(*createConfig(), *createConfig(), spec);
    FastAccessFeedView::SP n = f._view._feedView.get();

    FastAccessFeedViewComparer cmp(o, n);
    cmp.expect_not_equal();
    cmp.expect_not_equal_attribute_writer();
    cmp.expect_equal_summary_adapter();
    cmp.expect_not_equal_schema();
}

TEST_F("require that reconfigure returns reprocessing initializer", FastAccessFixture)
{
    AttributeCollectionSpec::AttributeList specList;
    AttributeCollectionSpec spec(specList, 1, 0);
    IReprocessingInitializer::UP init =
            f._configurer.reconfigure(*createConfig(), *createConfig(), spec);

    EXPECT_TRUE(init.get() != nullptr);
    EXPECT_TRUE((dynamic_cast<AttributeReprocessingInitializer *>(init.get())) != nullptr);
    EXPECT_FALSE(init->hasReprocessors());
}

TEST_F("require that we can reconfigure summary manager", Fixture)
{
    ViewPtrs o = f._views.getViewPtrs();
    ReconfigParams params(CCR().setSummarymapChanged(true));
    // Use new config snapshot == old config snapshot (only relevant for reprocessing)
    f._configurer->reconfigure(*createConfig(), *createConfig(), params, f._resolver);

    ViewPtrs n = f._views.getViewPtrs();
    { // verify search view
        SearchViewComparer cmp(o.sv, n.sv);
        cmp.expect_not_equal();
        cmp.expect_not_equal_summary_setup();
        cmp.expect_equal_match_view();
    }
    { // verify feed view
        FeedViewComparer cmp(o.fv, n.fv);
        cmp.expect_equal();
    }
}

TEST_F("require that we can reconfigure matchers", Fixture)
{
    ViewPtrs o = f._views.getViewPtrs();
    // Use new config snapshot == old config snapshot (only relevant for reprocessing)
    f._configurer->reconfigure(*createConfig(o.fv->getSchema()), *createConfig(o.fv->getSchema()),
            ReconfigParams(CCR().setRankProfilesChanged(true)), f._resolver);

    ViewPtrs n = f._views.getViewPtrs();
    { // verify search view
        SearchViewComparer cmp(o.sv, n.sv);
        cmp.expect_not_equal();
        cmp.expect_equal_summary_setup();
        cmp.expect_not_equal_match_view();
        cmp.expect_not_equal_matchers();
        cmp.expect_equal_index_searchable();
        cmp.expect_equal_attribute_manager();
        cmp.expect_equal_session_manager();
        cmp.expect_equal_document_meta_store();
    }
    { // verify feed view
        FeedViewComparer cmp(o.fv, n.fv);
        cmp.expect_not_equal();
        cmp.expect_equal_index_adapter();
        cmp.expect_equal_attribute_writer();
        cmp.expect_equal_summary_adapter();
        cmp.expect_equal_schema();
    }
}

TEST("require that attribute manager (imported attributes) should change when imported fields has changed")
{
    ReconfigParams params(CCR().setImportedFieldsChanged(true));
    EXPECT_TRUE(params.shouldAttributeManagerChange());
}

TEST("require that attribute manager (imported attributes) should change when visibility delay has changed")
{
    ReconfigParams params(CCR().setVisibilityDelayChanged(true));
    EXPECT_TRUE(params.shouldAttributeManagerChange());
}

void
assertMaintenanceControllerShouldNotChange(DocumentDBConfig::ComparisonResult result)
{
    ReconfigParams params(result);
    EXPECT_FALSE(params.configHasChanged());
    EXPECT_FALSE(params.shouldMaintenanceControllerChange());
}

void
assertMaintenanceControllerShouldChange(DocumentDBConfig::ComparisonResult result)
{
    ReconfigParams params(result);
    EXPECT_TRUE(params.configHasChanged());
    EXPECT_TRUE(params.shouldMaintenanceControllerChange());
}

TEST("require that maintenance controller should change if some config has changed")
{
    TEST_DO(assertMaintenanceControllerShouldNotChange(CCR()));

    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setRankProfilesChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setRankingConstantsChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setIndexschemaChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setAttributesChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setSummaryChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setSummarymapChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setJuniperrcChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setDocumenttypesChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setDocumentTypeRepoChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setImportedFieldsChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setTuneFileDocumentDBChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setSchemaChanged(true)));
    TEST_DO(assertMaintenanceControllerShouldChange(CCR().setMaintenanceChanged(true)));
}

void
assertSubDbsShouldNotChange(DocumentDBConfig::ComparisonResult result)
{
    ReconfigParams params(result);
    EXPECT_FALSE(params.configHasChanged());
    EXPECT_FALSE(params.shouldSubDbsChange());
}

void
assertSubDbsShouldChange(DocumentDBConfig::ComparisonResult result)
{
    ReconfigParams params(result);
    EXPECT_TRUE(params.configHasChanged());
    EXPECT_TRUE(params.shouldSubDbsChange());
}


TEST("require that subdbs should change if relevant config changed")
{
    TEST_DO(assertSubDbsShouldNotChange(CCR()));
    EXPECT_FALSE(ReconfigParams(CCR().setMaintenanceChanged(true)).shouldSubDbsChange());
    TEST_DO(assertSubDbsShouldChange(CCR().setFlushChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setStoreChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setDocumenttypesChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setDocumentTypeRepoChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setSummaryChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setSummarymapChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setJuniperrcChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setAttributesChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setImportedFieldsChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setVisibilityDelayChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setRankProfilesChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setRankingConstantsChanged(true)));
    TEST_DO(assertSubDbsShouldChange(CCR().setSchemaChanged(true)));
}

TEST_MAIN()
{
    TEST_RUN_ALL();
    vespalib::rmdir(BASE_DIR, true);
}
