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
      _candidates(),
      _high_priority_candidate() {
}

ReservedMemoryCalculator::~ReservedMemoryCalculator() = default;

bool ReservedMemoryCalculator::has_normal_high_priority_candidate() const noexcept {
    for (auto& candidate : _candidates) {
        if (candidate.high_priority()) {
            return true;
        }
    }
    return false;
}

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
    Candidate candidate(reserved_memory_for_flush, high_priority);
    if (candidate.high_priority()) {
        if (!_high_priority_candidate.has_value() || _high_priority_candidate.value() < candidate) {
            _high_priority_candidate = candidate;
        }
    }
    _candidates.push_back(candidate);
    if (type == IFlushTarget::Type::FLUSH && component == IFlushTarget::Component::INDEX) {
        ++_memory_indexes;
    }
}

size_t ReservedMemoryCalculator::reserved_memory_for_flush() {
    if (_concurrent < _candidates.size()) {
        /*
         * Retain the _concurrent biggest candidates. They are later used to calculate reserved
         * memory for flush.
         */
        std::nth_element(_candidates.begin(), _candidates.begin() + _concurrent, _candidates.end());
        _candidates.resize(_concurrent);
    }
    if (_concurrent == _candidates.size() && _concurrent != 0) {
        if (!has_normal_high_priority_candidate()) {
            // One of the flush slots can only be used for high priority flush targets
            std::nth_element(_candidates.begin(), _candidates.begin() + _concurrent - 1, _candidates.end());
            _candidates.resize(_concurrent - 1);
            if (_high_priority_candidate.has_value()) {
                _candidates.push_back(_high_priority_candidate.value());
            }
        }
    }
    size_t reserved_memory = 0;
    for (auto& candidate : _candidates) {
        reserved_memory += candidate.reserved();
    }
    return reserved_memory;
}

} // namespace proton::flushengine
