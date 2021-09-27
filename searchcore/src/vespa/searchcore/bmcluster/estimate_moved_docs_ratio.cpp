// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "estimate_moved_docs_ratio.h"

#include <vespa/log/log.h>
LOG_SETUP(".bmcluster.estimate_moved_docs_ratio");

namespace search::bmcluster {

EstimateMovedDocsRatio::EstimateMovedDocsRatio()
    : EstimateMovedDocsRatio(false)
{
}

EstimateMovedDocsRatio::EstimateMovedDocsRatio(bool verbose)
    : _verbose(verbose)
{
}

double
EstimateMovedDocsRatio::estimate_lost_docs_base_ratio(uint32_t redundancy, uint32_t lost_nodes, uint32_t num_nodes)
{
    if (redundancy > lost_nodes) {
        return 0.0;
    }
    double loss_ratio = 1.0;
    for (uint32_t i = 0; i < redundancy; ++i) {
        loss_ratio *= ((double) (lost_nodes - i)) / (num_nodes - i);
    }
    if (_verbose) {
        LOG(info, "estimated lost docs base ratio: %4.2f", loss_ratio);
    }
    return loss_ratio;
}

double
EstimateMovedDocsRatio::estimate_moved_docs_ratio_grow(uint32_t redundancy, uint32_t added_nodes, uint32_t num_nodes)
{
    if (added_nodes == num_nodes) {
        return 0.0;
    }
    double new_redundancy = redundancy;
    double new_per_node_doc_ratio = new_redundancy / num_nodes;
    double moved_ratio = new_per_node_doc_ratio * added_nodes;
    if (_verbose) {
        LOG(info, "estimated_moved_docs_ratio_grow(%u,%u,%u)=%4.2f", redundancy, added_nodes, num_nodes, moved_ratio);
    }
    return moved_ratio;
}

double
EstimateMovedDocsRatio::estimate_moved_docs_ratio_shrink(uint32_t redundancy, uint32_t retired_nodes, uint32_t num_nodes)
{
    if (retired_nodes == num_nodes) {
        return 0.0;
    }
    double old_redundancy = redundancy;
    double old_per_node_doc_ratio = old_redundancy / num_nodes;
    uint32_t new_nodes = num_nodes - retired_nodes;
    double new_redundancy = std::min(redundancy, new_nodes);
    double new_per_node_doc_ratio = new_redundancy / new_nodes;
    double moved_ratio = (new_per_node_doc_ratio - old_per_node_doc_ratio) * new_nodes;
    if (_verbose) {
        LOG(info, "estimated_moved_docs_ratio_shrink(%u,%u,%u)=%4.2f", redundancy, retired_nodes, num_nodes, moved_ratio);
    }
    return moved_ratio;
}

double
EstimateMovedDocsRatio::estimate_moved_docs_ratio_crash(uint32_t redundancy, uint32_t crashed_nodes, uint32_t num_nodes)
{
    if (crashed_nodes == num_nodes) {
        return 0.0;
    }
    double old_redundancy = redundancy;
    double old_per_node_doc_ratio = old_redundancy / num_nodes;
    uint32_t new_nodes = num_nodes - crashed_nodes;
    double new_redundancy = std::min(redundancy, new_nodes);
    double new_per_node_doc_ratio = new_redundancy / new_nodes;
    double lost_docs_ratio = estimate_lost_docs_base_ratio(redundancy, crashed_nodes, num_nodes) * new_redundancy;
    double moved_ratio = (new_per_node_doc_ratio - old_per_node_doc_ratio) * new_nodes - lost_docs_ratio;
    if (_verbose) {
        LOG(info, "estimated_moved_docs_ratio_crash(%u,%u,%u)=%4.2f", redundancy, crashed_nodes, num_nodes, moved_ratio);
    }
    return moved_ratio;
}

double
EstimateMovedDocsRatio::estimate_moved_docs_ratio_replace(uint32_t redundancy, uint32_t added_nodes, uint32_t retired_nodes, uint32_t num_nodes)
{
    if (added_nodes == num_nodes || retired_nodes == num_nodes) {
        return 0.0;
    }
    uint32_t old_nodes = num_nodes - added_nodes;
    double old_redundancy = std::min(redundancy, old_nodes);
    [[maybe_unused]] double old_per_node_doc_ratio = old_redundancy / old_nodes;
    uint32_t new_nodes = num_nodes - retired_nodes;
    double new_redundancy = std::min(redundancy, new_nodes);
    double new_per_node_doc_ratio = new_redundancy / new_nodes;
    double moved_ratio = new_per_node_doc_ratio * added_nodes;
    uint32_t stable_nodes = num_nodes - added_nodes - retired_nodes;
    // Account for extra documents moved from retired nodes to stable nodes
    // TODO: Fix calculation
    double baseline_per_node_doc_ratio = ((double) redundancy) / num_nodes;
    double extra_per_stable_node_doc_ratio = std::min(baseline_per_node_doc_ratio * retired_nodes / new_nodes, 1.0 - old_per_node_doc_ratio);
    double extra_moved_ratio = extra_per_stable_node_doc_ratio * stable_nodes;
    moved_ratio += extra_moved_ratio;
    if (_verbose) {
        LOG(info, "estimated_moved_docs_ratio_replace(%u,%u,%u,%u)=%4.2f, (of which %4.2f extra)", redundancy, added_nodes, retired_nodes, num_nodes, moved_ratio, extra_moved_ratio);
    }
    return moved_ratio;
}

}
