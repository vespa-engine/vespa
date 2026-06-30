// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcorespi/flush/iflushtarget.h>

#include <cstddef>
#include <optional>
#include <vector>

namespace proton::flushengine {

class ReservedMemoryCalculator {
    /*
     * Candidate for tracking reserved memory for flush, used to calculate worst case need for reserved memory.
     * The number of total flush threads determines how many candidates to use.
     */
    class Candidate {
        size_t _reserved;
        bool   _high_priority;

    public:
        explicit Candidate(size_t reserved_in, bool high_priority_in) noexcept
            : _reserved(reserved_in), _high_priority(high_priority_in) {}
        Candidate() noexcept : Candidate(0, false) {}
        bool operator<(const Candidate& rhs) const noexcept { return _reserved > rhs._reserved; }
        [[nodiscard]] size_t reserved() const noexcept { return _reserved; }
        [[nodiscard]] bool high_priority() const noexcept { return _high_priority; }
    };

    size_t                   _concurrent;
    size_t                   _each_max_memory;
    size_t                   _global_max_memory;
    uint32_t                 _memory_indexes;
    std::vector<Candidate>   _candidates; // Used to calculate worst case for concurrent flushes
    std::optional<Candidate> _high_priority_candidate;

    bool has_normal_high_priority_candidate() const noexcept;

public:
    using IFlushTarget = searchcorespi::IFlushTarget;
    ReservedMemoryCalculator(size_t concurrent, size_t each_max_memory, size_t global_max_memory) noexcept;
    ~ReservedMemoryCalculator();
    void track_reserved_memory_for_flush(size_t reserved_memory_for_flush, IFlushTarget::Type type,
                                         IFlushTarget::Component component, bool high_priority);
    [[nodiscard]] size_t reserved_memory_for_flush();
    [[nodiscard]] size_t reserved_memory_for_memory_indexes() const noexcept;
};

} // namespace proton::flushengine
