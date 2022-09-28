// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "alloc_config.h"
#include "subdbtype.h"
#include <algorithm>

using search::GrowStrategy;
using vespalib::datastore::CompactionStrategy;

namespace proton {

AllocConfig::AllocConfig(const AllocStrategy& alloc_strategy,
                         uint32_t redundancy, uint32_t searchable_copies)
    : _alloc_strategy(alloc_strategy),
      _redundancy(redundancy),
      _searchable_copies(searchable_copies)
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
    auto &baseline = _alloc_strategy.get_grow_strategy();
    size_t initial_capacity = baseline.getInitialCapacity();
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
    GrowStrategy grow_strategy(initial_capacity, baseline.getGrowFactor(), baseline.getGrowDelta(), initial_capacity, baseline.getMultiValueAllocGrowFactor());
    return AllocStrategy(grow_strategy, _alloc_strategy.get_compaction_strategy(), _alloc_strategy.get_amortize_count());
}

}
