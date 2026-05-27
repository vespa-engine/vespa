// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

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
    std::vector<Candidate> _candidates; // Used to calculate worst case for concurrent flushes

public:
    ReservedMemoryCalculator(size_t concurrent) noexcept;
    ~ReservedMemoryCalculator();
    void track_transient_memory_for_flush(size_t trnasient_memory_for_flush);
    size_t get_reserved_memory();
};

} // namespace proton::flushengine
