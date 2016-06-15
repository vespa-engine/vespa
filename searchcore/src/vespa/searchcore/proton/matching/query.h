// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchlib/fef/location.h>
#include <vespa/searchlib/query/tree/node.h>
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/irequestcontext.h>

namespace search { namespace fef { class ITermData; }}
namespace search { namespace fef { class MatchDataLayout; }}
namespace search { namespace fef { class IIndexEnvironment; }}

namespace proton {
namespace matching {

class ViewResolver;
class ISearchContext;

class Query
{
private:
    search::query::Node::UP          _query_tree;
    search::queryeval::Blueprint::UP _blueprint;
    search::fef::Location            _location;
    search::queryeval::Blueprint::UP _blackListBlueprint;

public:
    /**
     * Build query tree from a stack dump.
     *
     * @return success(true)/failure(false)
     **/
    bool buildTree(const vespalib::stringref &stack,
                   const vespalib::string &location,
                   const ViewResolver &resolver,
                   const search::fef::IIndexEnvironment &idxEnv);

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
    void extractLocations(std::vector<const search::fef::Location *> &locs);

    /**
     * Use the given blueprint as black list node in the blueprint tree.
     * The search iterator created by this blueprint should return all
     * invisible / inactive documents as hits. These hits will then not be
     * part of the result set for the query executed.
     *
     * @param blackListBlueprint the blueprint used for black listing.
     **/
    void setBlackListBlueprint(search::queryeval::Blueprint::UP blackListBlueprint);

    /**
     * Reserve room for terms in the query in the given match data
     * layout. This function also prepares the createSearch function
     * for use.
     *
     * @param context search context
     * @param mdl match data layout
     **/
    void reserveHandles(const search::queryeval::IRequestContext & requestContext,
                        ISearchContext &context,
                        search::fef::MatchDataLayout &mdl);

    /**
     * Optimize the query to be executed. This function should be
     * called after the reserveHandles function and before the
     * fetchPostings function. The only reason this is a separate
     * function is that the query optimization is so awesome that
     * testing becomes harder. Not calling this function enables the
     * test to verify the original query without optimization.
     **/
    void optimize();
    void fetchPostings(void);

    /**
     * Create the actual search iterator tree used to find matches.
     *
     * @return iterator tree
     * @param md match data used for feature unpacking
     **/
    search::queryeval::SearchIterator::UP
    createSearch(search::fef::MatchData &md) const;
};

} // namespace matching
} // namespace proton

