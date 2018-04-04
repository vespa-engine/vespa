// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "documentdbconfigmanager.h"
#include "isummaryadapter.h"
#include "matchers.h"
#include "matchview.h"
#include "searchable_feed_view.h"
#include "searchview.h"
#include <vespa/searchcore/proton/attribute/i_attribute_writer.h>
#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/searchcore/proton/index/i_index_writer.h>
#include <vespa/searchcore/proton/matching/constant_value_repo.h>
#include <vespa/searchcore/proton/reprocessing/i_reprocessing_initializer.h>
#include <vespa/document/repo/documenttyperepo.h>
#include <vespa/searchsummary/config/config-juniperrc.h>
#include <vespa/searchcommon/common/schema.h>
#include <vespa/config-attributes.h>
#include <vespa/config-indexschema.h>
#include <vespa/config-rank-profiles.h>
#include <vespa/config-summary.h>
#include <vespa/config-summarymap.h>
#include <vespa/vespalib/util/varholder.h>
#include <vespa/searchcore/proton/reference/i_document_db_reference_resolver.h>

namespace proton {

class IDocumentDBReferenceResolver;
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
    matching::ConstantValueRepo &_constantValueRepo;
    const vespalib::Clock       &_clock;
    vespalib::string             _subDbName;
    uint32_t                     _distributionKey;

    void
    reconfigureFeedView(const SearchView::SP &searchView);

    void
    reconfigureFeedView(const IIndexWriter::SP &indexWriter,
                        const ISummaryAdapter::SP &summaryAdapter,
                        const IAttributeWriter::SP &attrWriter,
                        const search::index::Schema::SP &schema,
                        const std::shared_ptr<const document::DocumentTypeRepo> &repo,
                        const SearchView::SP &searchView);

    void
    reconfigureMatchView(const searchcorespi::IndexSearchable::SP &indexSearchable);

    void
    reconfigureMatchView(const Matchers::SP &matchers,
                         const searchcorespi::IndexSearchable::SP &indexSearchable,
                         const IAttributeManager::SP &attrMgr);

    void
    reconfigureSearchView(const MatchView::SP &matchView);

    void
    reconfigureSearchView(const ISummaryManager::ISummarySetup::SP &summarySetup,
                           const MatchView::SP &matchView);

public:
    SearchableDocSubDBConfigurer(const SearchableDocSubDBConfigurer &) = delete;
    SearchableDocSubDBConfigurer & operator = (const SearchableDocSubDBConfigurer &) = delete;
    SearchableDocSubDBConfigurer(const ISummaryManager::SP &summaryMgr,
                                 SearchViewHolder &searchView,
                                 FeedViewHolder &feedView,
                                 matching::QueryLimiter &queryLimiter,
                                 matching::ConstantValueRepo &constantValueRepo,
                                 const vespalib::Clock &clock,
                                 const vespalib::string &subDbName,
                                 uint32_t distributionKey);
    ~SearchableDocSubDBConfigurer();

    Matchers::UP
    createMatchers(const search::index::Schema::SP &schema,
                   const vespa::config::search::RankProfilesConfig &cfg);

    void
    reconfigureIndexSearchable();

    void
    reconfigure(const DocumentDBConfig &newConfig,
                const DocumentDBConfig &oldConfig,
                const ReconfigParams &params,
                IDocumentDBReferenceResolver &resolver);

    IReprocessingInitializer::UP
    reconfigure(const DocumentDBConfig &newConfig,
                const DocumentDBConfig &oldConfig,
                const AttributeCollectionSpec &attrSpec,
                const ReconfigParams &params,
                IDocumentDBReferenceResolver &resolver);
};

} // namespace proton

