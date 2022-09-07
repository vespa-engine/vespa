// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchable_doc_subdb_configurer.h"
#include "reconfig_params.h"
#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/imported_attributes_repo.h>
#include <vespa/searchcore/proton/common/document_type_inspector.h>
#include <vespa/searchcore/proton/common/indexschema_inspector.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_resolver.h>
#include <vespa/searchcore/proton/reprocessing/attribute_reprocessing_initializer.h>
#include <vespa/eval/eval/llvm/compile_cache.h>

using namespace vespa::config::search;
using namespace config;
using search::index::Schema;
using searchcorespi::IndexSearchable;
using document::DocumentTypeRepo;
using vespa::config::search::RankProfilesConfig;

namespace proton {

using matching::Matcher;
using matching::RankingExpressions;
using matching::OnnxModels;

typedef AttributeReprocessingInitializer::Config ARIConfig;

void
SearchableDocSubDBConfigurer::reconfigureFeedView(IAttributeWriter::SP attrWriter,
                                                  Schema::SP schema,
                                                  std::shared_ptr<const DocumentTypeRepo> repo)
{
    SearchableFeedView::SP curr = _feedView.get();
    _feedView.set(std::make_shared<SearchableFeedView>(
            StoreOnlyFeedView::Context(curr->getSummaryAdapter(),
                    std::move(schema),
                    curr->getDocumentMetaStore(),
                    std::move(repo),
                    curr->getUncommittedLidTracker(),
                    curr->getGidToLidChangeHandler(),
                    curr->getWriteService()),
            curr->getPersistentParams(),
            FastAccessFeedView::Context(std::move(attrWriter), curr->getDocIdLimit()),
            SearchableFeedView::Context(curr->getIndexWriter())));
}

void
SearchableDocSubDBConfigurer::
reconfigureMatchView(const IndexSearchable::SP &indexSearchable)
{
    SearchView::SP curr = _searchView.get();
    reconfigureMatchView(curr->getMatchers(),
                         indexSearchable,
                         curr->getAttributeManager());
}

void
SearchableDocSubDBConfigurer::
reconfigureMatchView(const Matchers::SP &matchers,
                     const IndexSearchable::SP &indexSearchable,
                     const IAttributeManager::SP &attrMgr)
{
    SearchView::SP curr = _searchView.get();
    auto matchView = std::make_shared<MatchView>(matchers, indexSearchable, attrMgr, curr->getSessionManager(),
                                                 curr->getDocumentMetaStore(), curr->getDocIdLimit());
    reconfigureSearchView(matchView);
}

void
SearchableDocSubDBConfigurer::reconfigureSearchView(MatchView::SP matchView)
{
    SearchView::SP curr = _searchView.get();
    // make sure the initial search does not spend time waiting for
    // expression compilation completion during rank program setup.
    vespalib::eval::CompileCache::wait_pending();
    _searchView.set(SearchView::create(curr->getSummarySetup(), std::move(matchView)));
}

void
SearchableDocSubDBConfigurer::reconfigureSearchView(ISummaryManager::ISummarySetup::SP summarySetup,
                                                    MatchView::SP matchView)
{
    _searchView.set(SearchView::create(std::move(summarySetup), std::move(matchView)));
}

SearchableDocSubDBConfigurer::
SearchableDocSubDBConfigurer(const ISummaryManager::SP &summaryMgr,
                             SearchViewHolder &searchView,
                             FeedViewHolder &feedView,
                             matching::QueryLimiter &queryLimiter,
                             matching::RankingAssetsRepo &rankingAssetsRepo,
                             const vespalib::Clock &clock,
                             const vespalib::string &subDbName,
                             uint32_t distributionKey) :
    _summaryMgr(summaryMgr),
    _searchView(searchView),
    _feedView(feedView),
    _queryLimiter(queryLimiter),
    _rankingAssetsRepo(rankingAssetsRepo),
    _clock(clock),
    _subDbName(subDbName),
    _distributionKey(distributionKey)
{ }

SearchableDocSubDBConfigurer::~SearchableDocSubDBConfigurer() = default;

std::shared_ptr<Matchers>
SearchableDocSubDBConfigurer::createMatchers(const Schema::SP &schema,
                                             const RankProfilesConfig &cfg)
{
    auto newMatchers = std::make_shared<Matchers>(_clock, _queryLimiter, _rankingAssetsRepo);
    for (const auto &profile : cfg.rankprofile) {
        vespalib::string name = profile.name;
        search::fef::Properties properties;
        for (const auto &property : profile.fef.property) {
            properties.add(property.name, property.value);
        }
        // schema instance only used during call.
        auto profptr = std::make_shared<Matcher>(*schema, std::move(properties), _clock, _queryLimiter,
                                                 _rankingAssetsRepo, _distributionKey);
        newMatchers->add(name, std::move(profptr));
    }
    return newMatchers;
}

void
SearchableDocSubDBConfigurer::reconfigureIndexSearchable()
{
    SearchableFeedView::SP feedView(_feedView.get());
    const IIndexWriter::SP &indexWriter = feedView->getIndexWriter();
    const searchcorespi::IIndexManager::SP &indexManager = indexWriter->getIndexManager();
    reconfigureMatchView(indexManager->getSearchable());
}

void
SearchableDocSubDBConfigurer::
reconfigure(const DocumentDBConfig &newConfig,
            const DocumentDBConfig &oldConfig,
            const ReconfigParams &params,
            IDocumentDBReferenceResolver &resolver)
{
    assert(!params.shouldAttributeManagerChange());
    AttributeCollectionSpec attrSpec(AttributeCollectionSpec::AttributeList(), 0, 0);
    reconfigure(newConfig, oldConfig, std::move(attrSpec), params, resolver);
}

namespace {

IReprocessingInitializer::UP
createAttributeReprocessingInitializer(const DocumentDBConfig &newConfig,
                                       const IAttributeManager::SP &newAttrMgr,
                                       const DocumentDBConfig &oldConfig,
                                       const IAttributeManager::SP &oldAttrMgr,
                                       const vespalib::string &subDbName,
                                       search::SerialNum serialNum)
{
    const document::DocumentType *newDocType = newConfig.getDocumentType();
    const document::DocumentType *oldDocType = oldConfig.getDocumentType();
    assert(newDocType != nullptr);
    assert(oldDocType != nullptr);
    DocumentTypeInspector inspector(*oldDocType, *newDocType);
    IndexschemaInspector oldIndexschemaInspector(oldConfig.getIndexschemaConfig());
    return std::make_unique<AttributeReprocessingInitializer>
        (ARIConfig(newAttrMgr, *newConfig.getSchemaSP()),
         ARIConfig(oldAttrMgr, *oldConfig.getSchemaSP()),
         inspector, oldIndexschemaInspector, subDbName, serialNum);
}

}

IReprocessingInitializer::UP
SearchableDocSubDBConfigurer::reconfigure(const DocumentDBConfig &newConfig,
                                          const DocumentDBConfig &oldConfig,
                                          AttributeCollectionSpec && attrSpec,
                                          const ReconfigParams &params,
                                          IDocumentDBReferenceResolver &resolver)
{
    bool shouldMatchViewChange = false;
    bool shouldSearchViewChange = false;
    bool shouldFeedViewChange = params.shouldSchemaChange();
    search::SerialNum currentSerialNum = attrSpec.getCurrentSerialNum();
    SearchView::SP searchView = _searchView.get();
    Matchers::SP matchers = searchView->getMatchers();
    if (params.shouldMatchersChange()) {
        _rankingAssetsRepo.reconfigure(newConfig.getRankingConstantsSP(),
                                       newConfig.getRankingExpressionsSP(),
                                       newConfig.getOnnxModelsSP());
        Matchers::SP newMatchers = createMatchers(newConfig.getSchemaSP(), newConfig.getRankProfilesConfig());
        matchers = newMatchers;
        shouldMatchViewChange = true;
    }
    IReprocessingInitializer::UP initializer;
    IAttributeManager::SP attrMgr = searchView->getAttributeManager();
    IAttributeWriter::SP attrWriter = _feedView.get()->getAttributeWriter();
    if (params.shouldAttributeManagerChange()) {
        IAttributeManager::SP newAttrMgr = attrMgr->create(std::move(attrSpec));
        newAttrMgr->setImportedAttributes(resolver.resolve(*newAttrMgr, *attrMgr,
                                                           searchView->getDocumentMetaStore(),
                                                           newConfig.getMaintenanceConfigSP()->getVisibilityDelay()));
        IAttributeManager::SP oldAttrMgr = attrMgr;
        attrMgr = newAttrMgr;
        shouldMatchViewChange = true;

        auto newAttrWriter = std::make_shared<AttributeWriter>(newAttrMgr);
        attrWriter = newAttrWriter;
        shouldFeedViewChange = true;
        initializer = createAttributeReprocessingInitializer(newConfig, newAttrMgr, oldConfig, oldAttrMgr,
                                                             _subDbName, currentSerialNum);
    } else if (params.shouldAttributeWriterChange()) {
        attrWriter = std::make_shared<AttributeWriter>(attrMgr);
        shouldFeedViewChange = true;
    }

    ISummaryManager::ISummarySetup::SP sumSetup = _searchView.get()->getSummarySetup();
    if (params.shouldSummaryManagerChange() ||
        params.shouldAttributeManagerChange())
    {
        ISummaryManager::SP sumMgr(_summaryMgr);
        ISummaryManager::ISummarySetup::SP newSumSetup =
            sumMgr->createSummarySetup(newConfig.getSummaryConfig(),
                                       newConfig.getJuniperrcConfig(),
                                       newConfig.getDocumentTypeRepoSP(),
                                       attrMgr);
        sumSetup = newSumSetup;
        shouldSearchViewChange = true;
    }

    if (shouldMatchViewChange) {
        IndexSearchable::SP indexSearchable = searchView->getIndexSearchable();
        reconfigureMatchView(matchers, indexSearchable, attrMgr);
        searchView = _searchView.get();
    }

    if (shouldSearchViewChange) {
        reconfigureSearchView(sumSetup, searchView->getMatchView());
    }

    if (shouldFeedViewChange) {
        reconfigureFeedView(std::move(attrWriter),
                            newConfig.getSchemaSP(),
                            newConfig.getDocumentTypeRepoSP());
    }
    return initializer;
}


} // namespace proton
