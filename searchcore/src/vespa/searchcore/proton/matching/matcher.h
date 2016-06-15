// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "indexenvironment.h"
#include "matching_stats.h"
#include "match_tools.h"
#include "search_session.h"
#include "viewresolver.h"
#include <vespa/searchcore/proton/matching/querylimiter.h>
#include <vespa/searchlib/common/featureset.h>
#include <vespa/searchlib/common/resultset.h>
#include <vespa/searchlib/engine/docsumrequest.h>
#include <vespa/searchlib/engine/searchreply.h>
#include <vespa/searchlib/engine/searchrequest.h>
#include <vespa/searchlib/fef/fef.h>
#include <vespa/searchlib/query/base.h>
#include <vespa/vespalib/util/clock.h>
#include <vespa/vespalib/util/closure.h>
#include <vespa/vespalib/util/sync.h>
#include <vespa/vespalib/util/thread_bundle.h>

namespace search {
namespace grouping {
class GroupingContext;
class GroupingSession;
}  // namespace search::grouping;
namespace index { class Schema; }
namespace attribute { class IAttributeContext; }

class IDocumentMetaStore;

} // namespace search

namespace proton {
namespace matching {

class ISearchContext;
class SessionManager;

/**
 * The Matcher is responsible for performing searches.
 **/
class Matcher
{
private:
    IndexEnvironment              _indexEnv;
    search::fef::BlueprintFactory _blueprintFactory;
    search::fef::RankSetup::SP    _rankSetup;
    ViewResolver                  _viewResolver;
    vespalib::Lock                _statsLock;
    MatchingStats                 _stats;
    const vespalib::Clock       & _clock;
    QueryLimiter                & _queryLimiter;
    uint32_t                      _distributionKey;

    search::FeatureSet::SP
    getFeatureSet(const search::engine::DocsumRequest & req,
                  ISearchContext & searchCtx,
                  search::attribute::IAttributeContext & attrCtx,
                  SessionManager &sessionMgr,
                  bool summaryFeatures);
    search::engine::SearchReply::UP
    handleGroupingSession(
            SessionManager &sessionMgr,
            search::grouping::GroupingContext & groupingContext,
            std::unique_ptr<search::grouping::GroupingSession> gs);

    Matcher(const Matcher &);
    Matcher &operator=(const Matcher &);
public:
    /**
     * Convenience typedefs.
     */
    typedef std::unique_ptr<Matcher> UP;
    typedef std::shared_ptr<Matcher> SP;

    /**
     * Create a new matcher. The schema represents the current index
     * layout.
     *
     * @param schema index schema
     * @param props ranking configuration
     * @param clock used for timeout handling
     **/
    Matcher(const search::index::Schema &schema,
            const search::fef::Properties &props,
            const vespalib::Clock & clock,
            QueryLimiter & queryLimiter,
            uint32_t distributionKey);

    const search::fef::IIndexEnvironment &get_index_env() const { return _indexEnv; }

    /**
     * Observe and reset stats for this object.
     *
     * @return stats
     **/
    MatchingStats getStats();

    /**
     * Create the low-level tools needed to perform matching. This
     * function is exposed for testing purposes.
     **/
    MatchToolsFactory::UP create_match_tools_factory(const search::engine::SearchRequest &request,
                                                     ISearchContext &searchContext,
                                                     search::attribute::IAttributeContext &attrContext,
                                                     const search::IDocumentMetaStore &metaStore,
                                                     const search::fef::Properties &feature_overrides) const
    {
        return MatchToolsFactory::UP(new MatchToolsFactory(
                        _queryLimiter, vespalib::Doom(_clock, request.getTimeOfDoom()),
                        searchContext, attrContext, request.getStackRef(),
                        request.location, _viewResolver, metaStore, _indexEnv,
                        *_rankSetup, request.propertiesMap.rankProperties(),
                        feature_overrides));
    }

    /**
     * Perform a search against this matcher.
     *
     * @return search reply
     * @param request the search request
     * @param threadBundle bundle of threads to use for multi-threaded execution
     * @param searchContext abstract view of searchable data
     * @param attrContext abstract view of attribute data
     * @param sessionManager multilevel grouping session cache
     * @param metaStore the document meta store used to map from lid to gid
     **/
    search::engine::SearchReply::UP
    match(const search::engine::SearchRequest &request,
          vespalib::ThreadBundle &threadBundle,
          ISearchContext &searchContext,
          search::attribute::IAttributeContext &attrContext,
          SessionManager &sessionManager,
          const search::IDocumentMetaStore &metaStore,
          SearchSession::OwnershipBundle &&owned_objects);

    /**
     * Perform matching for the documents in the given docsum request
     * to calculate the summary features specified in the rank setup
     * of this matcher.
     *
     * @param req the docsum request
     * @param searchCtx abstract view of searchable data
     * @param attrCtx abstract view of attribute data
     * @return calculated summary features.
     **/
    search::FeatureSet::SP
    getSummaryFeatures(const search::engine::DocsumRequest & req,
                       ISearchContext & searchCtx,
                       search::attribute::IAttributeContext & attrCtx,
                       SessionManager &sessionManager);

    /**
     * Perform matching for the documents in the given docsum request
     * to calculate the rank features specified in the rank setup of
     * this matcher.
     *
     * @param req the docsum request
     * @param searchCtx abstract view of searchable data
     * @param attrCtx abstract view of attribute data
     * @return calculated rank features.
     **/
    search::FeatureSet::SP
    getRankFeatures(const search::engine::DocsumRequest & req,
                    ISearchContext & searchCtx,
                    search::attribute::IAttributeContext & attrCtx,
                    SessionManager &sessionManager);

    /**
     * @return true if this rankprofile has summary-features enabled
     **/
    bool canProduceSummaryFeatures() const { return ! _rankSetup->getSummaryFeatures().empty(); }
    double get_termwise_limit() const { return _rankSetup->get_termwise_limit(); }
};

} // namespace matching
} // namespace proton

