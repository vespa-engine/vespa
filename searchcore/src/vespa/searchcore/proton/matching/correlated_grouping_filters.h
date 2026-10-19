// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
#pragma once

#include <vector>

namespace search::expression { class CorrelatedPredicateNode; }
namespace search::grouping { class GroupingContext; }
namespace search::fef { class MatchDataLayout; class IObjectStore; }

namespace proton::matching {

class MatchToolsFactory;
class Query;

void prepare_correlated_bindings(const Query& query, search::fef::MatchDataLayout& layout,
                                search::fef::IObjectStore& store);

/** Installs thread-local query binding providers for the duration of grouping. */
class CorrelatedGroupingFilters {
    std::vector<search::expression::CorrelatedPredicateNode*> _filters;
public:
    CorrelatedGroupingFilters(search::grouping::GroupingContext& grouping, const MatchToolsFactory& factory);
    ~CorrelatedGroupingFilters();
    CorrelatedGroupingFilters(const CorrelatedGroupingFilters&) = delete;
    CorrelatedGroupingFilters& operator=(const CorrelatedGroupingFilters&) = delete;
};

} // namespace proton::matching
