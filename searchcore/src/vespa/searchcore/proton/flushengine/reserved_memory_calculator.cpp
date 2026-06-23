// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reserved_memory_calculator.h"

#include <algorithm>

namespace proton::flushengine {

ReservedMemoryCalculator::ReservedMemoryCalculator(size_t concurrent) noexcept
    : _concurrent(concurrent), _candidates() {
}

ReservedMemoryCalculator::~ReservedMemoryCalculator() = default;

void ReservedMemoryCalculator::track_transient_memory_for_flush(size_t transient_memory_for_flush) {
    _candidates.emplace_back(transient_memory_for_flush);
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
    return reserved_memory;
}

} // namespace proton::flushengine
