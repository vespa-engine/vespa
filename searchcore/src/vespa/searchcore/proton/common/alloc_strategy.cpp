// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "alloc_strategy.h"
#include <iostream>

using search::GrowStrategy;

namespace proton {

AllocStrategy::AllocStrategy(const GrowStrategy& grow_strategy,
                             const CompactionStrategy& compaction_strategy,
                             uint32_t amortize_count)
    : _grow_strategy(grow_strategy),
      _compaction_strategy(compaction_strategy),
      _amortize_count(amortize_count)
{
}

AllocStrategy::AllocStrategy()
    : AllocStrategy(GrowStrategy(), CompactionStrategy(), 10000)
{
}

AllocStrategy::~AllocStrategy() = default;

bool
AllocStrategy::operator==(const AllocStrategy &rhs) const noexcept
{
    return ((_grow_strategy == rhs._grow_strategy) &&
            (_compaction_strategy == rhs._compaction_strategy) &&
            (_amortize_count == rhs._amortize_count));
}

std::ostream& operator<<(std::ostream& os, const AllocStrategy&alloc_strategy)
{
    os << "{ grow_strategy=" << alloc_strategy.get_grow_strategy() << ", compaction_strategy=" << alloc_strategy.get_compaction_strategy() << ", amortize_count=" << alloc_strategy.get_amortize_count() << "}";
    return os;
}

}
