// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "hit_estimate.h"
#include <vespa/searchlib/queryeval/blueprint.h>
#include <vespa/searchlib/queryeval/flow_tuning.h>

namespace search::attribute {

/**
 * Adapter used when calculating FlowStats based on HitEstimate per term.
 */
struct HitEstimateFlowStatsAdapter {
    uint32_t docid_limit;
    uint32_t num_indirections;
    explicit HitEstimateFlowStatsAdapter(uint32_t docid_limit_in, uint32_t num_indirections_in) noexcept
        : docid_limit(docid_limit_in), num_indirections(num_indirections_in) {}
    double abs_to_rel_est(const HitEstimate& est) const noexcept {
        return queryeval::Blueprint::abs_to_rel_est(est.est_hits(), docid_limit);
    }
    double estimate(const HitEstimate& est) const noexcept {
        return est.is_unknown() ? 0.5 : abs_to_rel_est(est);
    }
    double cost(const HitEstimate& est) const noexcept {
        return est.is_unknown() ? queryeval::flow::lookup_cost(num_indirections) : queryeval::flow::btree_cost(abs_to_rel_est(est));
    }
    double strict_cost(const HitEstimate &est) const noexcept {
        return est.is_unknown() ? queryeval::flow::lookup_strict_cost(num_indirections) : queryeval::flow::btree_strict_cost(abs_to_rel_est(est));
    }
};

}
