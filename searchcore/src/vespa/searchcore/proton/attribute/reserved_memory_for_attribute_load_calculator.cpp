// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include "reserved_memory_for_attribute_load_calculator.h"

#include <vespa/searchcore/proton/initializer/load_order_compare.h>

#include <algorithm>

using proton::initializer::LoadMemoryUsage;
using proton::initializer::LoadOrderCompare;

namespace proton {

class ReservedMemoryForAttributeLoadCalculator::MaxTransientMemoryCompare {
public:
    bool operator()(const LoadMemoryUsage& lhs, const LoadMemoryUsage& rhs) const noexcept {
        return lhs.transient() > rhs.transient();
    }
};

class ReservedMemoryForAttributeLoadCalculator::MaxReservedMemoryCompare {
public:
    bool operator()(const MemoryUsage& lhs, const MemoryUsage& rhs) const noexcept {
        return lhs.reserved_memory() > rhs.reserved_memory();
    }
};

ReservedMemoryForAttributeLoadCalculator::ReservedMemoryForAttributeLoadCalculator(
    uint32_t initialize_threads) noexcept
    : _initialize_threads(initialize_threads), _reserved_memory(0), _memory_usages() {
}

ReservedMemoryForAttributeLoadCalculator::~ReservedMemoryForAttributeLoadCalculator() = default;

size_t ReservedMemoryForAttributeLoadCalculator::add_subdb(std::span<LoadMemoryUsage> usages) {
    std::sort(usages.begin(), usages.end(), LoadOrderCompare());
    size_t later_memory = 0;
    size_t reserved_memory = 0;
    for (auto it = usages.rbegin(); it != usages.rend(); ++it) {
        if (_initialize_threads > 0) {
            _memory_usages.emplace_back(*it, later_memory);
        }
        if (it->transient() > later_memory) {
            reserved_memory = std::max(reserved_memory, it->transient() - later_memory);
        }
        later_memory += it->permanent();
    }
    return reserved_memory;
}

void ReservedMemoryForAttributeLoadCalculator::reset() {
    _reserved_memory = 0;
    _memory_usages.clear();
}

void ReservedMemoryForAttributeLoadCalculator::add(std::span<LoadMemoryUsage> ready_usages,
                                                   std::span<LoadMemoryUsage> notready_usages) {
    auto ready_reserved_memory = add_subdb(ready_usages);
    auto notready_reserved_memory = add_subdb(notready_usages);
    if (_initialize_threads == 0) {
        _reserved_memory += std::max(ready_reserved_memory, notready_reserved_memory);
    }
}

size_t ReservedMemoryForAttributeLoadCalculator::calc() {
    if (_initialize_threads == 0) {
        return _reserved_memory;
    }
    size_t                 reserved_memory = 0;
    std::span<MemoryUsage> usages(_memory_usages);
    if (_initialize_threads > 1) {
        /*
         * Calculate worst case for _initialize_threads - 1 tasks using max transient memory and ignoring
         * later_memory(). This corresponds to these tasks running until all other tasks have also been started.
         */
        std::span<MemoryUsage> first_usages(usages);
        if (_initialize_threads - 1 < _memory_usages.size()) {
            std::nth_element(_memory_usages.begin(), _memory_usages.begin() + _initialize_threads - 1,
                             _memory_usages.end(), MaxTransientMemoryCompare());
            first_usages = usages.first(_initialize_threads - 1);
        }
        for (auto& usage : first_usages) {
            reserved_memory += usage.transient();
        }
        usages = usages.last(usages.size() - first_usages.size());
    }
    if (!usages.empty()) {
        // Calculate worst case for last initializer thread using reserved memory() which uses later_memory()
        reserved_memory +=
            std::min_element(usages.begin(), usages.end(), MaxReservedMemoryCompare())->reserved_memory();
    }
    return reserved_memory;
}

} // namespace proton
