// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/docsummary/summarymanager.h>
#include <vespa/vespalib/util/varholder.h>

namespace search::fef {

class RankingExpressions;
class OnnxModels;

}

namespace searchcorespi { class IndexSearchable; }

namespace proton::matching { class QueryLimiter; }

namespace vespalib::eval { struct ConstantValueFactory; }
namespace vespalib { class Clock; }

namespace proton {

class AttributeCollectionSpecFactory;
class DocumentDBConfig;
class DocumentSubDBReconfig;
class IAttributeWriter;
struct IDocumentDBReferenceResolver;
struct IReprocessingInitializer;
class MatchView;
class Matchers;
class ReconfigParams;
class SearchView;
class SearchableFeedView;

/**
 * Class used to reconfig the feed view and search view used in a searchable sub database.
 */
class SearchableDocSubDBConfigurer
{
private:
    using SearchViewHolder = vespalib::VarHolder<std::shared_ptr<SearchView>>;
    using FeedViewHolder = vespalib::VarHolder<std::shared_ptr<SearchableFeedView>>;
    const std::shared_ptr<ISummaryManager>& _summaryMgr;
    SearchViewHolder            &_searchView;
    FeedViewHolder              &_feedView;
    matching::QueryLimiter      &_queryLimiter;
    const vespalib::eval::ConstantValueFactory& _constant_value_factory;
    const vespalib::Clock       &_clock;
    vespalib::string             _subDbName;
    uint32_t                     _distributionKey;

    void reconfigureFeedView(std::shared_ptr<IAttributeWriter> attrWriter,
                             std::shared_ptr<search::index::Schema> schema,
                             std::shared_ptr<const document::DocumentTypeRepo> repo);

    void reconfigureMatchView(const std::shared_ptr<searchcorespi::IndexSearchable>& indexSearchable);

    void reconfigureMatchView(const std::shared_ptr<Matchers>& matchers,
                              const std::shared_ptr<searchcorespi::IndexSearchable>& indexSearchable,
                              const std::shared_ptr<IAttributeManager>& attrMgr);

    void reconfigureSearchView(std::shared_ptr<MatchView> matchView);

    void reconfigureSearchView(std::shared_ptr<ISummaryManager::ISummarySetup> summarySetup, std::shared_ptr<MatchView> matchView);

public:
    SearchableDocSubDBConfigurer(const SearchableDocSubDBConfigurer &) = delete;
    SearchableDocSubDBConfigurer & operator = (const SearchableDocSubDBConfigurer &) = delete;
    SearchableDocSubDBConfigurer(const std::shared_ptr<ISummaryManager>& summaryMgr,
                                 SearchViewHolder &searchView,
                                 FeedViewHolder &feedView,
                                 matching::QueryLimiter &queryLimiter,
                                 const vespalib::eval::ConstantValueFactory& constant_value_factory,
                                 const vespalib::Clock &clock,
                                 const vespalib::string &subDbName,
                                 uint32_t distributionKey);
    ~SearchableDocSubDBConfigurer();

    std::shared_ptr<Matchers> createMatchers(const DocumentDBConfig& new_config_snapshot);

    void reconfigureIndexSearchable();

    std::unique_ptr<DocumentSubDBReconfig>
    prepare_reconfig(const DocumentDBConfig& new_config_snapshot,
                     const AttributeCollectionSpecFactory& attr_spec_factory,
                     const ReconfigParams& reconfig_params,
                     uint32_t docid_limit,
                     std::optional<search::SerialNum> serial_num);

    std::unique_ptr<IReprocessingInitializer>
    reconfigure(const DocumentDBConfig &newConfig,
                const DocumentDBConfig &oldConfig,
                const ReconfigParams &params,
                IDocumentDBReferenceResolver &resolver,
                const DocumentSubDBReconfig& prepared_reconfig,
                search::SerialNum serial_num);
};

} // namespace proton

