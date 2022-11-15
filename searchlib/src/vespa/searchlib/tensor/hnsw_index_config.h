// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <cstdint>

namespace search::tensor {

/*
 * Class containing config for HnswIndex.
 */
class HnswIndexConfig {
private:
    uint32_t _max_links_at_level_0;
    uint32_t _max_links_on_inserts;
    uint32_t _neighbors_to_explore_at_construction;
    uint32_t _min_size_before_two_phase;
    bool     _heuristic_select_neighbors;

public:
    HnswIndexConfig(uint32_t max_links_at_level_0_in,
                    uint32_t max_links_on_inserts_in,
                    uint32_t neighbors_to_explore_at_construction_in,
                    uint32_t min_size_before_two_phase_in,
                    bool heuristic_select_neighbors_in)
        : _max_links_at_level_0(max_links_at_level_0_in),
          _max_links_on_inserts(max_links_on_inserts_in),
          _neighbors_to_explore_at_construction(neighbors_to_explore_at_construction_in),
          _min_size_before_two_phase(min_size_before_two_phase_in),
          _heuristic_select_neighbors(heuristic_select_neighbors_in)
    {}
    uint32_t max_links_at_level_0() const { return _max_links_at_level_0; }
    uint32_t max_links_on_inserts() const { return _max_links_on_inserts; }
    uint32_t neighbors_to_explore_at_construction() const { return _neighbors_to_explore_at_construction; }
    uint32_t min_size_before_two_phase() const { return _min_size_before_two_phase; }
    bool heuristic_select_neighbors() const { return _heuristic_select_neighbors; }
};

}
