// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reserved_memory_calculator.h"

#include <algorithm>

namespace proton::flushengine {

ReservedMemoryCalculator::ReservedMemoryCalculator(size_t concurrent, size_t each_max_memory,
                                                   size_t global_max_memory) noexcept
    : _each_max_memory(each_max_memory),
      _global_max_memory(global_max_memory),
      _memory_indexes(0),
      _candidates(concurrent) {
}

ReservedMemoryCalculator::~ReservedMemoryCalculator() = default;

size_t ReservedMemoryCalculator::reserved_memory_for_memory_indexes() const noexcept {
    /*
     * Calculate the highest memory usage for memory indexes before the memory flush strategy
     * triggers a flush based on the estimated memory gain.
     */
    return std::min(_global_max_memory, _memory_indexes * _each_max_memory);
}

void ReservedMemoryCalculator::track_reserved_memory_for_flush(size_t                  reserved_memory_for_flush,
                                                               IFlushTarget::Type      type,
                                                               IFlushTarget::Component component,
                                                               bool                    high_priority) {
    _candidates.add_candidate(reserved_memory_for_flush, high_priority);
    if (type == IFlushTarget::Type::FLUSH && component == IFlushTarget::Component::INDEX) {
        ++_memory_indexes;
    }
}

size_t ReservedMemoryCalculator::reserved_memory_for_flush() {
    return _candidates.reserved_resource_for_flush();
}

} // namespace proton::flushengine
