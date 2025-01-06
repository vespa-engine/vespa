// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/config-summary.h>
#include <vespa/document/datatype/documenttype.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/eval/eval/value_cache/constant_value.h>
#include <vespa/searchcore/proton/attribute/attribute_collection_spec_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/attributemanager.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/bucketdb/bucket_db_owner.h>
#include <vespa/searchcore/proton/common/docid_limit.h>
#include <vespa/searchcore/proton/common/pendinglidtracker.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/documentmetastore/documentmetastorecontext.h>
#include <vespa/searchcore/proton/index/index_writer.h>
#include <vespa/searchcore/proton/index/indexmanager.h>
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchcore/proton/matching/sessionmanager.h>
#include <vespa/searchcore/proton/reference/dummy_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reprocessing/attribute_reprocessing_initializer.h>
#include <vespa/searchcore/proton/server/document_subdb_reconfig.h>
#include <vespa/searchcore/proton/server/fast_access_doc_subdb_configurer.h>
#include <vespa/searchcore/proton/server/reconfig_params.h>
#include <vespa/searchcore/proton/server/searchable_doc_subdb_configurer.h>
#include <vespa/searchcore/proton/server/searchview.h>
#include <vespa/searchcore/proton/server/searchable_feed_view.h>
#include <vespa/searchcore/proton/server/summaryadapter.h>
#include <vespa/searchcore/proton/test/documentdb_config_builder.h>
#include <vespa/searchcore/proton/test/mock_gid_to_lid_change_handler.h>
#include <vespa/searchcore/proton/test/mock_summary_adapter.h>
#include <vespa/searchcore/proton/test/transport_helper.h>
#include <vespa/searchlib/attribute/interlock.h>
#include <vespa/searchlib/fef/ranking_assets_repo.h>
#include <vespa/searchlib/index/dummyfileheadercontext.h>
#include <vespa/searchlib/transactionlog/nosyncproxy.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <vespa/vespalib/util/testclock.h>
#include <vespa/vespalib/util/threadstackexecutor.h>
#include <filesystem>

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

using proton::matching::SessionManager;
using search::SerialNum;
using search::fef::RankingAssetsRepo;
using searchcorespi::IndexSearchable;
using searchcorespi::index::IThreadingService;
using proton::test::MockGidToLidChangeHandler;
using std::make_shared;

using CCR = DocumentDBConfig::ComparisonResult;
using Configurer = SearchableDocSubDBConfigurer;
using ConfigurerUP = std::unique_ptr<SearchableDocSubDBConfigurer>;
using DocumenttypesConfigSP = proton::DocumentDBConfig::DocumenttypesConfigSP;

namespace {
const std::string BASE_DIR("baseDir");
const std::string DOC_TYPE("invalid");

class IndexManagerDummyReconfigurer : public searchcorespi::IIndexManager::Reconfigurer
{
    bool reconfigure(std::unique_ptr<Configure> configure) override {
        bool ret = true;
        if (configure)
            ret = configure->configure(); // Perform index manager reconfiguration now
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
    std::shared_ptr<SearchView> sv;
    std::shared_ptr<SearchableFeedView> fv;
    ~ViewPtrs();
};

ViewPtrs::~ViewPtrs() = default;

struct ViewSet
{
    IndexManagerDummyReconfigurer _reconfigurer;
    DummyFileHeaderContext        _fileHeaderContext;
    TransportAndExecutorService   _service;
    SerialNum                     serialNum;
    std::shared_ptr<const DocumentTypeRepo> repo;
    DocTypeName _docTypeName;
    DocIdLimit _docIdLimit;
    search::transactionlog::NoSyncProxy _noTlSyncer;
    ISummaryManager::SP _summaryMgr;
    proton::IDocumentMetaStoreContext::SP _dmsc;
    std::shared_ptr<IGidToLidChangeHandler> _gidToLidChangeHandler;
    VarHolder<std::shared_ptr<SearchView>> searchView;
    VarHolder<std::shared_ptr<SearchableFeedView>> feedView;
    HwInfo _hwInfo;
    ViewSet();
    ~ViewSet();

