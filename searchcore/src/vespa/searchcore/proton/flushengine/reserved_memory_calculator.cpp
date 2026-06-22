// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reserved_memory_calculator.h"

#include <algorithm>

namespace proton::flushengine {

ReservedMemoryCalculator::ReservedMemoryCalculator(size_t concurrent, size_t each_max_memory,
                                                   size_t global_max_memory) noexcept
    : _concurrent(concurrent),
      _each_max_memory(each_max_memory),
      _global_max_memory(global_max_memory),
      _memory_indexes(0),
      _candidates() {
}

ReservedMemoryCalculator::~ReservedMemoryCalculator() = default;

size_t ReservedMemoryCalculator::reserved_memory_for_memory_indexes() const noexcept {
    /*
     * Calculate the highest memory usage for memory indexes before the memory flush strategy
     * triggers a flush based on the estimated memory gain.
     */
    return std::min(_global_max_memory, _memory_indexes * _each_max_memory);
}

void ReservedMemoryCalculator::track_transient_memory_for_flush(size_t                  transient_memory_for_flush,
                                                                IFlushTarget::Type      type,
                                                                IFlushTarget::Component component) {
    _candidates.emplace_back(transient_memory_for_flush);
    if (type == IFlushTarget::Type::FLUSH && component == IFlushTarget::Component::INDEX) {
        ++_memory_indexes;
    }
}

size_t ReservedMemoryCalculator::get_reserved_memory() {
    if (_concurrent < _candidates.size()) {
        /*
         * Retain the _concurrent biggest candidates. They are later used to calculate reserved
         * disk space for flush.
         */
        std::nth_element(_candidates.begin(), _candidates.begin() + _concurrent, _candidates.end());
        _candidates.resize(_concurrent);
    }
    size_t reserved_memory = 0;
    for (auto& candidate : _candidates) {
        reserved_memory += candidate.reserved();
    }
    reserved_memory += reserved_memory_for_memory_indexes();
    return reserved_memory;
}

} // namespace proton::flushengine
