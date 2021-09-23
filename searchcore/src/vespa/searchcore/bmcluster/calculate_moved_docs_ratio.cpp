// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "calculate_moved_docs_ratio.h"
#include <cassert>

namespace search::bmcluster {

struct CalculateMovedDocsRatio::Placements
{
    uint32_t _mask;
    uint32_t _count;

    Placements()
        : _mask(0u),
          _count(0u)
    {
    }

    Placements(uint32_t mask, uint32_t count) noexcept
        : _mask(mask),
          _count(count)
    {
    }

    Placements add(uint32_t idx) const noexcept {
        return Placements(_mask | (1u << idx), _count + 1);
    }
    
    Placements add(uint32_t idx, uint32_t mask, uint32_t redundancy) const noexcept {
        return (((_count < redundancy) && ((1u << idx) & mask) != 0)) ? Placements(_mask | (1u << idx), _count + 1) : *this;
    }
};


CalculateMovedDocsRatio::CalculateMovedDocsRatio(uint32_t nodes, uint32_t redundancy, uint32_t old_placement_mask, uint32_t new_placement_mask, uint32_t new_up_mask)
    : _num_states(nodes + 1),
      _nodes(nodes),
      _old_placement_mask(old_placement_mask),
      _new_placement_mask(new_placement_mask),
      _new_up_mask(new_up_mask),
      _moved_docs(0u),
      _moved_docs_per_node(nodes),
      _checked_states(0u),
      _lost_docs_base(0u),
      _redundancy(redundancy),
      _old_redundancy(std::min(redundancy, (uint32_t)__builtin_popcount(old_placement_mask))),
      _new_redundancy(std::min(redundancy, (uint32_t)__builtin_popcount(new_placement_mask)))
{
    assert((new_placement_mask & ~new_up_mask) == 0u);
    uint32_t states = 1;
    for (uint32_t level = nodes; level > 0; --level)  {
        states *= std::max(1u, nodes - level);
        _num_states[level] = states;
    }
    _num_states[0] = states * nodes;
}

CalculateMovedDocsRatio::~CalculateMovedDocsRatio() = default;

void
CalculateMovedDocsRatio::scan(Placements selected, Placements old_placement, Placements new_placement)
{
    if (old_placement._count >= _old_redundancy) {
        if ((old_placement._mask & _new_up_mask) == 0) {
            _lost_docs_base += _num_states[selected._count];
            _checked_states += _num_states[selected._count];
            return;
        }
        if (new_placement._count >= _new_redundancy) {
            _checked_states += _num_states[selected._count];
            uint32_t only_new_mask = new_placement._mask & ~old_placement._mask;
            if (only_new_mask != 0) {
                _moved_docs += _num_states[selected._count] * (uint32_t)__builtin_popcount(only_new_mask);
                for (uint32_t node_idx = 0; node_idx < _nodes; ++node_idx) {
                    if ((only_new_mask & (1u << node_idx)) != 0) {
                        _moved_docs_per_node[node_idx] += _num_states[selected._count];
                    }
                }
            }
            return;
        }
    }
    assert(selected._count < _nodes);
    for (uint32_t node_idx = 0; node_idx < _nodes; ++node_idx) {
        if ((selected._mask & (1u << node_idx)) != 0) {
            continue;
        }
        scan(selected.add(node_idx), old_placement.add(node_idx, _old_placement_mask, _old_redundancy), new_placement.add(node_idx, _new_placement_mask, _new_redundancy));
    }
}

void
CalculateMovedDocsRatio::scan()
{
    scan(Placements(), Placements(), Placements());
    assert(_checked_states == _num_states[0]);
}

}
