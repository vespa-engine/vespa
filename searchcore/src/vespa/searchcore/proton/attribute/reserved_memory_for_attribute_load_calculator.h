// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/initializer/load_memory_usage.h>

#include <cstdint>
#include <span>
#include <vector>

namespace proton {

/*
 * Calculator for reserved memory for load, based on known ordering of attribute vector loading.
 */
class ReservedMemoryForAttributeLoadCalculator {
    class MemoryUsage : public initializer::LoadMemoryUsage {
        size_t _later_memory; // memory usage in attribute vectors known to be loaded later on.

    public:
        MemoryUsage(const initializer::LoadMemoryUsage& usage, size_t later_memory_) noexcept
            : initializer::LoadMemoryUsage(usage), _later_memory(later_memory_) {}
        [[nodiscard]] size_t later_memory() const noexcept { return _later_memory; }
        [[nodiscard]] size_t reserved_memory() const noexcept {
            return transient() > later_memory() ? transient() - later_memory() : 0;
        }
    };
    class MaxTransientMemoryCompare;
    class MaxReservedMemoryCompare;

    size_t                   _initialize_threads;
    size_t                   _reserved_memory;
    std::vector<MemoryUsage> _memory_usages;

    size_t add_subdb(std::span<initializer::LoadMemoryUsage> usages);

public:
    ReservedMemoryForAttributeLoadCalculator(uint32_t initialize_threads) noexcept;
    ~ReservedMemoryForAttributeLoadCalculator();
    void reset();
    void add(std::span<initializer::LoadMemoryUsage> ready_usages,
             std::span<initializer::LoadMemoryUsage> notready_usages);
    size_t calc();
};

} // namespace proton