    ViewPtrs getViewPtrs() const {
        ViewPtrs ptrs;
        ptrs.sv = searchView.get();
        ptrs.fv = feedView.get();
        return ptrs;
    }
};


ViewSet::ViewSet()
    : _reconfigurer(),
      _fileHeaderContext(),
      _service(1),
      serialNum(1),
      repo(createRepo()),
      _docTypeName(DOC_TYPE),
      _docIdLimit(0u),
      _noTlSyncer(),
      _summaryMgr(),
      _dmsc(),
      _gidToLidChangeHandler(),
      searchView(),
      feedView(),
      _hwInfo()
{ }
ViewSet::~ViewSet() = default;

struct EmptyConstantValueFactory : public vespalib::eval::ConstantValueFactory {
    vespalib::eval::ConstantValue::UP create(const std::string &, const std::string &) const override {
        return {};
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

struct Fixture
{
    vespalib::TestClock _clock;
    matching::QueryLimiter _queryLimiter;
    EmptyConstantValueFactory _constantValueFactory;
    vespalib::ThreadStackExecutor _summaryExecutor;
    std::shared_ptr<PendingLidTrackerBase> _pendingLidsForCommit;
    SessionManager _sessionMgr;
    ViewSet _views;
    MyDocumentDBReferenceResolver _resolver;
    ConfigurerUP _configurer;
    Fixture();
    ~Fixture();
    void initViewSet(ViewSet &views);
    void reconfigure(const DocumentDBConfig& new_config_snapshot,
                     const DocumentDBConfig& old_config_snapshot,
                     const ReconfigParams& reconfig_params,
                     IDocumentDBReferenceResolver& resolver,
                     SerialNum serial_num) const;
    IReprocessingInitializer::UP reconfigure(const DocumentDBConfig& new_config_snapshot,
                                             const DocumentDBConfig& old_config_snapshot,
                                             const ReconfigParams& reconfig_params,
                                             IDocumentDBReferenceResolver& resolver,
                                             uint32_t docid_limit,
                                             SerialNum serial_num) const;
};

Fixture::Fixture()
    : _clock(),
      _queryLimiter(),
      _constantValueFactory(),
      _summaryExecutor(8),
      _pendingLidsForCommit(std::make_shared<PendingLidTracker>()),
      _sessionMgr(100),
      _views(),
      _resolver(),
      _configurer()
{
    std::filesystem::remove_all(std::filesystem::path(BASE_DIR));
    std::filesystem::create_directory(std::filesystem::path(BASE_DIR));
    initViewSet(_views);
    _configurer = std::make_unique<Configurer>(_views._summaryMgr, _views.searchView, _views.feedView, _queryLimiter,
                                               _constantValueFactory, _clock.nowRef(), "test", 0);
}
Fixture::~Fixture() {
    std::filesystem::remove_all(std::filesystem::path(BASE_DIR));
}

void
Fixture::initViewSet(ViewSet &views)
{
    using IndexManager = proton::index::IndexManager;
    using IndexConfig = proton::index::IndexConfig;
    RankingAssetsRepo ranking_assets_repo_source(_constantValueFactory, {}, {}, {});
    auto matchers = std::make_shared<Matchers>(_clock.nowRef(), _queryLimiter, ranking_assets_repo_source);
    auto indexMgr = make_shared<IndexManager>(BASE_DIR, std::shared_ptr<search::diskindex::IPostingListCache>(),
                                              IndexConfig(searchcorespi::index::WarmupConfig(), 2), Schema(), 1,
                                              views._reconfigurer, views._service.write(), _summaryExecutor,
                                              TuneFileIndexManager(), TuneFileAttributes(), views._fileHeaderContext);
    auto attrMgr = make_shared<AttributeManager>(BASE_DIR, "test.subdb", TuneFileAttributes(),
                                                 views._fileHeaderContext, std::make_shared<search::attribute::Interlock>(),
                                                 views._service.write().field_writer(), views._service.write().shared(), views._hwInfo);
    auto summaryMgr = make_shared<SummaryManager>
            (_summaryExecutor, search::LogDocumentStore::Config(), search::GrowStrategy(), BASE_DIR,
             TuneFileSummary(), views._fileHeaderContext,views._noTlSyncer, search::IBucketizer::SP());
    auto metaStore = make_shared<DocumentMetaStoreContext>(make_shared<bucketdb::BucketDBOwner>());
    auto indexWriter = std::make_shared<IndexWriter>(indexMgr);
    auto attrWriter = std::make_shared<AttributeWriter>(attrMgr);
    auto summaryAdapter = std::make_shared<SummaryAdapter>(summaryMgr);
    views._gidToLidChangeHandler = make_shared<MockGidToLidChangeHandler>();
    auto schema(std::make_shared<const Schema>());
    views._summaryMgr = summaryMgr;
    views._dmsc = metaStore;
    IndexSearchable::SP indexSearchable;
    auto matchView = std::make_shared<MatchView>(matchers, indexSearchable, attrMgr, _sessionMgr, metaStore, views._docIdLimit);
    views.searchView.set(SearchView::create
                                 (summaryMgr->createSummarySetup(SummaryConfig(),
                                                                 JuniperrcConfig(), views.repo, attrMgr, *schema),
                                  std::move(matchView)));
    views.feedView.set(
            make_shared<SearchableFeedView>(StoreOnlyFeedView::Context(summaryAdapter,
                                                                       schema,
                                                                       views.searchView.get()->getDocumentMetaStore(),
                                                                       views.repo,
                                                                       _pendingLidsForCommit,
                                                                       *views._gidToLidChangeHandler,
                                                                       views._service.write()),
                                            SearchableFeedView::PersistentParams(views.serialNum, views.serialNum,
                                                                                 views._docTypeName, 0u, SubDbType::READY),
                                            FastAccessFeedView::Context(attrWriter, views._docIdLimit),
                                            SearchableFeedView::Context(indexWriter)));
}

void
Fixture::reconfigure(const DocumentDBConfig& new_config_snapshot,
                     const DocumentDBConfig& old_config_snapshot,
                     const ReconfigParams& reconfig_params,
                     IDocumentDBReferenceResolver& resolver,
                     SerialNum serial_num) const
{
    EXPECT_FALSE(reconfig_params.shouldAttributeManagerChange());
    uint32_t docid_limit = 1;
    AttributeCollectionSpecFactory attr_spec_factory(AllocStrategy(), false);
    auto prepared_reconfig = _configurer->prepare_reconfig(new_config_snapshot, attr_spec_factory, reconfig_params, docid_limit, serial_num);
    prepared_reconfig->complete(docid_limit, serial_num);
    _configurer->reconfigure(new_config_snapshot, old_config_snapshot, reconfig_params, resolver, *prepared_reconfig, serial_num);
}

IReprocessingInitializer::UP
Fixture::reconfigure(const DocumentDBConfig& new_config_snapshot,
                     const DocumentDBConfig& old_config_snapshot,
                     const ReconfigParams& reconfig_params,
                     IDocumentDBReferenceResolver& resolver,
                     uint32_t docid_limit,
                     SerialNum serial_num) const
{
    AttributeCollectionSpecFactory attr_spec_factory(AllocStrategy(), false);
    auto prepared_reconfig = _configurer->prepare_reconfig(new_config_snapshot, attr_spec_factory, reconfig_params, docid_limit, serial_num);
    prepared_reconfig->complete(docid_limit, serial_num);
    return _configurer->reconfigure(new_config_snapshot, old_config_snapshot, reconfig_params, resolver, *prepared_reconfig, serial_num);
}

using MySummaryAdapter = proton::test::MockSummaryAdapter;

struct MyFastAccessFeedView
{
    DummyFileHeaderContext _fileHeaderContext;
    DocIdLimit _docIdLimit;
    IThreadingService &_writeService;
    HwInfo _hwInfo;

    proton::IDocumentMetaStoreContext::SP _dmsc;
    std::shared_ptr<IGidToLidChangeHandler> _gidToLidChangeHandler;
    std::shared_ptr<PendingLidTrackerBase> _pendingLidsForCommit;
    VarHolder<FastAccessFeedView::SP> _feedView;

    explicit MyFastAccessFeedView(IThreadingService &writeService) __attribute__((noinline));
    ~MyFastAccessFeedView();

    void init() __attribute__((noinline));
};

MyFastAccessFeedView::MyFastAccessFeedView(IThreadingService &writeService)
    : _fileHeaderContext(),
      _docIdLimit(0),
      _writeService(writeService),
      _hwInfo(),
      _dmsc(),
      _gidToLidChangeHandler(make_shared<DummyGidToLidChangeHandler>()),
      _pendingLidsForCommit(std::make_shared<PendingLidTracker>()),
      _feedView()
{
    init();
}

MyFastAccessFeedView::~MyFastAccessFeedView() = default;

void
MyFastAccessFeedView::init() {
    MySummaryAdapter::SP summaryAdapter = std::make_shared<MySummaryAdapter>();
    auto schema = std::make_shared<const Schema>();
    _dmsc = make_shared<DocumentMetaStoreContext>(std::make_shared<bucketdb::BucketDBOwner>());
    std::shared_ptr<const DocumentTypeRepo> repo = createRepo();
    StoreOnlyFeedView::Context storeOnlyCtx(summaryAdapter, schema, _dmsc, repo,
                                            _pendingLidsForCommit, *_gidToLidChangeHandler, _writeService);
    StoreOnlyFeedView::PersistentParams params(1, 1, DocTypeName(DOC_TYPE), 0, SubDbType::NOTREADY);
    auto mgr = make_shared<AttributeManager>(BASE_DIR, "test.subdb", TuneFileAttributes(),
                                             _fileHeaderContext, std::make_shared<search::attribute::Interlock>(),
                                             _writeService.field_writer(), _writeService.shared(), _hwInfo);
    auto writer = std::make_shared<AttributeWriter>(mgr);
    FastAccessFeedView::Context fastUpdateCtx(writer, _docIdLimit);
    _feedView.set(std::make_shared<FastAccessFeedView>(std::move(storeOnlyCtx), params, fastUpdateCtx));
}

struct FastAccessFixture
{
    TransportAndExecutorService  _service;
    MyFastAccessFeedView          _view;
    FastAccessDocSubDBConfigurer _configurer;
    FastAccessFixture() __attribute__((noinline));
    ~FastAccessFixture() __attribute__((noinline));

    IReprocessingInitializer::UP
    reconfigure(const DocumentDBConfig& new_config_snapshot,
                const DocumentDBConfig& old_config_snapshot,
                uint32_t docid_limit,
                SerialNum serial_num);
};

FastAccessFixture::FastAccessFixture()
    : _service(1),
      _view(_service.write()),
      _configurer(_view._feedView, "test")
{
    std::filesystem::remove_all(std::filesystem::path(BASE_DIR));
    std::filesystem::create_directory(std::filesystem::path(BASE_DIR));
}
FastAccessFixture::~FastAccessFixture() {
    _service.shutdown();
    std::filesystem::remove_all(std::filesystem::path(BASE_DIR));
}


IReprocessingInitializer::UP
FastAccessFixture::reconfigure(const DocumentDBConfig& new_config_snapshot,
                               const DocumentDBConfig& old_config_snapshot,
                               uint32_t docid_limit,
                               SerialNum serial_num)
{
    ReconfigParams reconfig_params{CCR()};
    AttributeCollectionSpecFactory attr_spec_factory(AllocStrategy(), true);
    auto prepared_reconfig = _configurer.prepare_reconfig(new_config_snapshot, attr_spec_factory, reconfig_params, docid_limit, serial_num);
    prepared_reconfig->complete(docid_limit, serial_num);
    return _configurer.reconfigure(new_config_snapshot, old_config_snapshot, *prepared_reconfig, serial_num);
}

DocumentDBConfig::SP
createConfig()
{
    return proton::test::DocumentDBConfigBuilder(0, make_shared<Schema>(), "client", DOC_TYPE).
            repo(createRepo()).build();
}

DocumentDBConfig::SP
createConfig(std::shared_ptr<const Schema> schema)
{
    return proton::test::DocumentDBConfigBuilder(0, std::move(schema), "client", DOC_TYPE).
            repo(createRepo()).build();
}

struct SearchViewComparer
{
    SearchView::SP _old;
    SearchView::SP _new;
    SearchViewComparer(SearchView::SP old, SearchView::SP new_);
    ~SearchViewComparer();
    void expect_equal() {
        EXPECT_EQ(_old.get(), _new.get());
    }
    void expect_not_equal() {
        EXPECT_NE(_old.get(), _new.get());
    }
    void expect_equal_summary_setup() {
        EXPECT_EQ(_old->getSummarySetup().get(), _new->getSummarySetup().get());
    }
    void expect_not_equal_summary_setup() {
        EXPECT_NE(_old->getSummarySetup().get(), _new->getSummarySetup().get());
    }
    void expect_equal_match_view() {
        EXPECT_EQ(_old->getMatchView().get(), _new->getMatchView().get());
    }
    void expect_not_equal_match_view() {
        EXPECT_NE(_old->getMatchView().get(), _new->getMatchView().get());
    }
    void expect_equal_matchers() {
        EXPECT_EQ(_old->getMatchers().get(), _new->getMatchers().get());
    }
    void expect_not_equal_matchers() {
        EXPECT_NE(_old->getMatchers().get(), _new->getMatchers().get());
    }
    void expect_equal_index_searchable() {
        EXPECT_EQ(_old->getIndexSearchable().get(), _new->getIndexSearchable().get());
    }
    void expect_not_equal_index_searchable() {
        EXPECT_NE(_old->getIndexSearchable().get(), _new->getIndexSearchable().get());
    }
    void expect_equal_attribute_manager() {
        EXPECT_EQ(_old->getAttributeManager().get(), _new->getAttributeManager().get());
    }
    void expect_not_equal_attribute_manager() {
        EXPECT_NE(_old->getAttributeManager().get(), _new->getAttributeManager().get());
    }

    void expect_equal_document_meta_store() {
        EXPECT_EQ(_old->getDocumentMetaStore().get(), _new->getDocumentMetaStore().get());
    }
};

SearchViewComparer::SearchViewComparer(SearchView::SP old, SearchView::SP new_)
    : _old(std::move(old)),
      _new(std::move(new_))
{}
SearchViewComparer::~SearchViewComparer() = default;


struct FeedViewComparer
{
    SearchableFeedView::SP _old;
    SearchableFeedView::SP _new;
    FeedViewComparer(SearchableFeedView::SP old, SearchableFeedView::SP new_);
    ~FeedViewComparer();
    void expect_equal() {
        EXPECT_EQ(_old.get(), _new.get());
    }
    void expect_not_equal() {
        EXPECT_NE(_old.get(), _new.get());
    }
    void expect_equal_index_adapter() {
        EXPECT_EQ(_old->getIndexWriter().get(), _new->getIndexWriter().get());
    }
    void expect_not_equal_attribute_writer() {
        EXPECT_NE(_old->getAttributeWriter().get(), _new->getAttributeWriter().get());
    }
    void expect_equal_summary_adapter() {
        EXPECT_EQ(_old->getSummaryAdapter().get(), _new->getSummaryAdapter().get());
    }
    void expect_not_equal_schema() {
        EXPECT_NE(_old->getSchema().get(), _new->getSchema().get());
    }
};

FeedViewComparer::FeedViewComparer(SearchableFeedView::SP old, SearchableFeedView::SP new_)
    : _old(std::move(old)),
      _new(std::move(new_))
{}
FeedViewComparer::~FeedViewComparer() = default;

struct FastAccessFeedViewComparer
{
    FastAccessFeedView::SP _old;
    FastAccessFeedView::SP _new;
    FastAccessFeedViewComparer(FastAccessFeedView::SP old, FastAccessFeedView::SP new_);
    ~FastAccessFeedViewComparer();
    void expect_not_equal() {
        EXPECT_NE(_old.get(), _new.get());
    }
    void expect_not_equal_attribute_writer() {
        EXPECT_NE(_old->getAttributeWriter().get(), _new->getAttributeWriter().get());
    }
    void expect_equal_summary_adapter() {
        EXPECT_EQ(_old->getSummaryAdapter().get(), _new->getSummaryAdapter().get());
    }
    void expect_not_equal_schema() {
        EXPECT_NE(_old->getSchema().get(), _new->getSchema().get());
    }
};

FastAccessFeedViewComparer::FastAccessFeedViewComparer(FastAccessFeedView::SP old, FastAccessFeedView::SP new_)
    : _old(std::move(old)),
      _new(std::move(new_))
{}
FastAccessFeedViewComparer::~FastAccessFeedViewComparer() = default;

}

TEST(DocSubDBConfigurerTest, require_that_we_can_reconfigure_index_searchable)
{
    Fixture f;
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
        cmp.expect_equal_document_meta_store();
    }
    { // verify feed view
        FeedViewComparer cmp(o.fv, n.fv);
        cmp.expect_equal();
    }
}

namespace {

const AttributeManager *
asAttributeManager(const proton::IAttributeManager::SP &attrMgr)
{
    auto result = dynamic_cast<const AttributeManager *>(attrMgr.get());
    EXPECT_TRUE(result != nullptr);
    return result;
}

}

TEST(DocSubDBConfigurerTest, require_that_we_can_reconfigure_attribute_manager)
{
    Fixture f;
    ViewPtrs o = f._views.getViewPtrs();
    ReconfigParams params(CCR().setAttributesChanged(true).setSchemaChanged(true));
    // Use new config snapshot == old config snapshot (only relevant for reprocessing)
    SerialNum reconfig_serial_num = 0;
    f.reconfigure(*createConfig(), *createConfig(),
                  params, f._resolver, 1, reconfig_serial_num);

    ViewPtrs n = f._views.getViewPtrs();
    { // verify search view
        SearchViewComparer cmp(o.sv, n.sv);
        cmp.expect_not_equal();
        cmp.expect_not_equal_summary_setup();
        cmp.expect_not_equal_match_view();
        cmp.expect_not_equal_matchers();
        cmp.expect_equal_index_searchable();
        cmp.expect_not_equal_attribute_manager();
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

namespace {

AttributeWriter::SP
getAttributeWriter(Fixture &f)
{
    return f._views.feedView.get()->getAttributeWriter();
}

void
checkAttributeWriterChangeOnRepoChange(Fixture &f, bool docTypeRepoChanged)
{
    auto oldAttributeWriter = getAttributeWriter(f);
    ReconfigParams params(CCR().setDocumentTypeRepoChanged(docTypeRepoChanged));
    // Use new config snapshot == old config snapshot (only relevant for reprocessing)
    SerialNum reconfig_serial_num = 0;
    f.reconfigure(*createConfig(), *createConfig(),
                  params, f._resolver, 1, reconfig_serial_num);
    auto newAttributeWriter = getAttributeWriter(f);
    if (docTypeRepoChanged) {
        EXPECT_NE(oldAttributeWriter, newAttributeWriter);
    } else {
        EXPECT_EQ(oldAttributeWriter, newAttributeWriter);
    }
}

}

TEST(DocSubDBConfigurerTest, require_that_we_get_new_attribute_writer_if_document_type_repo_changes)
{
    Fixture f;
    checkAttributeWriterChangeOnRepoChange(f, false);
    checkAttributeWriterChangeOnRepoChange(f, true);
}

TEST(DocSubDBConfigurerTest, require_that_reconfigure_returns_reprocessing_initializer_when_changing_attributes)
{
    Fixture f;
    ReconfigParams params(CCR().setAttributesChanged(true).setSchemaChanged(true));
    SerialNum reconfig_serial_num = 0;
    IReprocessingInitializer::UP init =
        f.reconfigure(*createConfig(), *createConfig(),
                      params, f._resolver, 1, reconfig_serial_num);

    EXPECT_TRUE(init.get() != nullptr);
    EXPECT_TRUE((dynamic_cast<AttributeReprocessingInitializer *>(init.get())) != nullptr);
    EXPECT_FALSE(init->hasReprocessors());
}

TEST(DocSubDBConfigurerTest, require_that_we_can_reconfigure_attribute_writer)
{
    FastAccessFixture f;
    FastAccessFeedView::SP o = f._view._feedView.get();
    SerialNum reconfig_serial_num = 0;
    f.reconfigure(*createConfig(), *createConfig(), 1, reconfig_serial_num);
    FastAccessFeedView::SP n = f._view._feedView.get();

    FastAccessFeedViewComparer cmp(o, n);
    cmp.expect_not_equal();
    cmp.expect_not_equal_attribute_writer();
    cmp.expect_equal_summary_adapter();
    cmp.expect_not_equal_schema();
}

TEST(DocSubDBConfigurerTest, require_that_reconfigure_returns_reprocessing_initializer)
{
    FastAccessFixture f;
    SerialNum reconfig_serial_num = 0;
    auto init = f.reconfigure(*createConfig(), *createConfig(), 1, reconfig_serial_num);

    EXPECT_TRUE(init.get() != nullptr);
    EXPECT_TRUE((dynamic_cast<AttributeReprocessingInitializer *>(init.get())) != nullptr);
    EXPECT_FALSE(init->hasReprocessors());
}

TEST(DocSubDBConfigurerTest, require_that_we_can_reconfigure_summary_manager)
{
    Fixture f;
    ViewPtrs o = f._views.getViewPtrs();
    ReconfigParams params(CCR().setSummaryChanged(true));
    // Use new config snapshot == old config snapshot (only relevant for reprocessing)
    SerialNum reconfig_serial_num = 0;
    f.reconfigure(*createConfig(), *createConfig(), params, f._resolver, reconfig_serial_num);

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

TEST(DocSubDBConfigurerTest, require_that_we_can_reconfigure_matchers)
{
    Fixture f;
    ViewPtrs o = f._views.getViewPtrs();
    // Use new config snapshot == old config snapshot (only relevant for reprocessing)
    SerialNum reconfig_serial_num = 0;
    f.reconfigure(*createConfig(o.fv->getSchema()), *createConfig(o.fv->getSchema()),
                  ReconfigParams(CCR().setRankProfilesChanged(true)), f._resolver, reconfig_serial_num);

    ViewPtrs n = f._views.getViewPtrs();
    { // verify search view
        SearchViewComparer cmp(o.sv, n.sv);
        cmp.expect_not_equal();
        cmp.expect_equal_summary_setup();
        cmp.expect_not_equal_match_view();
        cmp.expect_not_equal_matchers();
        cmp.expect_equal_index_searchable();
        cmp.expect_equal_attribute_manager();
        cmp.expect_equal_document_meta_store();
    }
    { // verify feed view
        FeedViewComparer cmp(o.fv, n.fv);
        cmp.expect_equal();
    }
}

TEST(DocSubDBConfigurerTest, require_that_attribute_manager_should_change_when_imported_fields_have_changed)
{
    ReconfigParams params(CCR().setImportedFieldsChanged(true));
    EXPECT_TRUE(params.shouldAttributeManagerChange());
}

TEST(DocSubDBConfigurerTest, require_that_attribute_manager_should_change_when_visibility_delay_has_changed)
{
    ReconfigParams params(CCR().setVisibilityDelayChanged(true));
    EXPECT_TRUE(params.shouldAttributeManagerChange());
}

TEST(DocSubDBConfigurerTest, require_that_attribute_manager_should_change_when_alloc_config_has_changed)
{
    ReconfigParams params(CCR().set_alloc_config_changed(true));
    EXPECT_TRUE(params.shouldAttributeManagerChange());
}

namespace {

void
assertMaintenanceControllerShouldNotChange(DocumentDBConfig::ComparisonResult result) {
    ReconfigParams params(result);
    EXPECT_FALSE(params.configHasChanged());
    EXPECT_FALSE(params.shouldMaintenanceControllerChange());
}

void
assertMaintenanceControllerShouldChange(DocumentDBConfig::ComparisonResult result, std::string_view label) {
    SCOPED_TRACE(label);
    ReconfigParams params(result);
    EXPECT_TRUE(params.configHasChanged());
    EXPECT_TRUE(params.shouldMaintenanceControllerChange());
}

}

TEST(DocSubDBConfigurerTest, require_that_maintenance_controller_should_change_if_some_config_has_changed)
{
    assertMaintenanceControllerShouldNotChange(CCR());
    assertMaintenanceControllerShouldChange(CCR().setRankProfilesChanged(true), "rank profiles changed");
    assertMaintenanceControllerShouldChange(CCR().setRankingConstantsChanged(true), "ranking constants changed");
    assertMaintenanceControllerShouldChange(CCR().setRankingExpressionsChanged(true), "ranking expressions changed");
    assertMaintenanceControllerShouldChange(CCR().setOnnxModelsChanged(true), "onnx models changed");
    assertMaintenanceControllerShouldChange(CCR().setIndexschemaChanged(true), "index schema changed");
    assertMaintenanceControllerShouldChange(CCR().setAttributesChanged(true), "attributes changed");
    assertMaintenanceControllerShouldChange(CCR().setSummaryChanged(true), "summary changed");
    assertMaintenanceControllerShouldChange(CCR().setJuniperrcChanged(true), "juniperrc changed");
    assertMaintenanceControllerShouldChange(CCR().setDocumenttypesChanged(true), "document types changed");
    assertMaintenanceControllerShouldChange(CCR().setDocumentTypeRepoChanged(true), "document types repo changed");
    assertMaintenanceControllerShouldChange(CCR().setImportedFieldsChanged(true), "imported fields changed");
    assertMaintenanceControllerShouldChange(CCR().setTuneFileDocumentDBChanged(true), "TuneFileDocumentCB changed");
    assertMaintenanceControllerShouldChange(CCR().setSchemaChanged(true), "schema changed");
    assertMaintenanceControllerShouldChange(CCR().setMaintenanceChanged(true), "maintenance changed");
}

namespace {

void
assertSubDbsShouldNotChange(DocumentDBConfig::ComparisonResult result)
{
    ReconfigParams params(result);
    EXPECT_FALSE(params.configHasChanged());
    EXPECT_FALSE(params.shouldSubDbsChange());
}

void
assertSubDbsShouldChange(DocumentDBConfig::ComparisonResult result, std::string_view label)
{
    SCOPED_TRACE(label);
    ReconfigParams params(result);
    EXPECT_TRUE(params.configHasChanged());
    EXPECT_TRUE(params.shouldSubDbsChange());
}

}

TEST(DocSubDBConfigurerTest, require_that_subdbs_should_change_if_relevant_config_changed)
{
    assertSubDbsShouldNotChange(CCR());
    EXPECT_FALSE(ReconfigParams(CCR().setMaintenanceChanged(true)).shouldSubDbsChange());
    assertSubDbsShouldChange(CCR().setFlushChanged(true), "flush cnanged");
    assertSubDbsShouldChange(CCR().setStoreChanged(true), "store changed");
    assertSubDbsShouldChange(CCR().setDocumenttypesChanged(true), "document types changed");
    assertSubDbsShouldChange(CCR().setDocumentTypeRepoChanged(true), "document type repo changed");
    assertSubDbsShouldChange(CCR().setSummaryChanged(true), "summary changed");
    assertSubDbsShouldChange(CCR().setJuniperrcChanged(true), "juniperrc changed");
    assertSubDbsShouldChange(CCR().setAttributesChanged(true), "attributes changed");
    assertSubDbsShouldChange(CCR().setImportedFieldsChanged(true), "imported fields changed");
    assertSubDbsShouldChange(CCR().setVisibilityDelayChanged(true), "visibility delay changed");
    assertSubDbsShouldChange(CCR().setRankProfilesChanged(true), "rank profiles changed");
    assertSubDbsShouldChange(CCR().setRankingConstantsChanged(true), "ranking constants changed");
    assertSubDbsShouldChange(CCR().setRankingExpressionsChanged(true), "ranking expressions changed");
    assertSubDbsShouldChange(CCR().setOnnxModelsChanged(true), "onnx models changed");
    assertSubDbsShouldChange(CCR().setSchemaChanged(true), "schema changed");
    assertSubDbsShouldChange(CCR().set_alloc_config_changed(true), "allocd config changed");
}

namespace {

void
assertSummaryManagerShouldNotChange(DocumentDBConfig::ComparisonResult result)
{
    ReconfigParams params(result);
    EXPECT_FALSE(params.configHasChanged());
    EXPECT_FALSE(params.shouldSummaryManagerChange());
}

void
assertSummaryManagerShouldChange(DocumentDBConfig::ComparisonResult result, std::string_view label)
{
    SCOPED_TRACE(label);
    ReconfigParams params(result);
    EXPECT_TRUE(params.configHasChanged());
    EXPECT_TRUE(params.shouldSummaryManagerChange());
}

}

TEST(DocSubDBConfigurerTest, require_that_summary_manager_should_change_if_relevant_config_changed)
{
    assertSummaryManagerShouldNotChange(CCR());
    assertSummaryManagerShouldChange(CCR().setSummaryChanged(true), "summary changed");
    assertSummaryManagerShouldChange(CCR().setJuniperrcChanged(true), "juniperrc changed");
    assertSummaryManagerShouldChange(CCR().setDocumenttypesChanged(true), "document types changed");
    assertSummaryManagerShouldChange(CCR().setDocumentTypeRepoChanged(true), "document type repo changed");
    assertSummaryManagerShouldChange(CCR().setStoreChanged(true), "store changed");
    assertSummaryManagerShouldChange(CCR().setSchemaChanged(true), "schema changed");
}
