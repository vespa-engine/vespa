// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "alloc_config.h"
#include <vespa/searchcore/proton/common/subdbtype.h>
#include <algorithm>

using search::CompactionStrategy;
using search::GrowStrategy;

namespace proton {

AllocConfig::AllocConfig(const AllocStrategy& alloc_strategy,
                         uint32_t redundancy, uint32_t searchable_copies)
    : _alloc_strategy(alloc_strategy),
      _redundancy(redundancy),
      _searchable_copies(searchable_copies)
{
}

AllocConfig::AllocConfig()
    : AllocConfig(AllocStrategy(), 1, 1)
{
}

AllocConfig::~AllocConfig() = default;

bool
AllocConfig::operator==(const AllocConfig &rhs) const noexcept
{
    return ((_alloc_strategy == rhs._alloc_strategy) &&
            (_redundancy == rhs._redundancy) &&
            (_searchable_copies == rhs._searchable_copies));
}

AllocStrategy
AllocConfig::make_alloc_strategy(SubDbType sub_db_type) const
{
    auto &baseline_grow_strategy = _alloc_strategy.get_grow_strategy();
    size_t initial_capacity = baseline_grow_strategy.getDocsInitialCapacity();
    switch (sub_db_type) {
    case SubDbType::READY:
        initial_capacity *= _searchable_copies;
        break;
    case SubDbType::NOTREADY:
        initial_capacity *= (_redundancy - _searchable_copies);
        break;
    case SubDbType::REMOVED:
    default:
        initial_capacity = std::max(1024ul, initial_capacity / 100);
        break;
    }
    GrowStrategy grow_strategy(initial_capacity, baseline_grow_strategy.getDocsGrowFactor(), baseline_grow_strategy.getDocsGrowDelta(), baseline_grow_strategy.getMultiValueAllocGrowFactor());
    return AllocStrategy(grow_strategy, _alloc_strategy.get_compaction_strategy(), _alloc_strategy.get_amortize_count());
}

}
