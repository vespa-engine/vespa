// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

#include <cstddef>
#include <vector>

namespace proton::flushengine {

class ReservedMemoryCalculator {
    /*
     * Candidate for tracking reserved memory for flush, used to calculate worst case need for reserved memory.
     * The number of total flush threads determines how many candidates to use.
     */
    class Candidate {
        size_t _reserved;

    public:
        explicit Candidate(size_t reserved_in) noexcept : _reserved(reserved_in) {}
        Candidate() noexcept : Candidate(0) {}
        bool operator<(const Candidate& rhs) const noexcept { return _reserved > rhs._reserved; }
        size_t reserved() const noexcept { return _reserved; }
    };

    size_t                 _concurrent;
    size_t                 _each_max_memory;
    size_t                 _global_max_memory;
    uint32_t               _memory_indexes;
    std::vector<Candidate> _candidates; // Used to calculate worst case for concurrent flushes

    [[nodiscard]] size_t reserved_memory_for_memory_indexes() const noexcept;

public:
    using IFlushTarget = searchcorespi::IFlushTarget;
    ReservedMemoryCalculator(size_t concurrent, size_t each_max_memory, size_t global_max_memory) noexcept;
    ~ReservedMemoryCalculator();
    void track_transient_memory_for_flush(size_t transient_memory_for_flush, IFlushTarget::Type type,
                                          IFlushTarget::Component component);
    [[nodiscard]] size_t get_reserved_memory();
};

} // namespace proton::flushengine
