// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <cstdint>
#include <vector>

namespace search::expression { class CorrelatedPredicateNode; }
namespace search::grouping { class GroupingContext; }
namespace search::fef { class MatchDataLayout; class IObjectStore; }

namespace proton::matching {

class MatchToolsFactory;
class Query;

/**
 * Stores a binding reader factory for each labeled correlateBy whose label is used once in the query.
 * Readers iterate documents below docid_limit.
 */
void prepare_correlated_bindings(const Query& query, search::fef::MatchDataLayout& layout,
                                search::fef::IObjectStore& store, uint32_t docid_limit);

/**
 * Installs thread-local query binding providers for the duration of grouping.
 * A filter whose label or variables do not match the query rejects every row,
 * and the problem is reported once as an issue.
 */
class CorrelatedGroupingFilters {
    std::vector<search::expression::CorrelatedPredicateNode*> _filters;
public:
    CorrelatedGroupingFilters(search::grouping::GroupingContext& grouping, const MatchToolsFactory& factory);
    ~CorrelatedGroupingFilters();
    CorrelatedGroupingFilters(const CorrelatedGroupingFilters&) = delete;
    CorrelatedGroupingFilters& operator=(const CorrelatedGroupingFilters&) = delete;
};

} // namespace proton::matching
