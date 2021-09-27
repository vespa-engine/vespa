// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>
#include <vector>

namespace search::bmcluster {

/*
 * Class for calculating moved docs ratio during
 * document redistribution.
 */
class CalculateMovedDocsRatio
{
    class Placements;
    std::vector<uint32_t> _num_states;
    uint32_t _nodes;
    uint32_t _old_placement_mask;
    uint32_t _new_placement_mask;
    uint32_t _new_up_mask;
    uint32_t _moved_docs;
    std::vector<uint32_t> _moved_docs_per_node;
    uint32_t _checked_states;
    uint32_t _lost_docs_base;
    uint32_t _redundancy;
    uint32_t _old_redundancy;
    uint32_t _new_redundancy;

    void scan(Placements selected, Placements old_placement, Placements new_placement);
public:
    CalculateMovedDocsRatio(uint32_t nodes, uint32_t redundancy, uint32_t old_placement_mask, uint32_t new_placement_mask, uint32_t new_up_mask);
    ~CalculateMovedDocsRatio();
    static CalculateMovedDocsRatio make_grow_calculator(uint32_t redundancy, uint32_t added_nodes, uint32_t nodes);
    static CalculateMovedDocsRatio make_shrink_calculator(uint32_t redundancy, uint32_t retired_nodes, uint32_t nodes);
    static CalculateMovedDocsRatio make_crash_calculator(uint32_t redundancy, uint32_t crashed_nodes, uint32_t nodes);
    static CalculateMovedDocsRatio make_replace_calculator(uint32_t redundancy, uint32_t added_nodes, uint32_t retired_nodes, uint32_t nodes);
    void scan();
    uint32_t get_lost_docs_base() const noexcept { return _lost_docs_base; }
    uint32_t get_checked_states() const noexcept { return _checked_states; }
    uint32_t get_new_redundancy() const noexcept { return _new_redundancy; }
    uint32_t get_moved_docs() const noexcept { return _moved_docs; }
    const std::vector<uint32_t>& get_moved_docs_per_node() const noexcept { return _moved_docs_per_node; }
    double get_lost_docs_base_ratio() const noexcept { return ((double) _lost_docs_base) / _checked_states; }
    double get_moved_docs_ratio() const noexcept { return ((double) _moved_docs) / _checked_states; }
};

}
