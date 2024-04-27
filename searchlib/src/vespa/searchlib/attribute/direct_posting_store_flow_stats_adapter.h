// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "i_direct_posting_store.h"
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/flow_tuning.h>

namespace search::attribute {

/**
 * Adapter used when calculating FlowStats based on IDirectPostingStore::LookupResult per term.
 */
struct DirectPostingStoreFlowStatsAdapter {
    uint32_t docid_limit;
    DirectPostingStoreFlowStatsAdapter(uint32_t docid_limit_in) noexcept : docid_limit(docid_limit_in) {}
    double estimate(const IDirectPostingStore::LookupResult& term) const noexcept {
        return queryeval::Blueprint::abs_to_rel_est(term.posting_size, docid_limit);
    }
    double cost(const IDirectPostingStore::LookupResult& term) const noexcept {
        double rel_est = queryeval::Blueprint::abs_to_rel_est(term.posting_size, docid_limit);
        return queryeval::flow::btree_cost(rel_est);
    }
    double strict_cost(const IDirectPostingStore::LookupResult& term) const noexcept {
        double rel_est = queryeval::Blueprint::abs_to_rel_est(term.posting_size, docid_limit);
        return queryeval::flow::btree_strict_cost(rel_est);
    }
};

}
