// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "isummaryadapter.h"
#include "matchers.h"
#include "matchview.h"
#include "searchable_feed_view.h"
#include "searchview.h"
#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/matching/ranking_assets_repo.h>
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_initializer.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/config-attributes.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/vespalib/util/varholder.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_resolver.h>

namespace proton::matching {
    class RankingExpressions;
    class OnnxModels;
}
namespace proton {

struct IDocumentDBReferenceResolver;
class ReconfigParams;

/**
 * Class used to reconfig the feed view and search view used in a searchable sub database.
 */
class SearchableDocSubDBConfigurer
{
private:
    typedef vespalib::VarHolder<SearchView::SP> SearchViewHolder;
    typedef vespalib::VarHolder<SearchableFeedView::SP> FeedViewHolder;
    const ISummaryManager::SP   &_summaryMgr;
    SearchViewHolder            &_searchView;
    FeedViewHolder              &_feedView;
    matching::QueryLimiter      &_queryLimiter;
    matching::RankingAssetsRepo &_rankingAssetsRepo;
    const vespalib::Clock       &_clock;
    vespalib::string             _subDbName;
    uint32_t                     _distributionKey;

    void reconfigureFeedView(IAttributeWriter::SP attrWriter,
                             search::index::Schema::SP schema,
                             std::shared_ptr<const document::DocumentTypeRepo> repo);

    void reconfigureMatchView(const searchcorespi::IndexSearchable::SP &indexSearchable);

    void reconfigureMatchView(const Matchers::SP &matchers,
                              const searchcorespi::IndexSearchable::SP &indexSearchable,
                              const IAttributeManager::SP &attrMgr);

    void reconfigureSearchView(MatchView::SP matchView);

    void reconfigureSearchView(ISummaryManager::ISummarySetup::SP summarySetup, MatchView::SP matchView);

public:
    SearchableDocSubDBConfigurer(const SearchableDocSubDBConfigurer &) = delete;
    SearchableDocSubDBConfigurer & operator = (const SearchableDocSubDBConfigurer &) = delete;
    SearchableDocSubDBConfigurer(const ISummaryManager::SP &summaryMgr,
                                 SearchViewHolder &searchView,
                                 FeedViewHolder &feedView,
                                 matching::QueryLimiter &queryLimiter,
                                 matching::RankingAssetsRepo &rankingAssetsRepo,
                                 const vespalib::Clock &clock,
                                 const vespalib::string &subDbName,
                                 uint32_t distributionKey);
    ~SearchableDocSubDBConfigurer();

    Matchers::SP createMatchers(const search::index::Schema::SP &schema,
                                const vespa::config::search::RankProfilesConfig &cfg);

    void reconfigureIndexSearchable();

    void reconfigure(const DocumentDBConfig &newConfig,
                     const DocumentDBConfig &oldConfig,
                     const ReconfigParams &params,
                     IDocumentDBReferenceResolver &resolver);

    IReprocessingInitializer::UP
    reconfigure(const DocumentDBConfig &newConfig,
                const DocumentDBConfig &oldConfig,
                AttributeCollectionSpec && attrSpec,
                const ReconfigParams &params,
                IDocumentDBReferenceResolver &resolver);
};

} // namespace proton

