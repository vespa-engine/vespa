// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::bmcluster {

/*
 * Class for estimating moved docs ratio during
 * document redistribution.
 */
class EstimateMovedDocsRatio {
    bool _verbose;
public:
    EstimateMovedDocsRatio();
    explicit EstimateMovedDocsRatio(bool verbose);
    double estimate_lost_docs_base_ratio(uint32_t redundancy, uint32_t lost_nodes, uint32_t num_nodes);
    double estimate_moved_docs_ratio_grow(uint32_t redundancy, uint32_t added_nodes, uint32_t num_nodes);
    double estimate_moved_docs_ratio_shrink(uint32_t redundancy, uint32_t retired_nodes, uint32_t num_nodes);
    double estimate_moved_docs_ratio_crash(uint32_t redundancy, uint32_t crashed_nodes, uint32_t num_nodes);
    double estimate_moved_docs_ratio_replace(uint32_t redundancy, uint32_t added_nodes, uint32_t retired_nodes, uint32_t num_nodes);
};

}
