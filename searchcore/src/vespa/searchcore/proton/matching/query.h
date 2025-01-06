// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/common/geo_location_spec.h>
#include <vespa/searchlib/fef/itermdata.h>
#include <vespa/searchlib/fef/matchdatalayout.h>
#include <vespa/searchlib/fef/iindexenvironment.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>

namespace vespalib { struct ThreadBundle; }
namespace search::engine { class Trace; }

namespace proton::matching {

class ViewResolver;
class ISearchContext;

class Query
{
private:
    using Blueprint = search::queryeval::Blueprint;
    using GlobalFilter = search::queryeval::GlobalFilter;
    using ExecuteInfo = search::queryeval::ExecuteInfo;
    using IRequestContext = search::queryeval::IRequestContext;
    using GeoLocationSpec = search::common::GeoLocationSpec;
    using InFlow = search::queryeval::InFlow;
    search::query::Node::UP      _query_tree;
    InFlow                       _in_flow = InFlow(true);
    Blueprint::UP                _blueprint;
    Blueprint::UP                _whiteListBlueprint;
    std::vector<GeoLocationSpec> _locations;
    bool                         _needs_ranking = false;

public:
    /** Convenience typedef. */
    using GeoLocationSpecPtrs = std::vector<const GeoLocationSpec *>;

    Query();
    ~Query();
    /**
     * Use the given blueprint as white list node in the blueprint
     * tree. The search iterator created by this blueprint should
     * return all visible / active documents as hits. These hits will
     * then be part of the result set for the query executed. Setting
     * this before building the query will enable additional
     * optimizations.
     *
     * @param whiteListBlueprint the blueprint used for white listing.
     **/
    void setWhiteListBlueprint(Blueprint::UP whiteListBlueprint);

    /**
     * Build query tree from a stack dump.
     *
     * @return success(true)/failure(false)
     **/
    bool buildTree(std::string_view stack,
                   const std::string &location,
                   const ViewResolver &resolver,
                   const search::fef::IIndexEnvironment &idxEnv)
    {
        return buildTree(stack, location, resolver, idxEnv, false);
    }
    bool buildTree(std::string_view stack,
                   const std::string &location,
                   const ViewResolver &resolver,
                   const search::fef::IIndexEnvironment &idxEnv,
                   bool always_mark_phrase_expensive);

    /**
     * Extract query terms from the query tree; to be used to build
     * the query environment.
     *
     * @param terms where to collect terms
     **/
    void extractTerms(std::vector<const search::fef::ITermData *> &terms);

    /**
     * Extract locations from the query tree; to be used to build
     * the query environment.
     *
     * @param locs where to collect locations
     **/
    void extractLocations(GeoLocationSpecPtrs &locs);

    /**
     * Reserve room for terms in the query in the given match data
     * layout. This function also prepares the createSearch function
     * for use.
     *
     * @param context search context
     * @param mdl match data layout
     **/
    void reserveHandles(const IRequestContext & requestContext,
                        ISearchContext &context,
                        search::fef::MatchDataLayout &mdl);

    void enumerate_blueprint_nodes() noexcept;

    /**
     * Optimize the query to be executed. This function should be
     * called after the reserveHandles function and before the
     * fetchPostings function. The only reason this is a separate
     * function is that the query optimization is so awesome that
     * testing becomes harder. Not calling this function enables the
     * test to verify the original query without optimization.
     **/
    void optimize(InFlow in_flow, bool sort_by_cost);
    void fetchPostings(const ExecuteInfo & executeInfo);

    void handle_global_filter(const IRequestContext & requestContext, uint32_t docid_limit,
                              double global_filter_lower_limit, double global_filter_upper_limit,
                              search::engine::Trace& trace, bool sort_by_cost);

    /**
     * Calculates and handles the global filter if needed by the blueprint tree.
     *
     * The estimated hit ratio from the blueprint tree is used to select strategy:
     * 1) estimated_hit_ratio < global_filter_lower_limit:
     *     Nothing is done.
     * 2) estimated_hit_ratio <= global_filter_upper_limit:
     *     Calculate the global filter and set it on the blueprint.
     * 3) estimated_hit_ratio > global_filter_upper_limit:
     *     Set a "match all filter" on the blueprint.
     *
     * @return whether the global filter was set on the blueprint.
     */
    static bool handle_global_filter(Blueprint& blueprint, uint32_t docid_limit,
                                     double global_filter_lower_limit, double global_filter_upper_limit,
                                     vespalib::ThreadBundle &thread_bundle, search::engine::Trace* trace);

    void freeze();
    void set_matching_phase(search::queryeval::MatchingPhase matching_phase) const noexcept;

    /**
     * Create the actual search iterator tree used to find matches.
     *
     * @return iterator tree
     * @param md match data used for feature unpacking
     **/
    std::unique_ptr<search::queryeval::SearchIterator> createSearch(search::fef::MatchData &md) const;

    /**
     * Return an upper bound of how many hits this query will produce.
     * @return estimate of hits produced.
     */
    Blueprint::HitEstimate estimate() const;
    const Blueprint * peekRoot() const { return _blueprint.get(); }
    bool needs_ranking() const noexcept { return _needs_ranking; }
};

}
