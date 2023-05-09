// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "searchable_doc_subdb_configurer.h"
#include "document_subdb_reconfig.h"
#include "reconfig_params.h"
#include "searchable_feed_view.h"
#include "searchview.h"
#include <vespa/config-rank-profiles.h>
#include <vespa/searchcore/proton/matching/matcher.h>
#include <vespa/searchcore/proton/attribute/attribute_collection_spec.h>
#include <vespa/searchcore/proton/attribute/attribute_collection_spec_factory.h>
#include <vespa/searchcore/proton/attribute/attribute_writer.h>
#include <vespa/searchcore/proton/attribute/i_attribute_manager_reconfig.h>
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

using ARIConfig = AttributeReprocessingInitializer::Config;

void
SearchableDocSubDBConfigurer::reconfigureFeedView(std::shared_ptr<IAttributeWriter> attrWriter,
                                                  std::shared_ptr<Schema> schema,
                                                  std::shared_ptr<const DocumentTypeRepo> repo)
{
    auto curr = _feedView.get();
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
reconfigureMatchView(const std::shared_ptr<IndexSearchable>& indexSearchable)
{
    auto curr = _searchView.get();
    reconfigureMatchView(curr->getMatchers(),
                         indexSearchable,
                         curr->getAttributeManager());
}

void
SearchableDocSubDBConfigurer::
reconfigureMatchView(const std::shared_ptr<Matchers>& matchers,
                     const std::shared_ptr<IndexSearchable>& indexSearchable,
                     const std::shared_ptr<IAttributeManager>& attrMgr)
{
    auto curr = _searchView.get();
    auto matchView = std::make_shared<MatchView>(matchers, indexSearchable, attrMgr, curr->getSessionManager(),
                                                 curr->getDocumentMetaStore(), curr->getDocIdLimit());
    reconfigureSearchView(matchView);
}

void
SearchableDocSubDBConfigurer::reconfigureSearchView(std::shared_ptr<MatchView> matchView)
{
    auto curr = _searchView.get();
    // make sure the initial search does not spend time waiting for
    // expression compilation completion during rank program setup.
    vespalib::eval::CompileCache::wait_pending();
    _searchView.set(SearchView::create(curr->getSummarySetup(), std::move(matchView)));
}

void
SearchableDocSubDBConfigurer::reconfigureSearchView(std::shared_ptr<ISummaryManager::ISummarySetup> summarySetup,
                                                    std::shared_ptr<MatchView> matchView)
{
    _searchView.set(SearchView::create(std::move(summarySetup), std::move(matchView)));
}

SearchableDocSubDBConfigurer::
SearchableDocSubDBConfigurer(const std::shared_ptr<ISummaryManager>& summaryMgr,
                             SearchViewHolder &searchView,
                             FeedViewHolder &feedView,
                             matching::QueryLimiter &queryLimiter,
                             const vespalib::eval::ConstantValueFactory& constant_value_factory,
                             const vespalib::Clock &clock,
                             const vespalib::string &subDbName,
                             uint32_t distributionKey) :
    _summaryMgr(summaryMgr),
    _searchView(searchView),
    _feedView(feedView),
    _queryLimiter(queryLimiter),
    _constant_value_factory(constant_value_factory),
    _clock(clock),
    _subDbName(subDbName),
    _distributionKey(distributionKey)
{ }

SearchableDocSubDBConfigurer::~SearchableDocSubDBConfigurer() = default;

std::shared_ptr<Matchers>
SearchableDocSubDBConfigurer::createMatchers(const DocumentDBConfig& new_config_snapshot)
{
    auto& schema = new_config_snapshot.getSchemaSP();
    auto& cfg = new_config_snapshot.getRankProfilesConfig();
    search::fef::RankingAssetsRepo ranking_assets_repo_source(_constant_value_factory,
                                                              new_config_snapshot.getRankingConstantsSP(),
                                                              new_config_snapshot.getRankingExpressionsSP(),
                                                              new_config_snapshot.getOnnxModelsSP());
    auto newMatchers = std::make_shared<Matchers>(_clock, _queryLimiter, ranking_assets_repo_source);
    auto& ranking_assets_repo = newMatchers->get_ranking_assets_repo();
    for (const auto &profile : cfg.rankprofile) {
        vespalib::string name = profile.name;
        search::fef::Properties properties;
        for (const auto &property : profile.fef.property) {
            properties.add(property.name, property.value);
        }
        // schema instance only used during call.
        auto profptr = std::make_shared<Matcher>(*schema, std::move(properties), _clock, _queryLimiter,
                                                 ranking_assets_repo, _distributionKey);
        newMatchers->add(name, std::move(profptr));
    }
    return newMatchers;
}

void
SearchableDocSubDBConfigurer::reconfigureIndexSearchable()
{
    auto feedView(_feedView.get());
    auto& indexWriter = feedView->getIndexWriter();
    auto& indexManager = indexWriter->getIndexManager();
    reconfigureMatchView(indexManager->getSearchable());
}

std::unique_ptr<DocumentSubDBReconfig>
SearchableDocSubDBConfigurer::prepare_reconfig(const DocumentDBConfig& new_config_snapshot,
                                               const AttributeCollectionSpecFactory& attr_spec_factory,
                                               const ReconfigParams& reconfig_params,
                                               uint32_t docid_limit,
                                               std::optional<search::SerialNum> serial_num)
{
    auto old_matchers = _searchView.get()->getMatchers();
    auto old_attribute_manager = _searchView.get()->getAttributeManager();
    auto reconfig = std::make_unique<DocumentSubDBReconfig>(std::move(old_matchers), old_attribute_manager);
    if (reconfig_params.shouldMatchersChange()) {
        reconfig->set_matchers(createMatchers(new_config_snapshot));
    }
    if (reconfig_params.shouldAttributeManagerChange()) {
        auto attr_spec = attr_spec_factory.create(new_config_snapshot.getAttributesConfig(), docid_limit, serial_num);
        reconfig->set_attribute_manager_reconfig(old_attribute_manager->prepare_create(std::move(*attr_spec)));
    }
    return reconfig;
}

namespace {

IReprocessingInitializer::UP
createAttributeReprocessingInitializer(const DocumentDBConfig &newConfig,
                                       const std::shared_ptr<IAttributeManager>& newAttrMgr,
                                       const DocumentDBConfig &oldConfig,
                                       const std::shared_ptr<IAttributeManager>& oldAttrMgr,
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
                                          const ReconfigParams &params,
                                          IDocumentDBReferenceResolver &resolver,
                                          const DocumentSubDBReconfig& prepared_reconfig,
                                          search::SerialNum serial_num)
{
    bool shouldMatchViewChange = prepared_reconfig.has_matchers_changed();
    bool shouldSearchViewChange = false;
    bool shouldFeedViewChange = params.shouldSchemaChange();
    auto searchView = _searchView.get();
    auto matchers = prepared_reconfig.matchers();
    IReprocessingInitializer::UP initializer;
    auto attrMgr = searchView->getAttributeManager();
    auto attrWriter = _feedView.get()->getAttributeWriter();
    if (prepared_reconfig.has_attribute_manager_changed()) {
        auto newAttrMgr = prepared_reconfig.attribute_manager();
        newAttrMgr->setImportedAttributes(resolver.resolve(*newAttrMgr, *attrMgr,
                                                           searchView->getDocumentMetaStore(),
                                                           newConfig.getMaintenanceConfigSP()->getVisibilityDelay()));
        auto oldAttrMgr = attrMgr;
        attrMgr = newAttrMgr;
        shouldMatchViewChange = true;

        auto newAttrWriter = std::make_shared<AttributeWriter>(newAttrMgr);
        attrWriter = newAttrWriter;
        shouldFeedViewChange = true;
        initializer = createAttributeReprocessingInitializer(newConfig, newAttrMgr, oldConfig, oldAttrMgr,
                                                             _subDbName, serial_num);
    } else if (params.shouldAttributeWriterChange()) {
        attrWriter = std::make_shared<AttributeWriter>(attrMgr);
        shouldFeedViewChange = true;
    }

    auto sumSetup = _searchView.get()->getSummarySetup();
    if (params.shouldSummaryManagerChange() ||
        params.shouldAttributeManagerChange())
    {
        auto sumMgr(_summaryMgr);
        auto newSumSetup =
            sumMgr->createSummarySetup(newConfig.getSummaryConfig(),
                                       newConfig.getJuniperrcConfig(),
                                       newConfig.getDocumentTypeRepoSP(),
                                       attrMgr,
                                       *newConfig.getSchemaSP());
        sumSetup = newSumSetup;
        shouldSearchViewChange = true;
    }

    if (shouldMatchViewChange) {
        auto indexSearchable = searchView->getIndexSearchable();
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
